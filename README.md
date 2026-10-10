# PlantDesk

**Spot the machine that's wearing out before it stops your plant.**

A multi-tenant maintenance management system (CMMS) for small and mid-size industrial plants.
Each plant is a tenant. Inside it: an asset hierarchy, breakdown and preventive work orders,
technicians, spare parts, PM schedules with dual triggers, and MTBF/MTTR reliability analytics.

| | |
|---|---|
| **Live app** | **https://plantdesk-production.up.railway.app** |
| **Source** | https://github.com/ayushmanjha52/CMMS |
| **API docs** | https://plantdesk-production.up.railway.app/swagger-ui.html |

![PlantDesk sign-in screen](frontend/public/og.jpg)

### Try it in one go

Password for every demo account: **`plantdesk-demo`** (the login page also has one-click role buttons).

| Plant code | Email | You are | Try this |
|---|---|---|---|
| `DEMO` | `manager@demo.plant` | Maintenance manager | Board and watch list, the conveyor motor's health strip, assign a job, approve a closure |
| `DEMO` | `electrical@demo.plant` | Technician | Sees only his own jobs: start one, book labour, return it to service |
| `LOCO` | `admin@loco.shed` | Admin of a *different* plant | Sees none of DEMO's data: tenant isolation |
| `DEMO` | `admin@demo.plant` | Plant admin | Register equipment in the asset tree |
| `DEMO` | `viewer@demo.plant` | Viewer | Read-only dashboards |

The demo plants reset every night at 03:00 IST.

**Stack:** Java 21 · Spring Boot 3.3 · Spring Security 6 · Spring Data JPA · PostgreSQL 16 ·
Flyway · Redis · React 18 + Vite + TypeScript + TanStack Query + Tailwind · JUnit 5 + Testcontainers.

---

## Run it

**With Docker** (Postgres, Redis and the app, with the same two demo plants seeded):

```bash
docker compose up --build
# open http://localhost:8080
```

Running the jar directly needs `JWT_SECRET` set (32+ characters). There is deliberately no default.
API docs locally: `http://localhost:8080/swagger-ui.html`.

**Frontend dev server:** `cd frontend && npm install && npm run dev` (proxies `/api` to `localhost:8080`;
set `API_URL` to point elsewhere).

## Tests

```bash
cd backend && ./mvnw test
```

Integration tests run against a real PostgreSQL 16 through Testcontainers, not H2. H2 has no row-level
security, so isolation tests against it would prove nothing. Without Docker, point the tests at any
Postgres 16 whose user can create roles:

```bash
export PLANTDESK_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/plantdesk_test
export PLANTDESK_TEST_DB_USER=postgres PLANTDESK_TEST_DB_PASSWORD=...
./mvnw test
```

70 tests. The ones that matter most:

| Test | Proves |
|---|---|
| `isolation/TenantIsolationIT` | Authenticated as plant A, plant B's work orders, assets and analytics are 404 or absent: by direct id, in lists, in mutations, and with `?tenantId=` / `X-Tenant-Id` sent |
| `isolation/HibernateTenantFilterIT` | The Hibernate filter alone holds with RLS switched **off**, and documents why `findById` is overridden |
| `isolation/RowLevelSecurityIT` | RLS alone holds with raw JDBC: no tenant = zero rows, cross-tenant insert rejected, session variables do not leak across pooled transactions, app role is not superuser |
| `auth/AuthFlowIT` | Token pair on login; refresh rotates; **reusing a rotated token revokes the whole family**; logout and deactivation revoke; 15-minute expiry |
| `workorder/WorkOrderLifecycleTest` | Every action from every status, checked against the transition table |
| `workorder/WorkOrderApiIT` | Invalid transition → 409 with a sentence an engineer can act on; exact BigDecimal costing; atomic stock issue |
| `performance/QueryCountIT` | 50-asset tree = **1** statement; work order page = **≤ 2** (measured 24 without the entity graph) |
| `pm/PmGenerationIT` | With a hand-advanced clock: generated exactly once in the lead window, never duplicated, re-baselined on completion; hours trigger fires first on a hard-running machine; DB rejects a duplicate occurrence |
| `analytics/ReliabilityIT` | MTBF 576.0 h, MTTR 4.8 h, availability 99.17 %, trend DEGRADING: matches the worked example in `ReliabilityCalculatorTest` |
| `security/SecurityHardeningIT` | Every `/api` endpoint declares its roles; login is rate-limited per IP and per account; security headers present; no CORS for foreign origins; error bodies leak nothing |
| `demo/DemoResetIT` | Demo accounts can't be deactivated; the nightly reset rebuilds the demo without touching other plants |

