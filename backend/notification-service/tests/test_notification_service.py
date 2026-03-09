import importlib.util
import os
import pathlib
import types
import unittest

from fastapi.testclient import TestClient


SERVICE_ROOT = pathlib.Path(__file__).resolve().parents[1]
MODULE_PATH = SERVICE_ROOT / "main.py"


def load_module(callback_url: str = "") -> types.ModuleType:
  module_name = f"notification_service_main_{callback_url.replace(':', '_').replace('/', '_') or 'default'}"
  env = {
    "NOTIFY_API_KEY": "notify-key-for-tests",
    "TWILIO_ACCOUNT_SID": "AC1234567890",
    "TWILIO_AUTH_TOKEN": "twilio-auth-token-for-tests",
    "TWILIO_FROM_NUMBER": "+12025550123",
    "TWILIO_STATUS_CALLBACK_URL": callback_url,
  }

  original = {key: os.environ.get(key) for key in env}
  try:
    os.environ.update(env)
    spec = importlib.util.spec_from_file_location(module_name, MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module
  finally:
    for key, value in original.items():
      if value is None:
        os.environ.pop(key, None)
      else:
        os.environ[key] = value


class NotificationServiceTest(unittest.TestCase):
  def test_health_endpoint_returns_ok(self) -> None:
    module = load_module()
    client = TestClient(module.app)

    response = client.get("/health")

    self.assertEqual(200, response.status_code)
    self.assertEqual({"status": "ok"}, response.json())

  def test_notify_rejects_invalid_api_key(self) -> None:
    module = load_module()
    client = TestClient(module.app)

    response = client.post(
        "/notify",
        headers={"x-notify-key": "wrong-key"},
        json={
            "to": "+12025550123",
            "subject": "hello",
            "text": "world",
        },
    )

    self.assertEqual(401, response.status_code)
    self.assertEqual("invalid notify key", response.json()["detail"])

  def test_twilio_status_callback_validates_signature(self) -> None:
    callback_url = "https://example.com/notify/twilio/status"
    module = load_module(callback_url)
    client = TestClient(module.app)
    form_data = {
        "MessageSid": "SM123",
        "MessageStatus": "delivered",
        "To": "+12025550123",
    }

    signature = module.compute_twilio_signature(
        callback_url,
        list(form_data.items()),
        "twilio-auth-token-for-tests",
    )

    response = client.post(
        "/twilio/status",
        data=form_data,
        headers={"X-Twilio-Signature": signature},
    )

    self.assertEqual(200, response.status_code)
    self.assertEqual({"ok": True}, response.json())


if __name__ == "__main__":
  unittest.main()
