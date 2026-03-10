## Workflows

- `ci.yml`: lint, compose validation, tests, and image build verification.
- `security-baseline.yml`: Gitleaks history scanning plus Trivy secret/config scanning.
- `container-security.yml`: Syft SBOM generation and Grype image scanning with artifact reports.
- `image-advisory-report.yml`: scheduled/manual stricter Grype reporting for fixed `HIGH,CRITICAL` image vulnerabilities.
- `codeql.yml`: source code scanning for Java, JavaScript/TypeScript, and Python.
- `dependency-review.yml`: pull-request dependency policy checks.
- `dockerhub-publish.yml` and `ecr-publish.yml`: tag-driven release publishing, attestations, and Cosign signing.
- `frontend-s3-deploy.yml`: frontend deploy plus post-deploy smoke test.
- `dast.yml`: manual live-target DAST.
- `smoke-tests.yml`: scheduled/manual stable-environment smoke checks.
- `scorecard.yml`: scheduled OSSF Scorecard SARIF upload.

## Configuration

Repository or environment variables:

- `CLOUD_PROVIDER_REGION`
- `CLOUD_PROVIDER_ROLE_TO_ASSUME`
- `DOCKERHUB_NAMESPACE`
- `DOCKERHUB_USERNAME`
- `ECR_IMAGE_NAMESPACE`
- `FRONTEND_BUCKET`
- `FRONTEND_DISTRIBUTION_ID`
- `DAST_TARGET_URL`
- `SMOKE_BASE_URL`

Repository or environment secrets:

- `DOCKERHUB_TOKEN`
- `SMOKE_AUTH_EMAIL`
- `SMOKE_AUTH_PASSWORD`
