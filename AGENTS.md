# BaseApplicationSpringBoot

Spring Boot 3 REST API (Java 17, Maven, Lombok) for user administration: JWT auth, login throttling, and transactional email (temporary credentials). Human-facing docs — run, configuration, full endpoint contract — live in [`README.md`](README.md).

## Commands

| Task | Command |
|------|---------|
| Full suite (Docker) | `docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app -u $(id -u):$(id -g) maven:3.9-eclipse-temurin-17 mvn -Duser.home=/tmp -B test` |
| Full suite (local) | `./mvnw test` |
| Run locally | `./mvnw spring-boot:run` (env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TOKEN_SECRET_KEY`, `EMAIL`, `EMAIL_PASSWORD`; optional `MAIL_*`, `APP_SEED_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL`) |
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
| `register` role | Accepts only `USER` (`InvalidRoleException` otherwise) |
| Login throttling | In-memory per instance; behind a reverse proxy `getRemoteAddr()` sees the proxy, not the client (see `LoginThrottle` javadoc) |
| Error envelope | `ApplicationResponse` uses `@JsonInclude(NON_NULL)`: errors are `{"message": ...}` with no `data` field |

## Endpoints (overview)

Context path `/api`; every route except `POST /auth/login` requires a JWT.

| Area | Routes | Role |
|------|--------|------|
| Auth | `POST /auth/login` | public (throttled) |
| Profile | `GET/PUT /users/me`, `PUT /users` | `ADMIN`, `USER` |
| Admin | `GET /users` (+ `/active`, `/{id}`), `POST /register`, `PUT /status/{id}`, `PUT /roles/{id}`, `DELETE /{id}` | `ADMIN` |

Full endpoint table, response contract and parameter details: [`README.md`](README.md).

## Backlog

- Forgot/reset password flow (mail temporary credentials) — not implemented; pattern is the register/reactivation email flow.