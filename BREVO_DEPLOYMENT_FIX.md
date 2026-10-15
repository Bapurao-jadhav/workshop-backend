# Brevo email deployment fix

This project sends transactional emails using Brevo's HTTPS API only. Gmail SMTP is not used.

## Render environment variables

Set these in Render > backend service > Environment:

- `BREVO_API_KEY`: a valid Brevo API key (keep secret)
- `MAIL_FROM`: the exact sender email address verified in Brevo
- `BREVO_SENDER_NAME`: `arQnare` or your preferred sender display name
- `FRONTEND_URL`: your deployed frontend origin, used to build password reset links

Do not define `BREVO_API_KEY=${BREVO_API_KEY:}` in application.properties. The Java service reads the environment variable directly.

## Deploy

1. Replace the repository source with this project version and commit the changes.
2. Push to the branch configured in Render.
3. Confirm Render's latest deploy corresponds to that commit.
4. Trigger a password reset or workshop registration.
5. Successful requests log `Brevo accepted ...`. Failures log `Brevo rejected ... (HTTP status)` or `Could not send ... through Brevo`.

If logs still mention `smtp.gmail.com`, Render is not running this source version or a different service/code path is being invoked. Verify Render's connected repository, branch, build command, and latest deployed commit.
