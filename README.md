# listing-service

The swapr service that owns items, their status and value bands, item photos in S3, and geo queries. It started as a copy of `user-service`, so its build, lint, configuration, authentication and delivery pipeline match that reference service.

Stack: Java 25, Spring Boot 4, Maven, Flyway, Checkstyle (Google rules, 120 columns), deployed to Render as a GraalVM native image.

## Running locally

Prerequisites: Docker and JDK 25.

Once, copy `.env.example` to `.env` and fill in the QA Supabase project URL, any non-empty local internal token, and the QA photo bucket's name and region with the QA IAM user's access key. `.env` is gitignored. Without the QA bucket values the service still starts if the bucket name and region are set, but the `photoBucket` health component is `DOWN`.

```sh
./mvnw spring-boot:run
```

With no profile set, the `local` profile is active. It reads `.env` from the repo root, and Spring Boot's Docker Compose support starts the PostGIS database from `docker-compose.yml` and wires the connection in. Local auth points at the QA Supabase project, so sign in through the QA project to get a JWT. Local photo storage points at the QA bucket; there is no local S3 stand-in. The service listens on port 8082. Real environment variables override the values in `.env`.

Run the linter and unit tests with:

```sh
./mvnw verify
```

The unit tests need no Docker, database or network. They start the application context without a database and replace the S3 client with a test double.

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `qa` or `prod`. Render sets it per service. | `local` |
| `PORT` | HTTP port. Render sets it. | `8082` |
| `DATABASE_URL` | JDBC URL, for example `jdbc:postgresql://<host>/<db>?sslmode=require`. Neon needs `sslmode=require`. | none (local uses Docker Compose) |
| `DATABASE_USERNAME` | Database user. | none |
| `DATABASE_PASSWORD` | Database password. Secret. | none |
| `SUPABASE_URL` | Supabase project URL, for example `https://<ref>.supabase.co`. The JWT issuer and JWKS address are built from it. | none (`.env` locally) |
| `INTERNAL_TOKEN` | Shared service-to-service secret for `/internal/**`. Secret. Startup fails when it is empty. | none (`.env` locally) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call `/v1/**` from a browser. | none (`.env` locally) |
| `PHOTO_BUCKET` | The environment's S3 photo bucket. Startup fails when it is empty. Locally, the QA bucket. | none (`.env` locally) |
| `AWS_REGION` | The photo bucket's AWS region. Startup fails when it is empty. | none (`.env` locally) |
| `AWS_ACCESS_KEY_ID` | Access key ID of the environment's IAM user. Locally, the QA IAM user. Not checked at startup: a wrong or missing key shows as `photoBucket` `DOWN`. | none (`.env` locally) |
| `AWS_SECRET_ACCESS_KEY` | Secret access key of that IAM user. Secret. | none (`.env` locally) |

YAML holds defaults only and reads every per-environment value from an environment variable, including for local runs. Locally those come from `.env`; in QA and prod they are set in Render. Secrets are never committed.

## Endpoints and security

- `/actuator/health` reports overall health including the `db` and `photoBucket` components, and is `UP` only when both are. `photoBucket` sends one S3 HeadBucket request for `PHOTO_BUCKET`, with a 5-second timeout, and is `DOWN` when the bucket is missing, access is denied or S3 cannot be reached. The IAM user needs `s3:ListBucket` on the bucket ARN for it. Details are never shown.
- `/actuator/health/liveness` ignores the database and the bucket. Render's health check uses it, so a slow Neon wake-up or a brief S3 problem does not fail deploys or restart the instance.
- `/v3/api-docs` serves the OpenAPI document without auth.
- `/v1/**` needs a Supabase JWT: issuer `<SUPABASE_URL>/auth/v1`, audience `authenticated`, signed by a key from the project's JWKS. The `sub` claim is the user's UUID and the principal name.
- `/internal/**` needs the `X-Internal-Token` header. A missing or wrong token gets 401. A request with the right token that also carries an `Authorization` header gets 403, so user JWTs never reach internal endpoints.
- Every other path is denied. Authentication and access errors are RFC 9457 Problem Details.

## Pipeline

`.github/workflows/pipeline.yml` has five jobs:

1. `verify` runs `./mvnw verify` (Checkstyle and unit tests) on every push to any branch and on pull requests. Any Checkstyle warning or failing test fails the run.
2. `image` runs on pushes to `main` only. It builds the native image from the `Dockerfile` and pushes `ghcr.io/<org>/listing-service:<commit-sha>`. There is no `latest` tag.
3. `deploy-qa` deploys that exact image to QA through the Render deploy hook in the GitHub Environment `qa`, and waits until the deploy is live.
4. `e2e` runs the whole integration test suite against QA by calling `integration-test-suite`'s reusable workflow. A failing suite skips `deploy-prod`, so no prod approval is offered.
5. `deploy-prod` waits for approval on the GitHub Environment `prod`, then deploys the same image to prod and waits until it is live. It never rebuilds, so prod runs only what passed in QA.

Both deploy jobs run `.github/scripts/render-deploy.sh`. It triggers the deploy hook, reads the deploy ID from the response, sleeps 5 minutes, then polls the Render API every 15 seconds until the deploy is `live`. A failed, cancelled or replaced deploy, an error response, or no live deploy 15 minutes after the trigger fails the job with an `::error::` annotation naming the environment, the deploy ID and the last status. Render 5xx responses are retried until the limit.

The workflow has one concurrency group per branch, so runs on `main` go one at a time from QA deploy through prod deploy, and a later merge can never replace the QA build while an earlier one is being tested. A run waiting for prod approval holds the group, so approve or reject it to let the next merge deploy. Only the newest queued run is kept. After a deploy, check `/actuator/health` by hand and confirm `db` and `photoBucket` are `UP`.

Build the native image locally with `docker build -t listing-service .`, or without Docker using `./mvnw -Pnative native:compile` on a GraalVM JDK 25.
