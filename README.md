# BaseApplicationSpringBoot

A Spring Boot base template for a JSON API with users and roles, JWT authentication, login throttling, and emailing. Starter to build on: it ships auth, authorization, seeding, and a tested endpoint contract you can extend instead of rewriting.

## Quick start

```bash
# 1. Create an empty MySQL database (the tables come from the Flyway migrations)
mysql -e "create database base_app;"

# 2. Export the required environment variables (see Configuration)
export DB_URL="jdbc:mysql://localhost:3306/base_app"
export DB_USERNAME="root"
export DB_PASSWORD="your-db-password"
export TOKEN_SECRET_KEY="$(openssl rand -base64 32)"
export EMAIL="your-bot@example.com"
export EMAIL_PASSWORD="your-app-password"

# 3. Run it
SPRING_PROFILES_ACTIVE=dev APP_SEED_PASSWORD=your-dev-password mvn spring-boot:run

# 4. Log in and read your own profile
curl -s http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@gmail.com","password":"<APP_SEED_PASSWORD>"}'
curl -s http://localhost:8085/api/users/me \
  -H "Authorization: Bearer <token>"
```

Spring Boot does **not** read a `.env` file (no dotenv dependency). Export the variables into the actual environment, or set them in your IDE run configuration.

## Configuration

Base URL: `http://localhost:8085/api` (port 8085, context path `/api`).

### Required (no default, the app fails to start without them)

| Variable | Purpose |
|----------|---------|
| `DB_URL` | JDBC URL of your database |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `TOKEN_SECRET_KEY` | Secret used to sign JWTs — use a strong random key (e.g. `openssl rand -base64 32`) |
| `EMAIL` | Sender address for outgoing mail |
| `EMAIL_PASSWORD` | Sender password (for Gmail: an app password) |

### Required for the `dev` profile

| Variable | Purpose |
|----------|---------|
| `APP_SEED_PASSWORD` | Password assigned to the seeded users |

### Optional (defaults shown)

| Variable | Default | Purpose |
|----------|---------|---------|
| `MAIL_HOST` | `smtp.gmail.com` | SMTP host |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_STARTTLS` | `true` | Enable STARTTLS on the SMTP connection |
| `MAIL_AUTH` | `true` | Authenticate against the SMTP server |
| `MAIL_CONNECTION_TIMEOUT` | `5000` | Connection timeout (ms) |
| `MAIL_TIMEOUT` | `5000` | Socket timeout (ms) |
| `MAIL_WRITE_TIMEOUT` | `5000` | Write timeout (ms) |
| `MAIL_TEMPLATE_CACHE` | `true` | Cache email templates |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed cross-origin origins |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | Frontend URL used to build links in emails |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` | Enable the first-admin initializer |
| `BOOTSTRAP_ADMIN_EMAIL` | _(empty)_ | Email of the admin to create |
| `BOOTSTRAP_ADMIN_PASSWORD` | _(empty)_ | Password of that admin, 12-72 printable ASCII characters |

### Login throttle

| Property | Default | Meaning |
|----------|---------|---------|
| `app.login.throttle.free-attempts` | `3` | Failures allowed before throttling kicks in |
| `app.login.throttle.base-delay` | `1000` | Initial backoff (ms) |
| `app.login.throttle.max-delay` | `30000` | Backoff cap (ms) |
| `app.login.throttle.reset-after` | `900000` | Window after which a counter resets (ms) |
| `app.login.throttle.client-max-failures` | `30` | Failures allowed from a single client before IP throttling |
| `app.login.throttle.client-window` | `300000` | IP counter window (ms) |
| `app.login.throttle.max-entries` | `10000` | Concurrent counter entries kept |

## Development profile

Run with `dev` active to mirror local conventions:

```bash
SPRING_PROFILES_ACTIVE=dev APP_SEED_PASSWORD=your-dev-password mvn spring-boot:run
```

The `dev` profile enables SQL logging and Security debug logging, disables mail template caching, and runs the seeder. On startup the seeder — only when the users table is empty — creates two accounts, both with `APP_SEED_PASSWORD`:

| Email | Role |
|-------|------|
| `admin@gmail.com` | `ADMIN` |
| `user@hotmail.com` | `USER` |

