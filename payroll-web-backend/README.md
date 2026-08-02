# Payroll Web Backend

Spring Boot 3 REST API for the web-based Super Admin console. JWT auth
against a dedicated `WebAdminAccount` store (separate from the desktop's
local `UserAccount` table) backed by PostgreSQL.

## Local dev database

Start a local Postgres with Docker:

```
docker run --name payroll-postgres -e POSTGRES_USER=payroll -e POSTGRES_PASSWORD=payroll -e POSTGRES_DB=payroll -p 5432:5432 -d postgres:16
```

`application.yml` points at `jdbc:postgresql://localhost:5432/payroll` with
user/pass `payroll`/`payroll` by default, matching the command above. Schema is
auto-generated on startup (`spring.jpa.hibernate.ddl-auto=update`).

Override the credentials via env vars for anything beyond local dev:

```
DB_USERNAME=<your-username>
DB_PASSWORD=<your-password>
```

On first startup with an empty account table, `AdminBootstrapRunner` creates
a default `admin` account with a randomly generated password, printed once to
the console. This is a dev/bootstrap convenience only — log in and rotate it
before relying on it for anything real.

## JWT secret

`jwt.secret` has no default — the app fails to start if `JWT_SECRET` isn't
set, so a known/guessable signing key never accidentally ships. Generate one
and export it before running the backend:

```
openssl rand -base64 32
```

Then set it as an environment variable in the shell you run the backend from:

```
# bash/zsh
export JWT_SECRET=<the-generated-value>

# PowerShell
$env:JWT_SECRET = "<the-generated-value>"
```

Use a different secret per environment; rotating it invalidates all
outstanding tokens.

## Tests

Integration tests use `io.zonky.test:embedded-postgres` (via
`@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)`), which
downloads and runs a real PostgreSQL binary per test run. This was chosen
over Testcontainers specifically so `mvn verify` doesn't require a running
Docker daemon — Testcontainers needs live Docker, while the Zonky provider
runs Postgres directly as a local process. Tests never touch the manually
started `payroll-postgres` container above; each test class gets its own
throwaway database.

Run all tests: `mvn verify` from the repo root.
