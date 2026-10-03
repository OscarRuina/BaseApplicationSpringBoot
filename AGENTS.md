# BaseApplicationSpringBoot

Spring Boot 3 REST API (Java 17, Maven, Lombok) for user administration: JWT auth, login throttling, and transactional email (temporary credentials). Human-facing docs — run, configuration, full endpoint contract — live in [`README.md`](README.md).

## Commands

| Task | Command |
|------|---------|
| Full suite (Docker) | `docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app -u $(id -u):$(id -g) maven:3.9-eclipse-temurin-17 mvn -Duser.home=/tmp -B test` |
| Full suite (local) | `./mvnw test` |
| Run locally | `./mvnw spring-boot:run` — the `dev` profile reads `.env` (copy `.env.example` and fill the blanks). Without `dev`, export the vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TOKEN_SECRET_KEY`, `EMAIL`, `EMAIL_PASSWORD`; optional `MAIL_*`, `APP_SEED_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL`, `BOOTSTRAP_ADMIN_*` |
| Dev seeding | `@Profile("dev")` seeder: seeds `admin@gmail.com` (`ADMIN`) and `user@hotmail.com` (`USER`) with `APP_SEED_PASSWORD` when the DB is empty |

## Where things live

| Package | Responsibility | Key files |
|---------|---------------|-----------|
| `controllers` | REST endpoints | `SecurityController` (`/auth`), `UserController` (`/users`) |
| `services` | Business logic, transactional flows | `UserService` (register/reactivation send mail inside the tx) |
| `repositories` | Spring Data JPA queries | `IUserRepository`, `IRoleRepository` |
| `models` | Entities + enums | `UserEntity`, `RoleType` |
| `dtos` | Request/response contracts | `ApplicationResponse<T>` envelope |
| `configurations/security` | JWT filters + login throttle | `filters/SecurityErrorWriter`, `JwtFilter`, `throttle/LoginThrottle` |
| `configurations/email` | Mail sending + templates | `IEmailService`, `EmailService` |
| `messages` | Message constants | `ResponseMessages`, `ExceptionMessages` |
| `converters` | Entity → DTO mapping | `UserConverter` |

## Conventions

- Tests: JUnit 5 + MockitoExtension (strict stubs); integration tests extend `AbstractIntegrationTest` (`@SpringBootTest` on H2, dynamic JWT secret). Assertions carry a reason.
- The user owns commits; leave changes staged and let the user commit.
- Language mix in the repo: README, AGENTS.md and test display names in English; Javadoc and mail templates in Spanish. Match the file you edit.
- Project agents/skills live in `.opencode/`; global ones in `~/.config/opencode/`. Extend there, not in AGENTS.md.

## Gotchas

| Trap | Reality |
|------|---------|
| Mail inside the transaction | `register` and reactivation send email before the commit; a send failure rolls back the whole operation. Don't move the send out casually |
| `spring.jpa.open-in-view` | OSIV is enabled; don't 'fix' it without re-testing lazy paths |
| `findAllByRoleForUpdate` | Relies on a count invariant (one `ADMIN` role max per user), so it has no `distinct` |
| `register` role | Gone: the endpoint is public and takes no role. It hardcodes `USER` and takes no password either — a field an endpoint honours is a field a caller controls |
| Login throttling | In-memory per instance; behind a reverse proxy `getRemoteAddr()` sees the proxy, not the client (see `LoginThrottle` javadoc) |
| Pending vs suspended | `active_user` is the only login gate; `UserEntity.isPendingActivation()` (`activated_at_user IS NULL`) is the *only* place that distinguishes an unconfirmed registration from a suspended account. Every site that creates a user must set `activatedAt` — `UserService.register`, `UsersSeeder`, `BootstrapAdminInitializer`, the test seeder — or the API reports it as pending. `register` is now the public flow, and it is the one site that deliberately leaves it null; its fixtures in `UserControllerContractTests` do the same |
| Activation token | `V3` stores a SHA-256 hex of the emailed token, never the token itself; `UNIQUE` makes the lookup single-row. `ddl-auto=validate` forces the entity and the migration to ship together — Flyway runs before JPA validation in the same boot. The hash stays after a successful activation so the service can return `409` (already used) instead of conflating it with `400` (unknown); the single-use guarantee is `activatedAt == null` (`isPendingActivation()`), not the nulling of the token |
| `updateStatus` is not a password reset | It flips `active_user` and mails a plain notification; it never rotates the password. A suspended user logs back in with the password they chose, so the only way back in is an admin reactivating them |
| Pending accounts reject `updateStatus` | `PendingActivationException` → `409`, in both directions, and the guard sits **before** the no-op check: a pending account is already inactive, so "suspend it" would otherwise return a `200` that changes nothing. Any test fixture without `activatedAt` is a *pending* user, not a suspended one — `AbstractSecuredIntegrationTest.persist`, `ReactivateMailRollbackTests.saveInactiveUser` and the `UserServiceTests.user(...)` helper all set it |
| Activation redemption order | `used → expired → valid`. Reversed, a burned token past its window answers `410 "venció, esperate"` for a link that will never work; the caller waits forever for a mail that is not coming. `ActivationTokenAlreadyUsedException` is therefore reachable with an *already active* user, not a suspicious one |
| SHA-256 is not reversible | A test cannot take the stored hash and derive the token. `UserControllerContractTests` fixes a known token per fixture (`TOKEN_PREFIX + email`, hashed on persist) and derives it from the email, never from the hash — the earlier `plainTokenFor(hash)` was a fake inverse that made every activation test hit the `400` unknown-token branch while reading as green |
| Reissue needs a dead token | `register` re-mails only when the pending row's token has expired. Re-mailing a live token turns a convenience endpoint into an unauthenticated mail cannon at one inbox, so that case is `409` |
| Register sends before commit | A mail failure rolls the row back, but the throttle counter is in-memory and does *not* roll back: a broken SMTP both leaves no user and burns the client's hourly budget. `RegisterMailRollbackTests` asserts the 21st attempt gets `429` even though all 20 rolled back — that is the intended interaction, not a bug |
| Public endpoints stay out of the auth matrix | `UserControllerAuthorizationTests.allEndpoints()` lists only token-guarded routes. `publicEndpoints()` carries `/users/register` and `/users/activate`: with a token they must still reach the service, so listing them as guarded makes the filter's real behaviour (permit) look like a security failure |
| Throttle budget order | The service calls `retryAfterSeconds` *before* `recordAttempt`, so `maxAttempts` requests pass and the next is blocked. A test that records N times and then checks has already simulated request N+1 and will fail against a correct throttle. `Retry-After` is the remaining window **rounded up** in whole seconds, so a full window reports 3599, not 3600 — assert a range, never the exact value |
| Bulk purge has no test on purpose | `enforceBound` purges expired entries with `removeIf` before evicting oldest-first. Those always select the same entries (expired == lowest `startedNanos`), so removing the purge keeps the suite green and only changes cost from O(n) to O(n^2). Verified by mutation; not a coverage gap. `RegistrationThrottleTests` says so at the bottom |
| Error envelope | `ApplicationResponse` uses `@JsonInclude(NON_NULL)`: errors are `{"message": ...}` with no `data` field |
| Schema authority | Flyway owns the schema, `ddl-auto=validate` only verifies it. Add a new `V<n>__*.sql` in `src/main/resources/db/migration`; never edit one that has been applied |
| Migrations vs suite | The suite runs H2 with `spring.flyway.enabled=false`, so a migration that breaks MySQL stays green. Verify against real MySQL 8.0 before merging |
| Roles come from `V2` | `RoleService.findRoleByType` throws when the row is missing, so an empty `roles` table breaks registration. The `dev` seeder's `loadRoles()` is only a test-suite safety net |
| First admin in production | `BootstrapAdminInitializer` is the only supported way to create an admin outside `dev`. Opt-in via `BOOTSTRAP_ADMIN_*`, runs last (seeder is `@Order(0)`), never promotes an existing account, and fails the boot on a weak password. The password policy is duplicated from `UpdateUserRequestDTO` on purpose — `save()` runs no bean validation — so change both together |
| Bootstrap vs H2 suite | Its repository queries and insert only run against MySQL in production; `ApplicationContextRunner` covers the condition and ordering, not the SQL. `/tmp/opencode/verify-bootstrap.sh` checks the four real-MySQL scenarios |
| `.env` is read as a properties file | `spring.config.import=optional:file:.env[.properties]` lives in `application-dev.properties`, so it applies to the `dev` profile only — every test runs under `test` and never sees it. Spring Boot 3.1.5 has no dotenv support; this import is the whole mechanism. Parsed by the properties loader, not a real dotenv: `#` opens a comment and `\` escapes, so a `#` inside a password truncates it and only fails later, at connection time. No `$VAR` expansion, and it cannot set `SPRING_PROFILES_ACTIVE` — the profile decides whether the file is read |
| Imports lose to real env vars | Config imports are registered with `propertySources.addLast`, i.e. the lowest precedence, so an exported `DB_URL` overrides `.env`. Keep it that way: it is what lets Docker and CI inject variables and stay unaffected. |