---

## Design decisions

### Tenant isolation: two independent layers

1. **Hibernate filter.** `JwtAuthenticationFilter` (a `OncePerRequestFilter`) resolves the tenant from the
   signed `tid` claim into `TenantContext`. `TenantAwareJpaTransactionManager` enables the filter on the
   Session when each transaction begins.
2. **PostgreSQL row-level security.** The same transaction hook sets `app.tenant_id` with
   `set_config(..., is_local => true)`. Every tenant table has a policy on it.

Details worth knowing:

- **The filter is enabled at transaction start, not in the servlet filter.** With open-in-view off, no
  Session exists yet when the request filter runs. The hook also covers the PM scheduler, which never goes
  through HTTP.
- **`findById` is overridden.** Hibernate filters do not apply to `EntityManager.find`, so Spring Data's
  `findById` would skip layer 1. `TenantAwareJpaRepository` routes by-id lookups through JPQL.
  `HibernateTenantFilterIT` shows the leak with the override removed.
- **The app does not connect as the owner.** Superusers and table owners bypass RLS. Flyway runs as the
  owner; the app connects as `plantdesk_app` (created in V1: no superuser, no BYPASSRLS). The tests connect
  the same way rather than with `@ServiceConnection`, which would hand the app the container's superuser.
- **Local session variables.** `is_local` variables vanish at commit, so a pooled connection cannot carry
  one plant's tenant into the next request.
- **Composite foreign keys** `(tenant_id, x_id)`. FK checks bypass RLS, so without these a raw insert could
  point plant A's work order at plant B's asset.
- **Cross-tenant ids return 404, never 403.** Probing ids reveals nothing.

### Auth

- The tenant comes **only** from the validated JWT. No code reads a tenant from a parameter, header or body.
- **Login takes plant code + email + password.** Login runs before a tenant is known, and RLS blocks the
  user lookup. A narrow `SECURITY DEFINER` function (`auth_lookup_user`) answers that one question. It also
  lets the same email exist at two plants.
- **Tokens.** Access token: HS256, 15 minutes, kept in browser memory only. Refresh token: 256 random bits
  in an `HttpOnly; SameSite=Strict; Path=/api/auth` cookie. The server stores it only as a SHA-256 hash.
- **Rotation with reuse detection.** Every refresh issues a new token in the same family. Presenting a
  rotated token revokes the entire family. The revocation commits *before* the 401 is thrown, so it is not
  rolled back.
- **The frontend refreshes single-flight**, so two parallel 401s (or React StrictMode) cannot trigger reuse
  detection against the user.
- **Unknown email and wrong password take the same time** (dummy BCrypt compare).

### Domain

- **Work order lifecycle** is an explicit state machine (`WorkOrderAction`). Clients send *actions*, never
  statuses. `WorkOrder` has no status setter. Guards: an assignee before start, labour before return to
  service, a failure code on breakdowns. DB `CHECK` constraints back these up.
- **Return to service and closure are separate.** The technician returns the equipment; the engineer
  reviews the job card and closes it.
- **Money is `BigDecimal`, time is integer minutes.** Line costs round once to paise. Part costs are
  snapshotted at issue. Stock is issued with a single conditional `UPDATE`, so two technicians cannot both
  draw the last bearing.
- **PM schedules: calendar or running hours, whichever comes first.**
  - Generated a tenth of the interval early, so shutdowns and spares can be planned.
  - The baseline moves when the PM is actually done.
  - Duplicates are blocked three ways: an outstanding order skips the schedule, `@Version` on the schedule,
    and a unique index on `(pm_schedule_id, pm_sequence)`.
