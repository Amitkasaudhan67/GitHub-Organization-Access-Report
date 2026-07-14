# GitHub Organization Access Report

A production-oriented Spring Boot service that builds a per-user view of repository access for one GitHub organization. It uses a server-side personal access token, never exposes that token in its API, and returns normalized `READ`, `WRITE`, or `ADMIN` permissions.

## Architecture

```text
GET /api/report
       |
AccessReportController
       |
GithubService -- Caffeine async cache (5 minutes by default)
       |
GithubClient -- shared authenticated WebClient -- GitHub REST API
```

`GithubClient` owns only HTTP and pagination. `GithubService` owns concurrency, aggregation, permission normalization, sorting, and caching. The controller owns the HTTP endpoint. `GlobalExceptionHandler` provides the error contract.

## Technology

- Java 17, Spring Boot 3.5, Maven
- Spring WebFlux and `WebClient`
- Jackson, Bean Validation, Lombok, SLF4J
- Caffeine, Actuator, SpringDoc OpenAPI / Swagger UI
- JUnit 5, Mockito, Reactor Test

## Layout

```text
src/main/java/com/github/accessreport
  client/       GitHub REST client and pagination
  config/       WebClient, properties, cache, OpenAPI
  controller/   REST endpoint
  dto/          API and GitHub payload records
  exception/    typed failures and JSON error handling
  model/        internal normalized report values
  service/      aggregation and cache orchestration
  util/         permission mapping
```

## Run

Prerequisites: Java 17+ and Maven 3.9+.

Set secrets outside source control:

```powershell
$env:GITHUB_ORGANIZATION = "your-organization"
$env:GITHUB_TOKEN = "github_pat_your_token"
mvn spring-boot:run
```

Build and run tests:

```powershell
mvn clean install
```

The application listens on `http://localhost:8080`. It validates the organization and token at startup, so it will not start with blank credentials.

## GitHub token setup

Use a token belonging to an account that can view every repository and collaborator that should appear in the report. For a fine-grained token, grant the target organization/repositories and read access sufficient to list repository metadata and collaborators. For a classic token, use the least privileged organization/repository scopes that allow those operations. Organization SSO authorization may also be required.

Keep `GITHUB_TOKEN` in a secret manager, CI secret, or environment variable. Do not commit it to `application.properties`.

## Configuration

All settings can be supplied as environment variables (for example, `GITHUB_MAX_CONCURRENCY`) or external Spring properties.

| Property | Default | Purpose |
| --- | --- | --- |
| `github.base-url` | `https://api.github.com` | GitHub API base URL |
| `github.organization` | `GITHUB_ORGANIZATION` | Organization to report |
| `github.token` | `GITHUB_TOKEN` | Personal access token |
| `github.max-concurrency` | `10` | Maximum concurrent collaborator requests |
| `github.retry.max-attempts` | `3` | Total attempts for transient failures |
| `github.retry.base-delay` | `PT1S` | Exponential retry base delay |
| `github.report-cache-ttl` | `PT5M` | Full-report cache lifetime |
| `github.connect-timeout` | `PT5S` | TCP connection timeout |
| `github.response-timeout` | `PT30S` | Response timeout |

## API

### `GET /api/report`

```powershell
Invoke-RestMethod http://localhost:8080/api/report
```

```json
[
  {
    "username": "amit",
    "repositories": [
      { "repository": "ATM", "permission": "WRITE" },
      { "repository": "EmployeeManagement", "permission": "ADMIN" }
    ]
  }
]
```

Errors use a consistent JSON payload. GitHub authentication, authorization, missing organization, rate-limit, transient upstream, and unexpected failures are mapped to useful HTTP status codes. Rate limits include `Retry-After` when GitHub provides a reset time.

```json
{
  "timestamp": "2026-07-14T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "code": "GITHUB_RATE_LIMITED",
  "message": "GitHub API rate limit has been exceeded.",
  "path": "/api/report",
  "retryAfterSeconds": 60
}
```

Swagger UI is available at `/swagger-ui/index.html`; OpenAPI JSON is at `/v3/api-docs`; liveness is available at `/actuator/health`.

## Scalability decisions and assumptions

- Both repository and collaborator endpoints use `per_page=100` and continue until GitHub returns an empty page.
- Repository collaborator calls run asynchronously with bounded concurrency, not one at a time.
- Successful reports are cached by organization. Failed loads are not cached.
- `admin` maps to `ADMIN`; `push` or `maintain` maps to `WRITE`; `pull` or `triage` maps to `READ`.
- A collaborator lookup returning 404 is skipped because the repository may have been deleted after repository enumeration. Any other collaborator lookup failure fails the request to avoid silently incomplete data.
- The inbound endpoint has no application authentication. Run it behind a trusted internal network, gateway, or identity-aware proxy.

## Future improvements

- Support an organization path parameter with tenant-aware authorization.
- Add an explicit cache refresh endpoint and metrics for cache hits, GitHub calls, retries, and rate-limit headroom.
- Add GitHub App authentication and webhook-driven cache invalidation.
- Add integration tests against a WireMock GitHub API fixture and deploy with centralized secrets/observability.
