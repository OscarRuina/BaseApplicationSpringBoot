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

## Database migrations

The schema belongs to Flyway. `spring.jpa.hibernate.ddl-auto=validate` means Hibernate only **verifies** that the database matches the entities at startup and fails fast when it does not; it never creates or alters anything.

Migrations live in `src/main/resources/db/migration`, named `V<n>__<description>.sql`, and run automatically on startup in version order. `flyway_schema_history` records what already ran, so only new versions are applied.

| Migration | What it does |
|-----------|--------------|
| `V1__create_schema.sql` | Creates `users`, `roles`, `users_roles` |
| `V2__seed_roles.sql` | Inserts the `USER` and `ADMIN` roles |

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

## Endpoints

All routes are under `/api`. Every request except `POST /auth/login` requires an `Authorization: Bearer <token>` header.

| Method | Path | Role | Action |
|--------|------|------|--------|
| POST | `/auth/login` | public | Authenticate with `{"username", "password"}` and receive a token |
| GET | `/users/me` | `ADMIN`, `USER` | Read the authenticated user's profile |
| PUT | `/users` | `ADMIN`, `USER` | Update the authenticated user's own profile |
| GET | `/users` | `ADMIN` | List all users |
| GET | `/users/active` | `ADMIN` | List active users |
| GET | `/users/{id}` | `ADMIN` | Get a user by id |
| POST | `/users/register` | `ADMIN` | Register a new user with the `USER` role |
| PUT | `/users/status/{id}` | `ADMIN` | Activate or deactivate a user — reactivation rotates the password and emails a new temporary one |
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