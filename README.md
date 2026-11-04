# wallet-ledger-api

[![CI](https://github.com/OWNER/wallet-ledger-api/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/wallet-ledger-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)

A double-entry wallet ledger for a ride-sharing platform, built with Java 21 and Spring Boot.
Riders top up wallets with mobile money, pay for rides, drivers get paid out and the platform
takes a fee — and every one of those money movements is recorded as a balanced, immutable
journal entry.

> 🚧 Work in progress. This README is filled in as the phases land.

## Quick start

Requirements: Docker. Building and testing with `./mvnw` also needs a JDK 21.

```bash
docker compose up --build
```

- Health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

### Demo users

> ⚠️ **Demo only.** These accounts are seeded by `V3__users.sql` and their passwords are public.

| Email | Password | Role |
|---|---|---|
| `rider@demo.local` | `rider-demo-123` | USER: can read and move money only from wallets it owns |
| `admin@demo.local` | `admin-demo-123` | ADMIN: every account, plus reversals and reconciliation |

```bash
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"rider@demo.local","password":"rider-demo-123"}'
```

Send the returned `accessToken` as `Authorization: Bearer <token>` (or use **Authorize** in Swagger UI).

JWTs are signed with HS256 using the `JWT_SECRET` environment variable (at least 32 bytes).
The application refuses to start without it; `docker-compose.yml` supplies a local-only value.

If ports 8080 or 5432 are already taken on your machine:

```bash
APP_PORT=8081 DB_PORT=5434 docker compose up --build
```

## Build and test

```bash
./mvnw verify
```

`verify` runs the unit tests (`*Test`, Surefire) and the integration tests (`*IT`, Failsafe)
against a real PostgreSQL 16 started by Testcontainers, then writes a JaCoCo coverage report to
`target/site/jacoco/index.html`. Formatting is checked with `./mvnw spotless:check` and fixed
with `./mvnw spotless:apply`.

## License

[MIT](LICENSE) © Eris Zoe Girijambo
