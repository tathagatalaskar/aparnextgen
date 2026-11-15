# APAR / ACR Management System — NIC Tripura

A secure full-stack Annual Performance Assessment Report (APAR / ACR) portal built on the NIC SPARROW-style `tran` schema.

**Stack:** React 18 + Vite + Tailwind · Spring Boot 3 / Java 17 · PostgreSQL 16 · JWT · Docker

## Deployment

This is a full-stack application. **Deploy the React frontend to Vercel, and deploy the Spring Boot backend plus PostgreSQL separately.** Vercel's static frontend hosting does not run this Spring Boot API as a normal Vite site.

### 1. Prepare PostgreSQL

Create a PostgreSQL 16 database and run:

```bash
psql "postgresql://DB_USER:DB_PASSWORD@DB_HOST:5432/DB_NAME?sslmode=require" -f db/init/01_schema.sql
psql "postgresql://DB_USER:DB_PASSWORD@DB_HOST:5432/DB_NAME?sslmode=require" -f db/init/02_reference_data.sql
```

The schema and reference-data scripts must be run using the same PostgreSQL role configured for the backend (or a DBA must grant that role access to schema `tran` and its tables). The reference-data script only inserts organizations, offices, and departments required by the application.

Do **not** run `db/init/02_seed.sql` against a real production database. It inserts publicly documented demo accounts, sample employee records, and test ACR data for local demonstrations only.

After deploying the schema, have an authorized administrator configure the approved assessment cycle and due date. Replace both placeholders with the real reporting period and deadline before running:

```sql
INSERT INTO tran.acr_cycle (assessment_year, due_date, is_open)
VALUES ('YYYY-YYYY', DATE 'YYYY-MM-DD', TRUE)
ON CONFLICT (assessment_year) DO UPDATE
SET due_date = EXCLUDED.due_date,
    is_open = EXCLUDED.is_open;
```

Do not leave placeholder values in the live database. Only one cycle should be open at a time.

### 2. Deploy the backend

Build and deploy the `backend/Dockerfile` with a Java 17 compatible container host. Set these environment variables in that host:

| Variable | Required | Example / meaning |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | Yes | `jdbc:postgresql://HOST:5432/DB?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Yes | Production database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Production database password |
| `APP_JWT_SECRET` | Yes | Unique random secret, at least 32 bytes |
| `APP_CORS_ORIGINS` | Yes | Comma-separated exact frontend origins, e.g. `https://your-app.vercel.app` |
| `PORT` | No | Defaults to `8080` |
| `APP_JWT_EXPIRATION_MS` | No | Defaults to `3600000` (one hour) |

Generate a strong JWT secret with:

```bash
openssl rand -base64 48
```

The backend URL must be publicly reachable over HTTPS. Its API base path is `/api`; for example `https://your-backend.example.com/api`.

### 3. Deploy the frontend to Vercel

1. Import this GitHub repository into Vercel.
2. Set **Root Directory** to the repository root (`.`). The root `vercel.json` installs and builds the Vite app inside `frontend`.
3. The Vercel config sets the install command, build command, output directory (`frontend/dist`), and SPA rewrite automatically. Do not override them unless you intentionally set Root Directory to `frontend`.

Alternatively, you can set **Root Directory** to `frontend`; in that case use `npm run build` as the build command and `dist` as the output directory.
4. Add the environment variable `VITE_API_BASE_URL` with the backend's public URL ending in `/api`.
5. Keep `VITE_SHOW_DEMO_ACCOUNTS` unset or set it to `false`.
6. Deploy, then add the exact Vercel production URL to the backend's `APP_CORS_ORIGINS`.

The Vercel rewrite in `frontend/vercel.json` routes direct visits to React Router pages (for example `/register` and `/acr/123`) back to `index.html`.

After changing Vercel environment variables, redeploy the frontend so Vite can include the new value in its build.

### 4. Local development with Docker

```bash
docker compose up --build
```

Open `http://localhost:5173`. The local compose setup loads both SQL files, including demo accounts. The seeded demo password is `Password@123`; never use those accounts or the local fallback secrets in a production deployment.

Stop with `Ctrl+C`. Remove local database data with `docker compose down -v`.

### 5. Local development without Docker

```bash
createdb apar_db
psql -d apar_db -f db/init/01_schema.sql
psql -d apar_db -f db/init/02_seed.sql
psql -d apar_db -f db/init/02_reference_data.sql
cd backend && APP_JWT_SECRET=local_dev_only_change_to_a_random_secret_of_at_least_32_bytes mvn spring-boot:run
# In a second terminal:
cd frontend && npm ci && npm run dev
```

For a local frontend build, set `VITE_API_BASE_URL=http://localhost:8080/api`. The development Vite server defaults to this URL. For the manual backend command above, provide a temporary local-only JWT secret; production deployments must use a separately generated secret.

## Roles and workflow

| Role | Capabilities |
|---|---|
| Employee | Submit a registration request, sign in after authority activation, submit APAR when unlocked, view own reports, edit contact details, receive notifications |
| Authority | Review submitted reports, grade/remark/flag, manage employees, send notifications and APAR reminders |

Self-registration creates an inactive employee and login account pending authority approval. The authority reviews the directory entry and activates it before the employee can sign in. Deactivation also disables the login immediately, including sessions with previously issued JWTs.

The APAR form is locked until an authority sends a reminder. Employees can only access their own reports and notifications; these ownership checks are enforced by the backend.

## Security notes

- JWT stateless authentication and BCrypt password hashes.
- Role checks enforced on server endpoints and service operations.
- CORS uses a comma-separated list of exact frontend origins; whitespace around entries is trimmed.
- Production startup requires `APP_JWT_SECRET`; do not rely on local development defaults.
- Demo credentials are hidden in production unless `VITE_SHOW_DEMO_ACCOUNTS=true` is explicitly set.
- Contact email in the employee profile is HR contact information; it does not change the account's login email.

## Continuous integration

GitHub Actions builds the Vite frontend and runs Maven verification for the backend on pushes and pull requests.

## API base paths

The backend uses the `/api` servlet context path. Main routes include:

- `POST /api/auth/login`, `POST /api/auth/register`
- `GET /api/v1/meta/departments`, `GET /api/v1/meta/offices`
- `GET/POST/PUT/DELETE /api/v1/employees` (authority)
- `GET /api/v1/acr`, `GET /api/v1/acr/{statusId}`, `POST /api/v1/acr/submit`
- `GET /api/v1/notifications`, `POST /api/v1/notifications/send`
- `GET/PUT /api/v1/me`

See the controllers in `backend/src/main/java/gov/nic/apar/controller` for the complete route contract.
