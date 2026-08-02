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
user/pass `payroll`/`payroll`, matching the command above. Schema is
auto-generated on startup (`spring.jpa.hibernate.ddl-auto=update`).

On first startup with an empty account table, `AdminBootstrapRunner` creates
a default `admin` account with a randomly generated password, logged once to
the console. This is a dev/bootstrap convenience only — log in and rotate it
before relying on it for anything real.

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
