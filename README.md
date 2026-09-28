# BaseApplicationSpringBoot

A Spring Boot base template for a JSON API with users and roles, JWT authentication, login throttling, and emailing. Starter to build on: it ships auth, authorization, seeding, and a tested endpoint contract you can extend instead of rewriting.

## Quick start

```bash
# 1. Create a MySQL database
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

The `dev` profile enables SQL logging and Security debug logging, disables mail template caching, and runs the seeder. On startup the seeder creates the roles and — only when the users table is empty — two accounts, both with `APP_SEED_PASSWORD`:

| Email | Role |
|-------|------|
| `admin@gmail.com` | `ADMIN` |
| `user@hotmail.com` | `USER` |

The seeder is `@Profile("dev")`; it does not run in production.

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
| POST | `/users/register` | `ADMIN` | Register a new user, assigning a single role (`ADMIN` or `USER`) |
| PUT | `/users/status/{id}` | `ADMIN` | Activate or deactivate a user |
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

The suite runs against an embedded H2 database in MySQL compatibility mode; no database needed.

## Swagger UI

Springdoc is included: `http://localhost:8085/api/swagger-ui/index.html`