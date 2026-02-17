import base64
import hashlib
import hmac
import json
import logging
import os
import re
from datetime import datetime, timezone

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field, field_validator


PLACEHOLDER_VALUES = {
  "secret",
  "dev-password-placeholder",
  "dev-jwt-secret-placeholder",
  "dev-mailer-key-placeholder",
  "dev-notify-key-placeholder",
  "dev-audit-key-placeholder",
  "replace-with-twilio-account-sid",
  "replace-with-twilio-auth-token",
  "replace-with-twilio-from-number",
  "your-smtp-user",
  "your-smtp-password",
  "your-smtp-from@example.com",
}


class JsonFormatter(logging.Formatter):
  def format(self, record: logging.LogRecord) -> str:
    payload = {
      "@timestamp": datetime.now(timezone.utc).isoformat(),
      "level": record.levelname,
      "logger": record.name,
      "message": record.getMessage(),
    }
    extra = getattr(record, "extra_data", None)
    if isinstance(extra, dict):
      payload.update(extra)
    return json.dumps(payload, separators=(",", ":"))


logger = logging.getLogger("notification-service")
handler = logging.StreamHandler()
handler.setFormatter(JsonFormatter())
logger.handlers.clear()
logger.addHandler(handler)
logger.setLevel(os.getenv("LOG_LEVEL", "INFO").upper())
logger.propagate = False


def is_placeholder(value: str) -> bool:
  normalized = value.strip().lower()
  return (
    normalized in PLACEHOLDER_VALUES
    or "placeholder" in normalized
    or normalized.startswith("your-")
    or "example.com" in normalized
    or "replace-with" in normalized
  )


def require_secret(name: str, value: str | None) -> str:
  if value is None or not value.strip():
    raise RuntimeError(f"{name} is required and cannot be blank")
  if is_placeholder(value):
    raise RuntimeError(f"{name} uses a placeholder value and must be replaced")
  return value.strip()


def require_bounded_int(name: str, value: str | None, default: int, minimum: int, maximum: int) -> int:
  raw = value if value is not None else str(default)
  try:
    parsed = int(raw)
  except (TypeError, ValueError) as exc:
    raise RuntimeError(f"{name} must be a valid integer") from exc
  return max(minimum, min(parsed, maximum))


def mask_phone(number: str) -> str:
  if len(number) <= 6:
    return f"{number[:2]}***"
  return f"{number[:4]}***{number[-2:]}"


app = FastAPI()
NOTIFY_API_KEY = require_secret("NOTIFY_API_KEY", os.getenv("NOTIFY_API_KEY"))
TWILIO_ACCOUNT_SID = require_secret("TWILIO_ACCOUNT_SID", os.getenv("TWILIO_ACCOUNT_SID"))
TWILIO_AUTH_TOKEN = require_secret("TWILIO_AUTH_TOKEN", os.getenv("TWILIO_AUTH_TOKEN"))
TWILIO_FROM_NUMBER = require_secret("TWILIO_FROM_NUMBER", os.getenv("TWILIO_FROM_NUMBER"))
TWILIO_TIMEOUT_MS = require_bounded_int("TWILIO_TIMEOUT_MS", os.getenv("TWILIO_TIMEOUT_MS"), 5000, 1000, 30000)
TWILIO_STATUS_CALLBACK_URL = os.getenv("TWILIO_STATUS_CALLBACK_URL", "").strip()
TWILIO_MESSAGES_URL = f"https://api.twilio.com/2010-04-01/Accounts/{TWILIO_ACCOUNT_SID}/Messages.json"
E164_REGEX = re.compile(r"^\+[1-9]\d{7,14}$")


class NotificationRequest(BaseModel):
  to: str = Field(min_length=9, max_length=16)
  subject: str = Field(min_length=1, max_length=200)
  text: str = Field(min_length=1, max_length=5000)

  @field_validator("to")
  @classmethod
  def validate_to(cls, value: str) -> str:
    normalized = value.strip()
    if not E164_REGEX.fullmatch(normalized):
      raise ValueError("to must be a valid E.164 phone number, for example +12025550123")
    return normalized


@app.get("/health")
async def health() -> dict[str, str]:
  return {"status": "ok"}


def build_status_callback_url() -> str:
  return TWILIO_STATUS_CALLBACK_URL