The seeder is `@Profile("dev")`; it does not run in production. The roles themselves come from migration `V2`, not from the seeder — see [Database migrations](#database-migrations).

## First admin in a fresh environment

The dev seeder does not run in production, so a brand-new production database has nobody who can log in. `BootstrapAdminInitializer` covers that one case: opt in with three variables and it creates the first `ADMIN` on startup.

```bash
BOOTSTRAP_ADMIN_ENABLED=true \
BOOTSTRAP_ADMIN_EMAIL=root@yourdomain.com \
BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-one' \
java -jar target/application.jar
```

It runs only when all three conditions hold:

| Condition | Behaviour when it does not hold |
|-----------|--------------------------------|
| `BOOTSTRAP_ADMIN_ENABLED=true` | The bean does not even exist, nothing is logged |
| No user holds the `ADMIN` role | Logs a WARN and skips, so redeploys are safe |
| The email is not already taken | Logs a WARN and skips. An existing account is **never** promoted |

The password must satisfy the same policy as `PUT /users`: 12 to 72 printable ASCII characters (72 is the BCrypt limit). A malformed email or a password outside that policy fails the startup instead of creating a weak account.

What this component is not: it is not a way to activate somebody else's registration, and it is not a temporary-password flow. The credential is chosen by the operator before the account exists and stays valid until someone changes it through `PUT /users`. **Remove the three variables once the environment is provisioned.** Leaving them set is harmless — the second boot skips — but the password sits in the environment or your secret store longer than it needs to.

The initializer runs as the last `CommandLineRunner`, after the dev seeder. That ordering matters: the seeder only creates its two accounts when the users table is empty, so a bootstrap running first would leave the dev profile without its regular user.

## Database migrations

The schema belongs to Flyway. `spring.jpa.hibernate.ddl-auto=validate` means Hibernate only **verifies** that the database matches the entities at startup and fails fast when it does not; it never creates or alters anything.

Migrations live in `src/main/resources/db/migration`, named `V<n>__<description>.sql`, and run automatically on startup in version order. `flyway_schema_history` records what already ran, so only new versions are applied.

| Migration | What it does |
|-----------|--------------|
| `V1__create_schema.sql` | Creates `users`, `roles`, `users_roles` |
| `V2__seed_roles.sql` | Inserts the `USER` and `ADMIN` roles |
| `V3__add_activation_state.sql` | Adds the activation columns and backfills `activated_at` |

Two starting points, both handled by the same configuration:

- **Empty database** → `V1` creates the tables, `V2` seeds the roles.
- **Database that already exists** (created by an earlier `ddl-auto=update`) → `flyway.baseline-on-migrate` marks it as version 1, so `V1` is skipped and only `V2` runs.

That is exactly why `V1` exists: `baseline-on-migrate` only baselines a **non-empty** schema, so an empty one has no way to obtain the tables that `V2` inserts into.

To change the schema, add a new `V3__...`, `V4__...` file. Never edit a migration that has already been applied anywhere — Flyway will not notice and two databases will silently diverge.

### Notes and caveats

- **The roles are a schema invariant, not seed data.** `RoleService.findRoleByType` throws `InvalidRoleException` (HTTP 400) when the row is missing, so an empty `roles` table makes registration fail for every caller. That is why `V2` exists, and why `V2` uses `INSERT IGNORE`: on a pre-existing database the roles may already be there, and the unique constraint on `name_role` would otherwise abort the migration. The seeder's `loadRoles()` is now only a safety net for the test suite, which runs without migrations.
- **`roles.name_role` is a MySQL `enum`**, because `V1` is generated by Hibernate rather than hand-written. Adding a value to `RoleType` requires a migration that widens the column: `ALTER TABLE roles MODIFY name_role enum('ADMIN','USER','NEWROLE')`.
- **`V1` is generated, not typed by hand.** Hibernate maps `boolean` to `bit(1)` on MySQL, and a hand-written guess that disagrees with the entity mapping makes `validate` fail at startup. After changing an entity, regenerate it with `ddl-auto=none` and `jakarta.persistence.schema-generation.scripts.action=create` on `MySQLDialect` rather than editing the file.
- **The test suite does not run migrations.** It runs on H2 with `spring.flyway.enabled=false`, because the migrations are MySQL-only SQL. A migration that breaks MySQL can therefore leave the suite green — verify against a real MySQL before merging.
- **`flyway-mysql` is required next to `flyway-core`.** `flyway-core` carries no MySQL support; without that module the app fails at startup with `Unsupported Database: MySQL`. Both versions come from the Spring Boot BOM (Flyway 9.16.3 on Boot 3.1.5), so neither declares a version.
- **`active_user` alone cannot tell pending from suspended.** `active_user` is still the only login gate, and `activated_at_user` is what says *why* a user is inactive: `NULL` means a registration nobody confirmed, `NOT NULL` with `active_user = 0` means an administrator suspended somebody who had logged in before. `UserEntity.isPendingActivation()` is the single place that distinction is read, and the API exposes it as `activatedAt`. The backfill in `V3` is deliberately unconditional — every row that predates public registration is as confirmed as it will ever be, including the suspended ones.
- **`activation_token_user` stores a SHA-256, never the token.** A database dump cannot be replayed against the activation endpoint, and the `UNIQUE` constraint is what guarantees the lookup returns at most one user. The hash **is kept after a successful activation** so the endpoint can answer `409` for a token that was already used instead of pretending it never existed; the single-use guarantee comes from `activatedAt`, not from deleting the hash.

## Endpoints

All routes are under `/api`. Every request requires an `Authorization: Bearer <token>` header except the public ones: `POST /auth/login`, `POST /users/register` and `POST /users/activate`.

| Method | Path | Role | Action |
|--------|------|------|--------|
| POST | `/auth/login` | public | Authenticate with `{"username", "password"}` and receive a token |
| GET | `/users/me` | `ADMIN`, `USER` | Read the authenticated user's profile |
| PUT | `/users` | `ADMIN`, `USER` | Update the authenticated user's own profile |
| GET | `/users` | `ADMIN` | List all users |
| GET | `/users/active` | `ADMIN` | List active users |
| GET | `/users/{id}` | `ADMIN` | Get a user by id |
| POST | `/users/register` | public | Create a pending account and mail the activation link |
| POST | `/users/activate` | public | Redeem the emailed token and choose the password |
| PUT | `/users/status/{id}` | `ADMIN` | Activate or deactivate a user — reactivation notifies by mail and keeps the current password |
| PUT | `/users/roles/{id}` | `ADMIN` | Replace the roles assigned to a user |
| DELETE | `/users/{id}` | `ADMIN` | Delete a user |

## Response contract

Errors and successes share one envelope, `ApplicationResponse<T>`:

- **Success** → `{"data": ..., "message": "..."}`
- **Error** → `{"message": "..."}` — the null `data` field is omitted.

Example error body:

```json
{"message": "Bad credentials"}
```

## Security notes

- **JWT** tokens expire after **1 hour** (`jwt.token.expiration=3600000`).
- **Login throttling** limits brute force: after `free-attempts` failures on an account, backoff grows exponentially from `base-delay` up to `max-delay`; the client IP has its own higher threshold with an independent window. Counters live in memory. Read the `LoginThrottle` class javadoc before deploying behind a reverse proxy or running more than one instance — `getRemoteAddr()` sees the proxy, not the client, and in-memory state is per instance.
- **Suspension is not password reset.** `PUT /users/status/{id}` only flips `active_user` and mails a plain notification on reactivation. It never rotates the password, because an account that gets a mail saying "your account is active, log in with your usual password" while its password was silently replaced is a lockout, not a recovery. `active_user` still blocks the login, so a suspended user cannot get in until an administrator reactivates them.
- **A pending registration cannot be suspended.** A user whose `activatedAt` is null is waiting on the activation mail, not serving a suspension, so `PUT /users/status/{id}` answers `409` in both directions. Reactivating one would hand an account to somebody who never proved they own the address, and "suspending" an already inactive account would otherwise return a `200` that changes nothing.
- **Public registration is create-pending, not create-active.** `POST /users/register` takes `{"firstname", "lastname", "email"}` and nothing else: the role is fixed to `USER` server-side and the password is not accepted, because a field an endpoint honours is a field a caller controls. The row is written with `active = false` and `activatedAt = null`, and its `password` column holds a random value the user never receives — the column is `NOT NULL`, and an empty string would be a guessable password sitting in the table. The account cannot log in until `POST /users/activate` redeems the emailed link.
- **The activation token is single-use and its life has an end.** The mail carries a 64-hex-char token; the database stores only its SHA-256. The hash stays after a successful activation so the endpoint can tell *used* (`409`) from *never existed* (`400`) instead of collapsing both into "bad link", and the single-use guarantee comes from `activatedAt == null` — not from deleting the token. Redemption order is **used → expired → valid**, so a burned token reports `409` even after its window closed. A token is valid for `ACTIVATION_TTL` (7 days); an expired token on an *unconfirmed* account is rotated and re-mailed by a new `POST /users/register` with the same address, because otherwise the owner has no way back in. A live token is not re-mailed, which keeps the endpoint from being a mail cannon at somebody's inbox.
- **Registration throttling** is separate from login throttling: 20 mail-sending registrations per hour per IP, `429` plus a `Retry-After` header in whole seconds. A `409` duplicate email does not spend budget, so a user who mistypes their address is not locked out — but that also means email enumeration is bounded by your own edge, not by this counter, so rate-limit at the proxy if that matters to you. Both throttles are in-memory and keyed on `getRemoteAddr()`; read the class javadoc before deploying behind a reverse proxy or running more than one instance.

## Testing

```bash
docker run --rm \
  -v "$PWD":/app -v "$HOME/.m2":/root/.m2 \
  -w /app -u "$(id -u):$(id -g)" \
  maven:3.9-eclipse-temurin-17 \
  mvn -Duser.home=/tmp -B test
```

The suite runs against an embedded H2 database in MySQL compatibility mode; no database needed. Flyway is disabled for it (`spring.flyway.enabled=false`), so the migrations are not exercised — see [Database migrations](#database-migrations).

## Swagger UI

Springdoc is included: `http://localhost:8085/api/swagger-ui/index.html`