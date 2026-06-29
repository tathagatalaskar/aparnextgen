# APAR / ACR Management System — NIC Tripura

A secure full-stack Annual Performance Assessment Report (APAR / ACR) system built on the
**NIC SPARROW table design** (schema `tran`). Employees fill their self-appraisal
(targets & achievements); a single **Reporting Authority** reviews every ACR, grades it,
adds remarks, and can flag it. The authority also has a full **employee directory**.

**Stack:** React (Vite) + Tailwind · Spring Boot 3 (Java 17) · PostgreSQL 16 (schema `tran`) · JWT · Docker

---

## Database design (schema `tran`)

Built directly on the faculty-provided NIC table design:

| Table | Purpose |
|-------|---------|
| `tran.employee_details` | Master roll of officers — ID, name parts, gender (M/F), DOB, mobile |
| `tran.employee_acr_status` | One row per employee per year — submission status + grade/remarks/flag |
| `tran.employee_acr_details` | Target / achievement rows of each ACR |
| `tran.user_account` | Login credentials (email + BCrypt password + role), linked to an employee |
| `tran.organization` / `tran.office` / `tran.department` | Lookup/master data for the IDs |

---

## Two roles

| Role | Can do |
|------|--------|
| **Employee** | **Self-register**, log in, submit ACR (targets/achievements), view own ACRs + grades |
| **Authority** | View all submitted ACRs, grade + remark + flag, **add / edit / activate-deactivate employees**, browse directory |

New employees can **register themselves**; the Authority can **add / edit / delete /
activate / deactivate** employees. Every flag, activation, deactivation and assessment
**notifies the employee**.

### APAR workflow (gated)
The APAR form is **locked by default**. The Authority selects employees and **sends an APAR
reminder**, which **unlocks the form for that employee in near-real-time** (the portal polls
every ~10s). Automatic reminders are also generated daily for anyone who hasn't submitted by
the due window. Each employee only ever sees **their own** notifications and reminders.

### Notification centre
Every user has a **bell with an unread badge** and a dropdown notification centre. The
Authority can send **manual notifications** to one / many / a department / or all employees.

Employees only see their own ACRs; cross-access is blocked server-side (403).

---

## Demo accounts (password: `Password@123`)

| Role | Email | Employee |
|------|-------|----------|
| Authority | authority@nic.in | Smt Anita Sharma (ID 81848) |
| Employee | employee@nic.in | Shri Tathagata Laskar (ID 81851) |
| Employee | priya@nic.in | Smt Priya Menon (ID 81853) — has a sample ACR ready |

---

## Run it (Docker)

```bash
docker compose up --build
```
Open **http://localhost:5173**. Schema + demo data load automatically.
Stop: `Ctrl+C`. Wipe DB: `docker compose down -v`.

---

## Run it (manual)

```bash
createdb apar_db
psql -d apar_db -f db/init/01_schema.sql
psql -d apar_db -f db/init/02_seed.sql
cd backend && mvn spring-boot:run     # needs Java 17 + Maven
cd frontend && npm install && npm run dev   # needs Node 20
```

---

## API endpoints

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/auth/login` | public | Get JWT |
| POST | `/api/auth/register` | public | Self-register a new employee |
| GET | `/api/v1/meta/departments` | public | Department list (for the register form) |
| GET | `/api/v1/employees` | Authority | List/manage employees |
| POST | `/api/v1/employees` | Authority | Add an employee |
| PUT | `/api/v1/employees/{id}` | Authority | Edit an employee |
| PATCH | `/api/v1/employees/{id}/active?active=` | Authority | Activate / deactivate |
| DELETE | `/api/v1/employees/{id}` | Authority | Delete an employee |
| GET | `/api/v1/acr/eligibility` | Employee | Is the APAR form unlocked? |
| GET | `/api/v1/notifications` | any | Own notification centre |
| GET | `/api/v1/notifications/unread-count` | any | Unread badge count |
| PATCH | `/api/v1/notifications/{id}/read` | any | Mark read |
| POST | `/api/v1/notifications/send` | Authority | Manual notification (one/many/dept/all) |
| POST | `/api/v1/notifications/reminders` | Authority | Send APAR reminders (unlocks forms) |
| GET | `/api/v1/notifications/pending` | Authority | Who hasn't submitted |
| GET / PUT | `/api/v1/me` | Employee | View / edit own profile |
| GET | `/api/v1/acr` | any | List ACRs (own / all) |
| GET | `/api/v1/acr/{statusId}` | any | One ACR detail |
| POST | `/api/v1/acr/submit` | Employee | Submit ACR |
| PUT | `/api/v1/acr/{statusId}/review` | Authority | Grade / remark / flag |
| GET | `/api/v1/acr/employees` | Authority | Employee directory |

---

## Security
JWT stateless auth · BCrypt passwords · CORS whitelist · server-side `@Valid` validation ·
SQL-injection-safe (JPA parameter binding) · role checks in the service layer ·
security headers (X-Frame-Options, nosniff). Login is separated from HR master data
(`user_account` vs `employee_details`) — government best practice.
