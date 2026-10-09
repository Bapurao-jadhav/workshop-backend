# Workshop Management Backend

Spring Boot 3 (Java 21) REST API for workshops: JWT auth, workshop catalogue, registrations with capacity control, contact form, admin tools, email confirmations, Swagger UI.

> **Build status:** this project was written in an environment with no access to Maven Central, so it has **not been compiled or tested yet**. Run `mvn verify` first (see below) and fix anything it reports.

## 1. Prerequisites
Java 21, Maven 3.9+ (no Maven Wrapper is included), PostgreSQL 14+.

## 2. Database
```sql
CREATE DATABASE workshop_db;
```
Tables are created automatically by Flyway (`src/main/resources/db/migration`).

## 3. Configure
```bash
cp .env.example .env     # then edit the values
```
The app reads `./.env` automatically; real environment variables take precedence.
Required: `DB_*`, `JWT_SECRET` (32+ chars). Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` to create the first admin on startup (there is no public admin registration). Leave `MAIL_HOST` empty to run without email (confirmations are logged instead).

## 4. Run
```bash
mvn verify               # compiles and runs the tests (H2, no PostgreSQL needed)
mvn spring-boot:run
```

## 5. Swagger and testing
Open http://localhost:8080/swagger-ui/index.html
1. `POST /api/auth/login` with your admin (or a registered user), copy the `token`.
2. Click **Authorize**, paste the token (without `Bearer `), then try the protected endpoints.

Typical flow: admin creates a workshop (`POST /api/admin/workshops`, starts as DRAFT) -> publishes it (`PATCH /api/admin/workshops/{id}/status` with `{"status":"PUBLISHED"}`) -> a user registers (`POST /api/workshops/{id}/registrations`).

## 6. Deploy on Render (Docker)
Push the repo, then create a Blueprint from `render.yaml` (it provisions PostgreSQL and the web service). Set `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS` (your Vercel URL, e.g. `https://my-app.vercel.app`; `https://*.vercel.app` also works) and optionally the `MAIL_*` variables.

## 7. React / Vercel
Send `Authorization: Bearer <token>` on protected calls. Errors always look like `{timestamp,status,error,message,path,fieldErrors?}`. Lists are paginated with `page`, `size`, `sort` (`field,asc|desc`).