- **Reliability (calendar basis).**
  - MTBF = uptime ÷ failures. MTTR = repair time ÷ restored failures.
  - The trend compares the older half of the gaps between failures with the recent half; recent below 75 %
    of older counts as degrading.
  - Failure code `X01` (external supply interruption) is excluded: a motor that tripped on a grid dip did
    not fail.
- **Asset tags** (`PLT-01/AREA-03/LN-05/MTR-005`) are generated from atomic per-plant counters and stored
  as a materialised path. The whole tree loads in one indexed query.

### Security checklist

| Area | What is in place |
|---|---|
| Secrets | None in the repository. Production secrets live only in the host's environment variables. The JWT key has no fallback: the app refuses to start without one. Git history scanned: no secrets, no `.env` files ever committed. |
| Authentication | BCrypt passwords; 15-minute access tokens kept in memory; rotating refresh tokens in an HttpOnly, Secure, SameSite=Strict cookie, revoked as a family on reuse. |
| Authorisation | `@PreAuthorize` on every endpoint, enforced by a test. Tenant and user identity come only from the signed token, never from request input. |
| Data isolation | Hibernate filter + PostgreSQL row-level security + composite foreign keys. App connects as a non-owner, non-superuser role. Database has no public endpoint. |
| Brute force | Sign-in limited to 20 attempts per IP per 5 minutes and 10 failures per account per 15 minutes (429 + `Retry-After`). |
| Input | Bean Validation on every request body (lengths, ranges, digit counts); JPA parameter binding only, so no SQL injection; React escapes all output. |
| Headers | Strict Content-Security-Policy (`'self'` only, no inline script), HSTS, `X-Frame-Options: DENY`, `nosniff`, Referrer-Policy, Permissions-Policy. No CORS: app and API share an origin. |
| Errors | No stack traces, exception names or SQL in responses; unexpected errors return a reference id and are logged server-side. |
| Dependencies | `npm audit`: 0 vulnerabilities. Fonts self-hosted, so no third-party requests. |
| Privacy | One strictly necessary cookie, no analytics or trackers. [Privacy & cookies](https://plantdesk-production.up.railway.app/privacy) and [Terms](https://plantdesk-production.up.railway.app/terms) pages. |

Not applicable here: payments and refunds, file uploads, outgoing email, Firebase/Supabase.

### Credits

Fonts: [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) and
[Geist / Geist Mono](https://vercel.com/font), all under the SIL Open Font License 1.1, self-hosted
via Fontsource. Logo, icons and illustrations are original to this project.

### Spec deviations (deliberate)

- The Hibernate filter is enabled in the transaction manager rather than inside the `OncePerRequestFilter`
  (reason above). The request filter still resolves the tenant.
- RLS is `ENABLE`d, not `FORCE`d. The owner must bypass it for the `SECURITY DEFINER` lookups; the app
  role can never become the owner.
- `work_orders.raised_by` is nullable: `NULL` means generated by the PM scheduler, rather than a fake
  "system" user showing up in technician lists.

## Deploy

The image is self-contained (`Dockerfile` at the root). Any host that runs a container plus Postgres 16
works. **The Flyway user must be able to `CREATE ROLE`**, because V1 creates the restricted app role.
Railway's Postgres user can; check before using another provider.

On Railway: create a project from this repo (it detects the Dockerfile), add a PostgreSQL service, and set:

| Variable | Value |
|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_OWNER_USER` / `DB_OWNER_PASSWORD` | `${{Postgres.PGUSER}}` / `${{Postgres.PGPASSWORD}}` |
| `APP_DB_PASSWORD` | a new strong password (V1 creates `plantdesk_app` with it) |
| `JWT_SECRET` | 32+ random bytes, e.g. `openssl rand -base64 48` |
| `COOKIE_SECURE` | `true` |
| `DEMO_ENABLED` | `true` for the public demo |

Optional: add Redis and set `CACHE_TYPE=redis`, `REDIS_URL`, `REDIS_HEALTH_ENABLED=true`.
