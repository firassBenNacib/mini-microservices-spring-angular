.DEFAULT_GOAL := help

ROOT_DIR ?= $(CURDIR)
SCRIPT_DIR := $(ROOT_DIR)/scripts
ENV_FILE ?= .env
LOCAL_ENV_FILE ?= .env.local.example

COMPOSE_BASE := docker compose -f docker-compose.yml
COMPOSE_IMAGES := $(COMPOSE_BASE) -f docker-compose.images.yml
COMPOSE_BUILD := $(COMPOSE_BASE) -f docker-compose.build.yml
COMPOSE_DEV_IMAGES := $(COMPOSE_IMAGES) -f docker-compose.dev.yml
COMPOSE_DEV_BUILD := $(COMPOSE_BUILD) -f docker-compose.dev.yml
LOCAL_BUILD_ENV := DOCKERHUB_USERNAME=local

PUBLISH_FRONTEND_SCRIPT := $(SCRIPT_DIR)/publish_frontend_s3.sh
PUSH_ECR_SCRIPT := $(SCRIPT_DIR)/build_push_ecr.sh
PUSH_DOCKERHUB_SCRIPT := $(SCRIPT_DIR)/build_push_dockerhub.sh
CREATE_CLOUD_SECRETS_SCRIPT := $(SCRIPT_DIR)/create_aws_secrets_from_env.sh
SMOKE_TEST_SCRIPT := $(SCRIPT_DIR)/smoke_test.sh
INTEGRATION_TEST_SCRIPT := $(SCRIPT_DIR)/integration_test.sh

SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
.SILENT:

.PHONY: help
help:
	printf "Available targets:\n\n"
	printf "  %-22s %s\n" "up" "Pull pinned images and start the base application"
	printf "  %-22s %s\n" "up-images" "Start the published tag-based application"
	printf "  %-22s %s\n" "up-build" "Rebuild from local source and recreate the application"
	printf "  %-22s %s\n" "up-build-fresh" "Clean local artifacts, rebuild without cache, and recreate"
	printf "\n"
	printf "  %-22s %s\n" "up-dev-images" "Start published images with localhost dev ports"
	printf "  %-22s %s\n" "up-dev-build" "Rebuild locally with localhost dev ports"
	printf "  %-22s %s\n" "up-dev-build-fresh" "Clean local artifacts, rebuild dev images without cache"
	printf "\n"
	printf "  %-22s %s\n" "down" "Stop the application and remove containers/networks"
	printf "  %-22s %s\n" "down-volumes" "Stop the application and remove volumes too"
	printf "  %-22s %s\n" "clean-artifacts" "Remove local frontend/backend build artifacts and caches"
	printf "  %-22s %s\n" "ps" "Show container status for the base application"
	printf "  %-22s %s\n" "logs" "Tail gateway/auth/api logs"
	printf "\n"
	printf "  %-22s %s\n" "compose-validate" "Validate compose overlays with example env"
	printf "  %-22s %s\n" "smoke-test" "Run HTTP smoke checks against BASE_URL"
	printf "  %-22s %s\n" "integration-test" "Run compose-backed gateway integration checks"
	printf "  %-22s %s\n" "publish-frontend-build" "Build the Angular frontend only"
	printf "  %-22s %s\n" "publish-frontend" "Build and publish frontend assets (requires BUCKET)"
	printf "\n"
	printf "  %-22s %s\n" "push-dockerhub" "Build and push images to DockerHub"
	printf "  %-22s %s\n" "push-ecr" "Build and push images to ECR"
	printf "  %-22s %s\n" "create-cloud-secrets" "Create runtime secrets from ENV_FILE for ENVIRONMENT=<name>"

.PHONY: up
up:
	$(COMPOSE_BASE) pull
	$(COMPOSE_BASE) up -d

.PHONY: up-images
up-images:
	$(COMPOSE_IMAGES) pull
	$(COMPOSE_IMAGES) up -d

.PHONY: up-build
up-build:
	$(LOCAL_BUILD_ENV) $(COMPOSE_BUILD) build --pull
	$(LOCAL_BUILD_ENV) $(COMPOSE_BUILD) up -d --force-recreate

.PHONY: up-build-fresh
up-build-fresh: clean-artifacts
	$(LOCAL_BUILD_ENV) $(COMPOSE_BUILD) build --pull --no-cache
	$(LOCAL_BUILD_ENV) $(COMPOSE_BUILD) up -d --force-recreate

.PHONY: up-dev-images
up-dev-images:
	$(COMPOSE_DEV_IMAGES) pull
	$(COMPOSE_DEV_IMAGES) up -d

