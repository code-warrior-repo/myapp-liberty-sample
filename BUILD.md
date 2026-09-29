# Build and deploy

Prerequisites: JDK 21, installed Gradle 9.x, Node.js/npm. This sample does not bundle
Gradle wrappers, so use the installed `gradle` executable.

1. `cd frontend && npm install && npm run build`
2. From frontend-war: `gradle war` (also builds the frontend).
3. From backend: `gradle test bootWar`.
4. Create two Liberty server instances named `frontend` and `backend`:
   - Frontend: copy liberty/frontend/server.xml to usr/servers/frontend/server.xml
     and frontend-war/build/libs/frontend.war to usr/servers/frontend/apps/.
   - Backend: copy liberty/backend/server.xml to usr/servers/backend/server.xml
     and backend/build/libs/backend.war to usr/servers/backend/apps/.
   These directories may be in one Liberty installation or in separate installations.
5. Set APP_ENV, TOKEN_VALIDATION_URL and PERMISSIONS_URL in the backend instance's
   environment. SSO_URL defaults to https://abc.sso.com/ and FRONTEND_URL to /frontend/.
6. Start both instances. Frontend listens on HTTP 8080; backend on HTTP 8081.
7. Configure the public HTTPS reverse proxy to send /frontend and its descendants
   to the frontend instance on port 8080, and /backend and its descendants to the
   backend instance on port 8081. Preserve both path prefixes. Keep
   NEXT_PUBLIC_API_BASE=/backend for the frontend build.
8. Verify the real browser form login, /backend/api/auth/me, CSRF-protected writes,
   logout and idle expiry through the public HTTPS URL.

Production public URLs are https://prod.domain.com/frontend/ and
https://prod.domain.com/backend/. Test uses test.domain.com with the same paths.
The service adapters require the response contracts documented in README.md.

## Verification of this update

16 backend tests pass, including the browser login/session/CSRF flow and HTTP
service adapter contracts. Backend and frontend WAR builds pass. The packaged
backend starts successfully with the embedded container. Checks used the locally
installed JDK 25 compiler with --release 21; the project still targets JDK 21.
A live Liberty deployment and the real upstream services have not been tested.

The separate Liberty XML files were checked for valid XML, distinct ports, isolated
WAR deployments, and backend-only session configuration. Live Liberty startup and
reverse-proxy routing still require validation in your deployment environment.