def compute_twilio_signature(url: str, params: list[tuple[str, str]], auth_token: str) -> str:
  base = url
  for key, value in sorted(params, key=lambda item: item[0]):
    base += f"{key}{value}"
  digest = hmac.new(auth_token.encode("utf-8"), base.encode("utf-8"), hashlib.sha1).digest()
  return base64.b64encode(digest).decode("utf-8")


async def send_twilio_sms(to: str, body: str) -> str:
  payload = {"From": TWILIO_FROM_NUMBER, "To": to, "Body": body}
  status_callback_url = build_status_callback_url()
  if status_callback_url:
    payload["StatusCallback"] = status_callback_url

  try:
    async with httpx.AsyncClient(timeout=TWILIO_TIMEOUT_MS / 1000) as client:
      response = await client.post(
        TWILIO_MESSAGES_URL,
        data=payload,
        auth=(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN),
      )
  except httpx.TimeoutException as exc:
    logger.error(
      "twilio_sms_timeout",
      extra={"extra_data": {"event": "twilio_sms_timeout", "to": mask_phone(to)}},
    )
    raise HTTPException(status_code=502, detail="notification provider timeout") from exc
  except httpx.RequestError as exc:
    logger.error(
      "twilio_sms_request_error",
      extra={"extra_data": {"event": "twilio_sms_request_error", "to": mask_phone(to)}},
    )
    raise HTTPException(status_code=502, detail="notification provider unavailable") from exc

  parsed = {}
  try:
    parsed = response.json()
  except ValueError:
    parsed = {}

  if response.status_code >= 400:
    logger.error(
      "twilio_sms_failed",
      extra={
        "extra_data": {
          "event": "twilio_sms_failed",
          "to": mask_phone(to),
          "statusCode": response.status_code,
          "providerCode": parsed.get("code"),
          "providerMessage": parsed.get("message"),
        }
      },
    )
    raise HTTPException(status_code=502, detail="notification provider rejected request")

  sid = str(parsed.get("sid", ""))
  logger.info(
    "twilio_sms_sent",
    extra={
      "extra_data": {
        "event": "twilio_sms_sent",
        "to": mask_phone(to),
        "providerMessageSid": sid,
      }
    },
  )
  return sid


@app.post("/notify")
async def notify(request: NotificationRequest, x_notify_key: str | None = Header(default=None)) -> dict[str, bool]:
  if x_notify_key != NOTIFY_API_KEY:
    raise HTTPException(status_code=401, detail="invalid notify key")

  sid = await send_twilio_sms(request.to, request.text)

  logger.info(
    "notification_sent",
    extra={
      "extra_data": {
        "event": "notification_sent",
        "to": mask_phone(request.to),
        "subject": request.subject,
        "textLength": len(request.text),
        "providerMessageSid": sid,
      }
    },
  )
  return {"ok": True}


@app.post("/twilio/status")
async def twilio_status_callback(
    request: Request,
    x_twilio_signature: str | None = Header(default=None, alias="X-Twilio-Signature"),
) -> dict[str, bool]:
  if not TWILIO_STATUS_CALLBACK_URL:
    raise HTTPException(status_code=404, detail="callback not enabled")
  if x_twilio_signature is None or not x_twilio_signature.strip():
    raise HTTPException(status_code=401, detail="missing signature")

  form = await request.form()
  params = [(str(k), str(v)) for k, v in form.multi_items()]
  expected = compute_twilio_signature(TWILIO_STATUS_CALLBACK_URL, params, TWILIO_AUTH_TOKEN)
  if not hmac.compare_digest(expected, x_twilio_signature.strip()):
    logger.error(
      "twilio_callback_invalid_signature",
      extra={"extra_data": {"event": "twilio_callback_invalid_signature"}},
    )
    raise HTTPException(status_code=401, detail="invalid signature")

  message_sid = str(form.get("MessageSid", form.get("SmsSid", "")))
  message_status = str(form.get("MessageStatus", "unknown"))
  to = str(form.get("To", ""))
  error_code = str(form.get("ErrorCode", ""))
  error_message = str(form.get("ErrorMessage", ""))

  logger.info(
    "twilio_sms_delivery_status",
    extra={
      "extra_data": {
        "event": "twilio_sms_delivery_status",
        "providerMessageSid": message_sid,
        "messageStatus": message_status,
        "to": mask_phone(to) if to else "",
        "errorCode": error_code,
        "errorMessage": error_message,
      }
    },
  )
  return {"ok": True}
