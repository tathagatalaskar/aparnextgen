# APAR / ACR Management System — NIC Tripura

A full-stack Annual Performance Assessment Report (APAR / ACR) portal. Employees maintain self-appraisal targets and achievements, while reporting authorities review submissions, assign grades, add remarks, flag records, manage employees, and send notifications.

**Stack:** React 18 · Vite · Tailwind CSS · Spring Boot 3 · Java 17 · PostgreSQL 16 · Spring Security · JWT · Docker

## Features

### Employee
- Register and sign in with an employee account.
- View their own ACR submissions and assessment status.
- Submit targets and achievements after an authority reminder unlocks the current cycle.
- View and update contact details.
- Receive APAR reminders, announcements, and notifications.

### Reporting authority
- View submitted ACRs across employees.
- Review ACRs, assign grades, add remarks, and flag records with a reason.
- Add, edit, activate/deactivate, and remove employee records.
- Send notifications to an employee, selected employees, a department, or all active employees.
- Send APAR reminders and inspect pending submissions.

### Security
- Stateless JWT authentication and BCrypt password hashing.
- Employee-only access to their own ACR and notification records.
- Server-side authority checks for administrative actions.
- Validation on incoming request DTOs.
- Production JWT secrets are required; no default signing secret is used.

## Repository layout

```text
.
├── backend/                 # Spring Boot REST API
│   ├── Dockerfile           # Local Docker image
│   ├── Dockerfile.vercel    # Vercel Container service
│   └── src/
├── frontend/                # React + Vite SPA
│   ├── src/
│   └── vercel.json          # Client-side route fallback
├── db/init/
│   ├── 01_schema.sql        # PostgreSQL schema (tran)
│   ├── 02_seed.sql          # Local/demo data only
│   └── 03_reference_data.sql# Production lookup data
├── .github/workflows/ci.yml # Frontend + backend build checks
├── docker-compose.yml
└── vercel.json              # Frontend/backend routing + daily cron
```

## Deploy the complete application on Vercel

This repository is configured for one Vercel deployment using two Vercel Services: the Vite static frontend and the Spring Boot Docker container backend. Both are exposed through one domain: the browser uses `/api/*` for the API, and all other paths are routed to the React app.

### 1. Create the PostgreSQL database

Create a managed PostgreSQL database that is reachable from Vercel. Keep its host, database name, username, and password handy.

Run these SQL files in order using the provider's SQL editor or `psql`:

1. `db/init/01_schema.sql`
2. `db/init/03_reference_data.sql`

Do **not** run `02_seed.sql` against a production database. It creates demonstration accounts with a publicly documented sample password. The production reference-data script creates the organization, office, and departments needed by employee registration without inserting demo users.

### 2. Import the GitHub repository into Vercel

Import `tathagatalaskar/aparnextgen` from the Vercel dashboard.

In **Project Settings → Build and Deployment**, set the Framework Preset to **Services**. The repository's root `vercel.json` declares:
- `frontend`: Vite application rooted at `frontend/`.
- `backend`: Container runtime rooted at `backend/`, built from `Dockerfile.vercel`.
- `/api/*` routed to Spring Boot and other requests routed to the frontend.

Vercel Services and the Container runtime are currently in beta. If the Services framework preset or container deployment is not enabled for your account, deploy the frontend and backend as separate projects or use a Java container host for the backend.

### 3. Add backend environment variables

