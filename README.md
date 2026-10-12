# Workshop Management Backend

Spring Boot 3 (Java 21) REST API for workshops: JWT auth, workshop catalogue, registrations with capacity control, contact form, admin tools, email notifications, password reset, Swagger UI.

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
Required: `DB_*`, `JWT_SECRET` (32+ chars). Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` to create the first admin on startup (there is no public admin registration). Set `FRONTEND_URL` to the deployed Vercel site origin (for example, `https://your-app.vercel.app`; no trailing slash required) so password-reset links return to the frontend. For Gmail SMTP, set `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME` to the sender Gmail address, `MAIL_PASSWORD` to a Google App Password, and `MAIL_FROM` to the same sender address. Leave `MAIL_HOST` empty to disable email sending.

## 4. Run
```bash
mvn verify               # compiles and runs the tests (H2, no PostgreSQL needed)
mvn spring-boot:run
```

## 5. Swagger and testing
Open http://localhost:8080/swagger-ui/index.html
1. `POST /api/auth/login` with your admin (or a registered user), copy the `token`.
2. Click **Authorize**, paste the token (without `Bearer `), then try the protected endpoints.

Typical flow: admin creates a workshop (`POST /api/admin/workshops`, starts as DRAFT) -> publishes it (`PATCH /api/admin/workshops/{id}/status` with `{"status":"PUBLISHED"}`) -> a user registers (`POST /api/workshops/{id}/registrations`, status `PENDING`) -> admin approves/rejects (`PATCH /api/admin/registrations/{registrationId}/status` with `{"status":"CONFIRMED"}` or `{"status":"REJECTED"}`) -> the matching email is sent after the status change commits.

## 5a. Password reset flow
1. `POST /api/auth/forgot-password` with `{"email":"asha@example.com"}`. It always returns `200` with the same generic message to avoid revealing whether the account exists.
2. If the account exists and SMTP is configured, the user receives a reset email with a link to `${FRONTEND_URL}/reset-password?token=...`.
3. The link token expires after 30 minutes, is stored only as a SHA-256 hash, and can be used once.
4. The frontend submits `POST /api/auth/reset-password` with `{"token":"...","newPassword":"Str0ngPass!"}`. Passwords must be 8–72 characters and contain at least one letter and one digit. Invalid, expired, or used tokens return `400`.

## 5b. Google Form registration flow
1. Admin creates a workshop with `registrationFormUrl` (required, a Google Form link). It is visible to admins only; users get it from `GET /api/workshops/{id}/registration-form`.
2. The React app opens that link. The user fills in the form **with the same email as their account**.
3. The form's Apps Script posts to `POST /api/integrations/google-forms/submissions` (header `X-Webhook-Secret`, no JWT) and a `PENDING` request is created (`submissionSource=FORM_WEBHOOK`).
4. Admin approves/rejects via `PATCH /api/admin/registrations/{id}/status`; the user then gets the confirmation/rejection email.

Setup per form: turn on **Collect email addresses**, open *Extensions -> Apps Script*, paste the script below, set `GOOGLE_FORM_WEBHOOK_SECRET` on the server to the same value, and add an **On form submit** trigger for `onFormSubmit`.
```javascript
const API = 'https://YOUR-BACKEND/api/integrations/google-forms/submissions';
const SECRET = 'same value as GOOGLE_FORM_WEBHOOK_SECRET';
const WORKSHOP_ID = 12;   // id of the workshop this form belongs to
function onFormSubmit(e) {
  UrlFetchApp.fetch(API, {method: 'post', contentType: 'application/json',
    headers: {'X-Webhook-Secret': SECRET},
    payload: JSON.stringify({workshopId: WORKSHOP_ID, email: e.response.getRespondentEmail()}),
    muteHttpExceptions: true});
}
```
Fallback: `POST /api/workshops/{id}/registrations` ("I submitted" button) also creates a `PENDING` request with `submissionSource=WEB_APP`. The server cannot verify the form was filled in, so admins should check the form responses for those, or set `GOOGLE_FORM_ALLOW_USER_CLAIM=false` to accept only webhook submissions. Pending requests count against capacity until approved/rejected.
Emails are sent from `MAIL_FROM`, or from `MAIL_USERNAME` when `MAIL_FROM` is empty.

## 6. Deploy on Render (Docker)
Push the repo, then create a Blueprint from `render.yaml` (it provisions PostgreSQL and the web service). Set `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS` (your Vercel URL, e.g. `https://my-app.vercel.app`; `https://*.vercel.app` also works), `FRONTEND_URL` to the exact Vercel site origin, and the `MAIL_*` variables for Gmail SMTP (`MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME`, `MAIL_PASSWORD` using a Google App Password, and `MAIL_FROM` matching the sender address).

## 7. React / Vercel
Send `Authorization: Bearer <token>` on protected calls. Errors always look like `{timestamp,status,error,message,path,fieldErrors?}`. Lists are paginated with `page`, `size`, `sort` (`field,asc|desc`).
