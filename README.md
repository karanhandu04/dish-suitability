# Dish Suitability Service

A small Spring Boot service that scores how suitable a dish is for a given time of day,
from **0** (not suitable) to **10** (perfect).

> Java 25 · Spring Boot 4.1 · API-first with OpenAPI Generator · Lombok · Maven · JUnit ·
> Docker · GitHub Actions
> Implements **Level 1** (core API) and **Level 2** (containerization and operability).

---

## Build and run

### With Docker (recommended)

```bash
docker-compose up --build
```

The service starts on **`https://localhost:8443`**. On first start, a one-off `certs` container
creates a self-signed certificate for `localhost` in `./certs` (git-ignored); later starts reuse
it. The demo API key is `local-dev-key`; set your own with
`APP_SECURITY_API_KEYS=my-key docker-compose up`.

### Locally with Maven (Java 25)

```bash
mvn verify                                          # generate API code, compile, all tests, format check
mvn spring-boot:run -Dspring-boot.run.profiles=local  # plain HTTP on 8080, API key "local-dev-key"
```

`mvn spotless:apply` formats the code (Google Java Format); the build fails on unformatted code.

### Try it

```bash
curl --cacert certs/cert.pem -X POST https://localhost:8443/dish-suitability \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key' \
  -H 'X-Channel: my-laptop' \
  -d '{"time": "08:30", "dish": "Pancakes"}'
# {"score":10}

curl --cacert certs/cert.pem https://localhost:8443/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

`--cacert certs/cert.pem` makes curl trust the self-signed certificate (or use `-k`).

The API contract is served at `https://localhost:8443/openapi/dish-suitability.yaml`
(paste it into [editor.swagger.io](https://editor.swagger.io) to browse it).

---

## API

The contract is defined in
[`src/main/resources/static/openapi/dish-suitability.yaml`](src/main/resources/static/openapi/dish-suitability.yaml).
The tables below summarize it.

### `POST /dish-suitability`

**Headers**

| Header | Required | Rules |
|---|---|---|
| `X-API-Key` | yes | A configured API key |
| `X-Channel` | yes | Who is calling, e.g. `web`, `mobile-app`. 1–32 of `A-Z a-z 0-9 . _ -` |
| `X-Request-ID` | no | Caller's id for this request, 1–64 of `A-Z a-z 0-9 . _ -`. Generated if absent; always returned |

**Body**

| Field  | Type   | Rules |
|--------|--------|-------|
| `time` | string | Required. 24-hour `HH:mm`, `00:00`–`23:59` |
| `dish` | enum   | Required. `Pancakes`, `Chicken` or `IceCream` (exact, case-sensitive) |

Response `200 OK`: `{"score": <0–10>}`

Errors use the standard RFC 7807 format (`application/problem+json`), and every error carries
the `requestId`, so a caller reporting a problem gives us the id to find it in the logs:

```json
{
  "type": "about:blank",
  "title": "Unsupported dish",
  "status": 400,
  "detail": "dish must be one of: Pancakes, Chicken, IceCream",
  "instance": "/dish-suitability",
  "requestId": "3f2c9a7e-8b1d-4c55-9e2a-6d0f1b7c4a21",
  "acceptedDishes": ["Pancakes", "Chicken", "IceCream"]
}
```

| Situation | Status |
|---|---|
| Missing or invalid API key | 401, with `WWW-Authenticate: ApiKey` |
| Missing field or header, bad time format, bad header format | 400, with an `errors` map per field/header |
| Dish not in the contract | 400, with the list of accepted dishes |
| Malformed JSON | 400 |
| Wrong content type | 415 |
| Wrong HTTP method | 405 |
| Unexpected error | 500, no internal details leaked (full error is logged) |

### `GET /health`

Public (no API key). Returns `"status":"UP"`, plus the liveness/readiness probe groups that
Spring Boot adds. Provided by Spring Boot Actuator, mapped to `/health` instead of the default
`/actuator/health`. Only the health endpoint is exposed over HTTP.

---

## Design

### API-first

The OpenAPI specification is written first and is the **source of truth** for the API.

```
dish-suitability.yaml ──(openapi-generator, at build time)──▶ DishSuitabilityApi (interface)
                                                             Dish, DishSuitabilityRequest/Response
                                                                         ▲
                                                DishSuitabilityController implements DishSuitabilityApi
```

- The generated code lands in `target/generated-sources` and is **never committed**: change the
  spec, rebuild, and the compiler shows every place that must follow.
- Path, HTTP method, content types, headers and validation rules (`required`, `pattern`, `enum`)
  come from the spec, so the implementation cannot drift from the documentation.
- Consumers can generate their own clients from the same file, and the contract can be reviewed
  with them **before** any code is written.
- **Generated classes stop at the API layer.** The controller maps them to domain types
  (`Dish`, `LocalTime`, `Score`); the service and domain never see generated code.

**`dish` is an enum in the contract.** Callers see exactly which values are allowed, and the
contract is strict:
- *Adding* a dish is backwards compatible for callers: `dish` is only ever sent, never returned,
  so existing clients keep working and simply don't send the new value yet.
- *Removing* a dish is a breaking change, and it should be: callers must find out. That means a
  new major version of the API.
- The controller maps the contract's enum to the domain's with an **exhaustive `switch` and no
  `default`**. Add a dish to the spec and the build fails until the domain has it; add it to the
  domain and the application refuses to start until it has a scorer. Contract, domain and
  scoring cannot drift apart.

Trade-offs I accepted:
- The spec cannot carry custom validation messages, so `ApiExceptionHandler` rewrites the
  generated defaults (`must match "^([01]..."`) into readable ones.
- The generated interface is annotated `@Validated`, so header constraints are checked by Spring's
  validation proxy, which by default reports them as a 500. With
  `spring.validation.method.adapt-constraint-violations=true` they arrive with per-parameter
  details and `ApiExceptionHandler` turns them into a 400 naming the header.
- The health endpoint is not in the spec: it is an operational endpoint for the platform
  (Docker, Kubernetes), not part of the business contract.

### Structure

```
src/main/resources/static/openapi/
  dish-suitability.yaml      the API contract (source of truth, also served at runtime)
api/                         HTTP layer
  DishSuitabilityController  implements the generated interface, maps API ↔ domain
  ApiExceptionHandler        every error as problem+json, with the request id
  ApiHeaders                 header names and formats
  security/                  ApiKeyAuthenticationFilter, ApiKeyProperties
  logging/                   RequestCorrelationFilter: request id, channel, access log
service/                     DishSuitabilityService: picks the right scorer for the dish
domain/                      Business concepts, independent of HTTP
  Dish                       the supported dishes (enum)
  Score                      value object, enforces 0–10
  TimeWindow                 a time range [start, end) with a score; supports wrapping midnight
  DishScorer                 strategy interface: one implementation per dish
  TimeWindowDishScorer       shared logic: first matching window wins, else a default score
  scoring/                   PancakesScorer, ChickenScorer, IceCreamScorer (data only)
```

Each layer has one job. The controller knows HTTP but not scoring; the scorers know scoring but
not HTTP. Dependencies point inwards: `api → service → domain`.

### Key decisions

**Strategy pattern, one scorer per dish.** The service receives all `DishScorer` beans from
Spring and indexes them by dish. There is no `if/switch` on the dish in the scoring code, so
adding a dish never means editing existing scorers (open/closed principle).

**Adding a new dish:**
1. Add it to the `Dish` enum in the OpenAPI spec, e.g. `Soup`.
2. The build now fails in `DishSuitabilityController.toDomain`: add `SOUP` to the domain `Dish`
   and the mapping.
3. The application now refuses to start (`No scorer registered for: [SOUP]`): add
   `SoupScorer extends TimeWindowDishScorer` annotated with `@Component`.

Every step is enforced by the compiler or at startup, never discovered as a 500 in production.

**Scoring as data, not logic.** Each scorer is a list of time windows plus a default score. The
rules are easy to read, review and change, and the lookup logic exists once in the base class.

**Value objects guard the rules.** `Score` cannot be created outside 0–10, and `TimeWindow`
cannot be empty. Invalid states are impossible rather than checked in many places. These are
Java records rather than Lombok `@Value`: the language feature does the job.

**Half-open time windows `[start, end)`.** 10:00 belongs to the 10:00–12:00 window, not to
07:00–10:00, so adjacent windows never overlap and boundaries are unambiguous.

### Security

- **HTTPS** with TLS 1.3/1.2 and HTTP/2, configured through a Spring Boot **SSL bundle**
  (`application-tls.yml`). The keystore is never in the image or the repository; its location
  and password come from the environment. `reload-on-update` picks up a renewed certificate
  without a restart. In production the certificate would be CA-issued and come from a secret
  store, or TLS would terminate at the ingress.
- **API key authentication** (`X-API-Key`) in a servlet filter, **secure by default**: every
  path is protected except `/health` and the published contract, so a new endpoint can't be
  left open by accident.
  - Several keys can be valid at once (`APP_SECURITY_API_KEYS=key-one,key-two`), so a key can
    be rotated without downtime.
  - Keys are compared as SHA-256 hashes in **constant time** (`MessageDigest.isEqual`), so
    response timing can't leak a key character by character. Only the hashes are kept in
    memory, and keys are never logged.
  - The application **refuses to start without a key**, so it can never run unprotected.
  - A plain filter is enough for a shared-key scheme. For OAuth2 / JWT I would use Spring
    Security's resource server support instead.
- **No internals in errors:** a 500 never contains the exception message.

### Logging and traceability

- **`X-Request-ID`**: taken from the caller or generated, returned in every response, included
  in every error body, and written on **every log line** for that request (SLF4J MDC).
- **`X-Channel`**: required, so every log line also says *who* called.
- **Access log**: one line per request with method, path, status and duration. Health checks
  are logged at DEBUG so the INFO log isn't flooded by the Docker health check.
- **Log injection is prevented**: header values are only logged if they match the expected
  format, so a header containing a newline can't forge a log line. The MDC is cleared after
  each request because server threads are reused.

```
INFO [requestId=3f2c9a7e-… channel=mobile-app] … RequestCorrelationFilter : POST /dish-suitability -> 200 (3 ms)
```

### Lombok

Used where it removes boilerplate, with stable (non-experimental) features only:
`@RequiredArgsConstructor` for constructor injection, `@Slf4j` for SLF4J loggers, `@NonNull`
for generated null checks, `@Getter`. `lombok.config` marks generated code with
`@lombok.Generated` so coverage tools ignore it. Lombok is compile-time only: it is excluded
from the runnable jar. Value objects use Java records instead.

### Scoring rules

The assignment leaves the scores open; these reflect typical eating habits.

| Time          | Pancakes | Chicken | IceCream |
|---------------|:--------:|:-------:|:--------:|
| 00:00–06:00   | 0        | 1       | 0        |
| 06:00–07:00   | 7        | 2       | 0        |
| 07:00–08:00   | 10       | 2       | 0        |
| 08:00–10:00   | 10       | 2       | 2        |
| 10:00–11:00   | 7        | 2       | 2        |
| 11:00–12:00   | 7        | 8       | 6        |
| 12:00–13:00   | 3        | 8       | 6        |
| 13:00–14:00   | 3        | 8       | 10       |
| 14:00–15:00   | 3        | 5       | 10       |
| 15:00–17:00   | 5        | 5       | 10       |
| 17:00–19:00   | 2        | 10      | 6        |
| 19:00–20:30   | 2        | 10      | 8        |
| 20:30–21:00   | 2        | 6       | 8        |
| 21:00–21:30   | 0        | 6       | 8        |
| 21:30–23:00   | 0        | 6       | 4        |
| 23:00–23:30   | 0        | 1       | 4        |
| 23:30–24:00   | 0        | 1       | 0        |

### Assumptions

- `time` is a local time of day with no date or time zone, as in the example (`"HH:mm"`).
- Dish names must match the contract exactly (`"IceCream"`, not `"icecream"`).
- The scores are deterministic: the same input always gives the same output.
- A shared API key per calling system is sufficient authentication for this exercise.

---

## Testing

| Level | What | Examples |
|-------|------|----------|
| Unit (domain) | Scoring rules and value objects, plain JUnit, no Spring | `TimeWindowTest`, `ScoreTest`, `PancakesScorerTest`, `AllScorersContractTest` |
| Unit (service) | Scorer selection and startup validation | `DishSuitabilityServiceTest` |
| Unit (web) | Request id / channel handling and log-injection protection; contract ↔ domain mapping | `RequestCorrelationFilterTest`, `DishMappingTest` |
| Web slice | HTTP behaviour against the contract: authentication, headers, status codes, validation, error format (service mocked) | `DishSuitabilityControllerTest` (`@WebMvcTest`) |
| Integration | Whole application on a random port, real HTTP (`RestTestClient`), health and published spec | `DishSuitabilityApplicationIT` |

Notable tests:
- **Boundary tests** at every window edge (e.g. 09:59 vs 10:00), where off-by-one bugs live.
- **Contract test** that runs every scorer for all 1,440 minutes of the day and checks the score
  is always 0–10, and that every dish has exactly one scorer.
- **Every dish in the API contract maps to the domain** (`@EnumSource` over the generated enum).
- **Security**: missing/invalid keys, key rotation, a forged `X-Request-ID` containing a newline,
  and a 500 that never contains the internal exception message.

Unit tests run with `mvn test` (Surefire). Integration tests (`*IT`) run with `mvn verify`
(Failsafe), so the fast feedback loop stays fast.

---

## CI/CD flow

A working CI pipeline is included in `.github/workflows/ci.yml`: it runs `mvn verify`, then
starts the stack with `docker compose up` exactly as a reviewer would, and smoke-tests it over
HTTPS (health, a scoring call, and a 401 without a key). The full flow I would use to deploy
this service:

```
 Pull request ──▶ Build & test ──▶ Image build ──▶ Smoke test      (no deploy)

 Merge to main ─▶ Build & test ──▶ Image build ──▶ Smoke test ──▶ Push image (tag = git SHA)
                                                                    │
                     ┌──────────────────────────────────────────────┘
                     ▼
                Deploy to DEV (automatic) ──▶ Smoke test
                     ▼
                Deploy to STAGING (automatic) ──▶ Automated API tests
                     ▼
                Manual approval
                     ▼
                Deploy to PROD (rolling update, gated on /health) ──▶ Monitor
```

**Continuous integration (on every pull request)**
1. `mvn verify`: generate code from the spec (an invalid spec fails the build), compile, unit
   tests, integration tests, formatting check.
2. Quality gates (to add): static analysis (e.g. SonarQube), dependency vulnerability scan
   (e.g. OWASP Dependency-Check or Dependabot), and an image scan (e.g. Trivy).
3. Build the Docker image and smoke test the real container. The pull request can't be merged
   unless everything passes.

**Continuous delivery (on merge to main)**
1. Build the image **once**, tag it with the git commit SHA, and push it to a registry
   (GHCR here; Azure Container Registry or ECR in a cloud setup).
2. Promote the **same image** through dev → staging → prod. Only configuration changes between
   environments (environment variables; API keys and certificates from a vault such as Azure
   Key Vault), never the artifact. What was tested is exactly what runs in production.
3. Dev and staging deploy automatically. Production deploys after a manual approval
   (GitHub Environments with required reviewers).
4. Production uses a **rolling update** on a container platform (Kubernetes / AKS, or Azure
   Container Apps). New instances receive traffic only once `/health` reports `UP`. Graceful
   shutdown (`server.shutdown: graceful`) lets old instances finish in-flight requests.

**Rollback:** redeploy the previous image tag (the previous SHA). Because images are immutable
and tagged per commit, a rollback is a one-line change and takes seconds.

**Authentication:** the pipeline should authenticate to the cloud with OIDC federation
(short-lived tokens) rather than long-lived secrets stored in the repository.

---

## Operability

- **Health:** `GET /health`, also used by the Docker `HEALTHCHECK` (`HEALTHCHECK_URL` lets the
  same image be checked over HTTP or HTTPS). Liveness and readiness probes are at
  `/health/liveness` and `/health/readiness`.
- **Graceful shutdown:** in-flight requests complete before the application stops; the
  container runs Java as PID 1 so it receives `SIGTERM`.
- **Container:** multi-stage build, JRE-only Alpine runtime image, non-root user, heap sized
  from the container memory limit (`MaxRAMPercentage`).
- **Configuration** via environment variables:

| Variable | Purpose |
|---|---|
| `APP_SECURITY_API_KEYS` | Accepted API keys, comma-separated (required) |
| `SPRING_PROFILES_ACTIVE=tls` | Serve HTTPS on 8443 |
| `TLS_KEYSTORE_PATH`, `TLS_KEYSTORE_PASSWORD` | PKCS12 keystore with the certificate and key |
| `SERVER_PORT`, `APP_LOG_LEVEL` | Port and log level |

---

## What I would add next

- **Swagger UI** in docker-compose (the `swaggerapi/swagger-ui` image pointed at the spec), and
  a **breaking-change check** in CI that compares the spec against `main` (e.g. `oasdiff`).
- **Metrics and tracing:** Micrometer metrics (request count/latency per dish and channel)
  exposed for Prometheus, and OpenTelemetry tracing propagating the request id.
- **Rate limiting per API key**, and one named key per calling system so the logs show which
  system called, not only the channel it claims.
- **Richer context:** weekday vs weekend, or user time zone, would only add parameters to the
  `DishScorer` interface (e.g. a `ScoringContext` object) without changing the structure.

---

## Next step: managing dishes and scores at runtime

A natural next requirement is letting admins add dishes and change scores without a release.
I deliberately did not build it here: done properly it needs role-based security, concurrency
control and caching, and the brief asked for quality over scope. This is how I would approach
it.

**The current design already supports most of it.** `TimeWindowDishScorer` takes its windows as
data, so a database-backed scorer loading windows from a repository slots in behind the same
`DishScorer` interface; the controller and the scoring logic don't change. The one deliberate
change: `dish` would move from a contract enum to a string validated against the database,
because the list of dishes would no longer be known at build time.

**Data model (PostgreSQL, Flyway migrations seeding today's three dishes)**

```
dish          (id, name UNIQUE, default_score CHECK 0–10, version)
score_window  (id, dish_id → dish, start_time, end_time, score CHECK 0–10)
```

**Admin API (added to the OpenAPI spec first, as with everything else)**

| Method & path | Purpose |
|---|---|
| `POST /admin/dishes` | Create a dish with its default score and schedule |
| `GET /admin/dishes/{name}` | Read a dish and its schedule (returns an `ETag`) |
| `PUT /admin/dishes/{name}/schedule` | Replace the whole schedule atomically |
| `DELETE /admin/dishes/{name}` | Remove a dish |

Design decisions:
- **Replace the whole schedule in one request** instead of CRUD per time window. Overlap
  validation then happens in one place, and the schedule is never half-updated.
- **Validation:** no overlapping windows, no empty windows, scores 0–10. Violations return 400
  with details; the same `TimeWindow` and `Score` value objects enforce it.
- **Security:** admin endpoints need more than a shared API key: OAuth2 / JWT with an `admin`
  scope, via Spring Security's resource server.
- **Concurrency:** optimistic locking. Clients send `If-Match` with the `ETag` they read; if
  someone else changed the dish in between, the server returns `412 Precondition Failed`
  instead of silently overwriting their change.
- **Performance:** schedules are cached in memory and evicted when one changes, so scoring
  never waits on the database. With several instances, the eviction is broadcast (or a short
  TTL is accepted).
- **Testing:** repository and API tests against a real PostgreSQL with Testcontainers.