Add the following variables to the **backend service's** production environment in Vercel. Use the database provider's actual connection information.

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require` (use the provider's SSL settings) |
| `SPRING_DATASOURCE_USERNAME` | Managed PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Managed PostgreSQL password |
| `APP_JWT_SECRET` | A unique random secret, at least 32 bytes |
| `CRON_SECRET` | A separate long random secret for the scheduled reminder endpoint |
| `APP_SCHEDULER_ENABLED` | `false` |

Generate secrets locally with `openssl rand -base64 48`. Do not commit the generated values or share them in screenshots. `APP_CORS_ORIGINS` can be set to a comma-separated allowlist when you intentionally call the API from a different origin; same-domain Vercel routing does not require a cross-origin browser request.

### 4. Provision the first reporting authority

After the first deployment is ready and the database is connected:

1. Open your deployed application's `/register` page and create the first account using the real authority's details.
2. Using an administrator connection to the database, promote that account:

   ```sql
   UPDATE tran.user_account
   SET role = 'AUTHORITY'
   WHERE email = LOWER('authority@example.com');
   ```

3. Sign out and sign back in so the newly issued JWT contains the authority role.

The registration endpoint creates an `EMPLOYEE` account by design. Only someone with database administration access should perform the promotion. Authorities can then add other employees and manage the assessment cycle. Configure an open row in `tran.acr_cycle` for the assessment year and due date before enabling submissions.

### 5. Daily APAR reminders

The root `vercel.json` schedules `/api/v1/cron/reminders` daily at 03:30 UTC (09:00 India Standard Time). Vercel sends `Authorization: Bearer <CRON_SECRET>`; the backend rejects requests if the secret is missing or does not match.

Vercel Cron runs on production deployments. The backend in-process scheduler is disabled with `APP_SCHEDULER_ENABLED=false` because a scale-to-zero container should not be relied on to run continuously.

### 6. Verify after deployment

- Open the Vercel production URL and confirm the login page loads.
- Refresh a nested route such as `/register` or `/profile`; the SPA fallback should return the app.
- Register an employee and confirm the department list populates.
- Sign in with the provisioned authority account; verify directory, notification, and review actions.
- Send a reminder to a test employee; confirm the employee can then submit an ACR.
- Review Vercel deployment/build logs and the backend service logs if any request fails.

## Run locally with Docker

1. Copy `.env.example` to `.env`.
2. Replace the sample values with unique local secrets (at least 32 bytes for `JWT_SECRET`).
3. Start the application:

```bash
docker compose up --build
```

Open http://localhost:5173. PostgreSQL initializes the schema, demo data, and reference data on the first database volume creation. The local demo accounts in `db/init/02_seed.sql` all use `Password@123`; use them only in a local/demo environment.

Stop with `Ctrl+C`. To remove the local database volume and re-run initialization, use:

```bash
docker compose down -v
```

## Run locally without Docker

Requirements: Java 17, Maven, Node.js 20+, and PostgreSQL 16.

1. Create a PostgreSQL database and execute `01_schema.sql`, `02_seed.sql`, and `03_reference_data.sql` in order for a demo environment.
2. Set the backend environment:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/apar_db
export SPRING_DATASOURCE_USERNAME=apar_admin
export SPRING_DATASOURCE_PASSWORD=your-local-db-password
export APP_JWT_SECRET="$(openssl rand -base64 48)"
```

3. Start Spring Boot:

```bash
cd backend
mvn spring-boot:run
```

4. In a second terminal, start Vite:

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` requests to `http://localhost:8080`. The frontend's default API base is `/api`, so the same client code works locally and behind Vercel's same-domain rewrite.

## API overview

All routes below are prefixed by `/api`.

| Method | Path | Access |
|---|---|---|
| POST | `/auth/login` | Public |
| POST | `/auth/register` | Public (creates EMPLOYEE role) |
| GET | `/v1/meta/departments` | Public reference data |
| GET | `/v1/meta/offices` | Public reference data |
| GET / PUT | `/v1/me` | Authenticated user's profile |
| GET | `/v1/acr` | Authenticated; own reports or all reports for authority |
| GET | `/v1/acr/eligibility` | Employee |
| GET | `/v1/acr/{statusId}` | Owner or authority |
| POST | `/v1/acr/submit` | Employee, after reminder |
| PUT | `/v1/acr/{statusId}/review` | Authority |
| GET / POST / PUT / PATCH / DELETE | `/v1/employees...` | Authority |
| GET / PATCH | `/v1/notifications...` | Authenticated user's own notifications |
| POST | `/v1/notifications/send` | Authority |
| POST | `/v1/notifications/reminders` | Authority |
| POST | `/v1/notifications/reminders/auto` | Authority |
| GET | `/v1/notifications/pending` | Authority |
| GET | `/v1/cron/reminders` | Vercel Cron secret only |

## Continuous integration

GitHub Actions runs a clean frontend dependency install and Vite production build, plus Maven verification on Java 17, for pushes and pull requests. A green CI build verifies compilation and bundling; it does not replace testing with a live PostgreSQL instance, real user credentials, or a deployed Vercel preview.