.PHONY: up-dev-build
up-dev-build:
	$(LOCAL_BUILD_ENV) $(COMPOSE_DEV_BUILD) build --pull
	$(LOCAL_BUILD_ENV) $(COMPOSE_DEV_BUILD) up -d --force-recreate

.PHONY: up-dev-build-fresh
up-dev-build-fresh: clean-artifacts
	$(LOCAL_BUILD_ENV) $(COMPOSE_DEV_BUILD) build --pull --no-cache
	$(LOCAL_BUILD_ENV) $(COMPOSE_DEV_BUILD) up -d --force-recreate

.PHONY: down
down:
	$(COMPOSE_BASE) down --remove-orphans

.PHONY: down-volumes
down-volumes:
	$(COMPOSE_BASE) down --remove-orphans --volumes

.PHONY: clean-artifacts
clean-artifacts:
	find backend -mindepth 2 -maxdepth 2 -type d -name target -exec rm -rf {} +
	rm -rf frontend/node_modules frontend/dist frontend/.angular
	if [[ -d .artifacts ]]; then
		find .artifacts -mindepth 1 -maxdepth 1 -exec rm -rf {} +
	fi

.PHONY: ps
ps:
	$(COMPOSE_BASE) ps

.PHONY: logs
logs:
	$(COMPOSE_BASE) logs -f gateway auth-service api-service || test $$? -eq 130

.PHONY: compose-validate
compose-validate:
	docker compose --env-file $(LOCAL_ENV_FILE) -f docker-compose.yml config --services
	docker compose --env-file $(LOCAL_ENV_FILE) -f docker-compose.yml -f docker-compose.images.yml config --services
	docker compose --env-file $(LOCAL_ENV_FILE) -f docker-compose.yml -f docker-compose.build.yml config --services

.PHONY: smoke-test
smoke-test:
	: "$${BASE_URL:?Usage: make smoke-test BASE_URL=https://example.com [SMOKE_AUTH_EMAIL=user@example.com] [SMOKE_AUTH_PASSWORD=secret]}"
	BASE_URL="$${BASE_URL}" \
	SMOKE_AUTH_EMAIL="$${SMOKE_AUTH_EMAIL:-}" \
	SMOKE_AUTH_PASSWORD="$${SMOKE_AUTH_PASSWORD:-}" \
	REQUIRE_AUTH_SMOKE="$${REQUIRE_AUTH_SMOKE:-false}" \
	bash "$(SMOKE_TEST_SCRIPT)" --base-url "$${BASE_URL}"

.PHONY: integration-test
integration-test:
	bash "$(INTEGRATION_TEST_SCRIPT)"

.PHONY: publish-frontend-build
publish-frontend-build:
	bash "$(PUBLISH_FRONTEND_SCRIPT)" --build-only

.PHONY: publish-frontend
publish-frontend:
	: "$${BUCKET:?Usage: make publish-frontend BUCKET=<bucket> [DISTRIBUTION_ID=<id>]}"
	ARGS=(--bucket "$${BUCKET}")
	if [[ -n "$${DISTRIBUTION_ID:-}" ]]; then
		ARGS+=(--distribution-id "$${DISTRIBUTION_ID}")
	fi
	bash "$(PUBLISH_FRONTEND_SCRIPT)" "$${ARGS[@]}"

.PHONY: push-dockerhub
push-dockerhub:
	: "$${DOCKERHUB_USERNAME:?Usage: make push-dockerhub DOCKERHUB_USERNAME=<name> [DOCKERHUB_NAMESPACE=<org>] [IMAGE_TAG=<tag>] [SERVICES=a,b] [INCLUDE_FRONTEND_IMAGE=true] [PUSH_LATEST=true] [DOCKERHUB_TOKEN=<token>]}"
	bash "$(PUSH_DOCKERHUB_SCRIPT)"

.PHONY: push-ecr
push-ecr:
	CLOUD_PROVIDER_REGION="$${CLOUD_PROVIDER_REGION:-eu-west-1}" \
	bash "$(PUSH_ECR_SCRIPT)"

.PHONY: build-push-images
build-push-images: push-ecr

.PHONY: create-cloud-secrets
create-cloud-secrets:
	: "$${ENVIRONMENT:?Usage: make create-cloud-secrets ENVIRONMENT=<name> [ENV_FILE=.env] [CLOUD_PROVIDER_REGION=eu-west-1]}"
	REGION_ARG=""
	if [[ -n "$${CLOUD_PROVIDER_REGION:-}" ]]; then
		REGION_ARG="--region $${CLOUD_PROVIDER_REGION}"
	fi
	bash "$(CREATE_CLOUD_SECRETS_SCRIPT)" --environment "$${ENVIRONMENT}" --env-file "$(ENV_FILE)" $$REGION_ARG