## Endpoints (overview)

Context path `/api`; every route requires a JWT except the public ones: `POST /auth/login` and, since `b6dae80`, `POST /users/register` and `POST /users/activate`.

| Area | Routes | Role |
|------|--------|------|
| Auth | `POST /auth/login` | public (throttled) |
| Profile | `GET/PUT /users/me`, `PUT /users` | `ADMIN`, `USER` |
| Admin | `GET /users` (+ `/active`, `/{id}`), `PUT /status/{id}`, `PUT /roles/{id}`, `DELETE /{id}` | `ADMIN` |
| Public | `POST /users/register`, `POST /users/activate` | none |

Full endpoint table, response contract and parameter details: [`README.md`](README.md).

## Backlog

- Forgot/reset password flow (mail temporary credentials) — not implemented. Pattern to follow: the register/reactivation email flow. Reactivation deliberately stopped rotating passwords, so a forgotten password has no way back today; only an admin can reactivate an account whose password is lost.
- Email enumeration on `POST /users/register` — a duplicate email answers `409` **without** spending throttle budget, so the endpoint cannot distinguish "in use" from "free" at a bounded rate. Deliberate: charging the `409` would lock out anyone who mistypes their address. Rate-limit this path at the proxy if enumeration matters to you.
- Throttle behind a reverse proxy — `RegistrationThrottle` keys on `getRemoteAddr()`, which is the proxy, not the client. Behind a proxy the 20/hour budget becomes one global budget for every user, so a signup burst from a single NAT egress can block legitimate registrations. Same caveat as `LoginThrottle`, and it needs a trusted-forwarded-for header to fix — not solvable inside this repo.
- `updateRole` replaces rather than adds — promoting a `USER` to `ADMIN` drops any other role it had. It matches the endpoint's documented contract ("Replace the roles assigned to a user"), so it is not a bug, but confirm it is intended before relying on multi-role users.