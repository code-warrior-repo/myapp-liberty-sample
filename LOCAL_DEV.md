# Local development: frontend 8080, backend 8081

Use localhost consistently for both URLs (do not mix localhost and 127.0.0.1).

## Backend on Liberty

1. Build the backend: from backend, run `gradle test bootWar`.
2. Copy liberty/local/backend/server.xml to your backend Liberty instance's
   usr/servers/backend/server.xml, and backend/build/libs/backend.war to its apps/.
3. Copy liberty/local/backend/server.env.example to usr/servers/backend/server.env.
   Set TOKEN_VALIDATION_URL and PERMISSIONS_URL to your real services; set APP_ENV
   to the env value that SSO sends. Keep these local settings:

   ```properties
   CORS_ALLOWED_ORIGINS=http://localhost:8080
   FRONTEND_URL=http://localhost:8080/frontend/
   SSO_URL=https://abc.sso.com/
   ```

4. Restart the backend Liberty instance.

Local server.xml uses HttpOnly/Lax cookies with Secure=false because this listener
uses HTTP. It binds to localhost. Production configurations retain Secure=true.
The backend still validates real SSO tokens and permissions; there is no login bypass.

## Frontend: choose one

### Next.js dev server with live reload

From frontend, run `npm ci` then `npm run dev`. This starts localhost:8080 and reads
.env.development, which points API calls to http://localhost:8081/backend.
Stop the frontend Liberty instance first so port 8080 is free.

### Static frontend on its own Liberty instance

From frontend, run `npm ci` then `npm run build:local`.
From frontend-war, run `gradle war -x frontendBuild` to package that local export.
Copy the generated frontend.war to usr/servers/frontend/apps/, and use
liberty/local/frontend/server.xml as that instance's server.xml. Restart it.
The build:local command uses a POSIX shell (macOS/Linux).

Open http://localhost:8080/frontend/. Configure your SSO's local return form to POST
http://localhost:8081/backend/api/sso-login. Login success redirects explicitly to
port 8080. The SSO form's Origin must still match SSO_URL. If your SSO/browser does
not permit posting to local HTTP, use an HTTPS development proxy instead.

## How CORS works here

The backend allows the exact configured frontend origin, including its port, with
credentials. It handles OPTIONS before session authentication, allows JSON and
X-CSRF-TOKEN headers, and includes CORS headers on 401/403 API responses. CSRF
protection remains enabled. The browser form login is separate from frontend fetch
and continues to use its existing SSO Origin check.

A 401 now means no valid session; the frontend navigates to SSO. This is distinct
from a CORS failure. Merely opening the frontend does not create a logged-in session.

For another local frontend port, update CORS_ALLOWED_ORIGINS and FRONTEND_URL.
Multiple trusted origins may be comma-separated; do not use a wildcard with cookies.

For test/prod, build normally (`npm run build` or `gradle war`), use the standard
liberty/frontend and liberty/backend configurations, and leave CORS_ALLOWED_ORIGINS
empty when both public paths share one HTTPS origin. Do not deploy a local export.
NEXT_PUBLIC_API_BASE is baked into static output: restart the dev server or rebuild
and redeploy the frontend WAR after changing it.
