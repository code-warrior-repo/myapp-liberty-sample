# Browser SSO with Next.js, Spring Boot and Liberty

Public paths: https://test.domain.com/frontend/ and https://test.domain.com/backend/;
production uses the same paths on prod.domain.com. SSO: https://abc.sso.com/.
Run two separate Liberty instances behind the public HTTPS reverse proxy:

| Public path | Internal destination | Deployment |
| --- | --- | --- |
| /frontend/ | frontend Liberty host:8080/frontend/ | frontend.war |
| /backend/ | backend Liberty host:8081/backend/ | backend.war |

Preserve the path prefix when proxying. If both instances are on the proxy host,
the upstreams can be 127.0.0.1:8080 and 127.0.0.1:8081; otherwise use their actual
internal hostnames. Use liberty/frontend/server.xml and liberty/backend/server.xml
for the respective instances. The old combined liberty/server.xml is removed.
Never put an internal localhost backend address in browser code.

## Login contract

SSO submits a browser form (application/x-www-form-urlencoded) to
https://test.domain.com/backend/api/sso-login (prod uses prod.domain.com).
Fields: `userid`, `token`, `context`, `env`. All are required.
The browser Origin header must match SSO_URL (https://abc.sso.com).
The token service MUST verify that the token belongs to userid and the supplied context/env.
Use short-lived, single-use SSO tokens and validate the intended application audience upstream.
The environment must equal the server's APP_ENV; it never selects a service URL.

After validation and nonempty permissions, the backend creates a fresh 30-minute idle
session and redirects with HTTP 303 to /frontend/. Failed login (including upstream
errors) redirects to SSO and invalidates any prior session supplied with the request.
The original token is never stored in the session or returned to React.
Spring Security loads the authenticated principal from the session on each request.
No custom authentication interceptor is necessary.

GET /backend/api/auth/me returns userId, fullName, context, permissions,
csrfToken and csrfHeader. React sends the CSRF header for POSTs; the browser sends
MYAPP_SESSION automatically. Missing/expired sessions return 401; React navigates
to SSO. Insufficient permission returns 403 and is displayed without a login loop.
Logout invalidates the server session. The demo data endpoint requires USER to POST;
adapt this authority to your real permission vocabulary. Data is still demo data.

## Service adapters: assumed contracts, pending real service details

Both adapters currently send JSON POSTs. Configure fixed, trusted HTTPS endpoints:

- TOKEN_VALIDATION_URL receives {"userid":"jsmith","token":"...","context":"myapp","env":"test"}
  and returns {"valid":true,"fullName":"Jane Smith"}.
- PERMISSIONS_URL receives {"userId":"jsmith","context":"myapp"}
  and returns {"permissions":["USER","REPORT_VIEWER"]}.

Blank URLs, rejected tokens, blank names, empty/invalid permissions, timeouts and
upstream errors fail closed. No demo-token bypass exists. Adapt TokenValidator's
DTOs, HTTP methods and service authentication once the real contracts are provided.

Set APP_ENV=test or prod, SSO_URL=https://abc.sso.com/ and optionally FRONTEND_URL
(default /frontend/). The frontend uses NEXT_PUBLIC_SSO_URL at build time if SSO differs.
NEXT_PUBLIC_API_BASE defaults to /backend; keep frontend and API on the same public origin.

The backend Liberty instance owns the HttpOnly/Secure/Lax cookie with /backend path. Lax supports the
cross-site form submission followed by a top-level GET redirect. Only the SSO login
POST is exempt from CSRF validation; authenticated writes remain protected.
For deployed environments, use public HTTPS for browser testing. Both internal HTTP listeners assume TLS
termination at the proxy. The browser sees one origin, so frontend requests to
/backend go through the proxy to port 8081 without CORS configuration. A relative
redirect to /frontend/ goes through the proxy to port 8080. The session cookie's
/backend path keeps it off frontend asset requests; sessions live only in the backend.

For direct local development on localhost:8080 and localhost:8081, use the
separate local configurations and CORS setup documented in LOCAL_DEV.md. Standard
production builds continue to use the HTTPS proxy and relative /backend path.

The two instances serve different applications and do not need shared sessions.
If you later run multiple backend replicas, configure backend session persistence
or replication; no distributed session store is included.

See BUILD.md for build commands.
