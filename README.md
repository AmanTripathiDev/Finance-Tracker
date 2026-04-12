# Personal Finance Tracker

Production-ready full-stack personal finance management platform with a Spring Boot 3 backend and React 18 frontend.

## Stack

- Backend: Java 21, Spring Boot 3, Spring Security, JWT, Spring Data JPA, PostgreSQL, Maven
- Frontend: React 18, Vite, React Router, TanStack Query, Axios, Zustand, React Hook Form, Zod, Recharts, Tailwind CSS
- Docker: PostgreSQL, backend, frontend via `docker-compose`

## Project Structure

```text
finance-tracker/
  backend/
  frontend/
  README.md
  docker-compose.yml
  schema.sql
```

## Backend Highlights

- JWT access and refresh token flow
- User-scoped repository access and service methods
- Accounts, transactions, categories, budgets, goals, recurring transactions, and reports
- Transfer handling with paired ledger entries
- Scheduler for recurring transactions
- Global exception handling and request validation
- Pagination and filtering for transactions

## Frontend Highlights

- Protected routing with persisted auth state
- Dashboard widgets for balances, budgets, goals, recurring items, and charts
- CRUD flows for transactions, accounts, budgets, goals, and recurring rules
- Reporting charts for category spend, income vs expense, and account balance trends
- Responsive Tailwind UI

## Local Setup

### 1. Start PostgreSQL

Use an existing PostgreSQL instance or Docker:

```bash
docker compose up postgres -d
```

### 2. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Default environment values:

- `DB_URL=jdbc:postgresql://localhost:5432/finance_tracker`
- `DB_USERNAME=postgres`
- `DB_PASSWORD=postgres`
- `JWT_ACCESS_SECRET=<base64-secret>`
- `JWT_REFRESH_SECRET=<base64-secret>`

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend default URL: `http://localhost:5173`

Backend default URL: `http://localhost:8080`

## Docker Setup

Start everything:

```bash
docker compose up --build
```

Services:

- Frontend: `http://localhost:4173`
- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

## API Routes

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`

### Transactions

- `GET /api/transactions`
- `POST /api/transactions`
- `GET /api/transactions/{id}`
- `PUT /api/transactions/{id}`
- `DELETE /api/transactions/{id}`

### Accounts

- `GET /api/accounts`
- `POST /api/accounts`
- `PUT /api/accounts/{id}`
- `POST /api/accounts/transfer`

### Categories

- `GET /api/categories`
- `POST /api/categories`
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}`

### Budgets

- `GET /api/budgets`
- `POST /api/budgets`
- `PUT /api/budgets/{id}`
- `DELETE /api/budgets/{id}`

### Goals

- `GET /api/goals`
- `POST /api/goals`
- `PUT /api/goals/{id}`
- `POST /api/goals/{id}/contribute`
- `POST /api/goals/{id}/withdraw`

### Reports

- `GET /api/reports/category-spend`
- `GET /api/reports/income-vs-expense`
- `GET /api/reports/account-balance-trend`

### Recurring

- `GET /api/recurring`
- `POST /api/recurring`
- `PUT /api/recurring/{id}`
- `DELETE /api/recurring/{id}`

## Verification Performed

- Backend: `mvn -q -DskipTests compile`
- Frontend: `npm run build`

## Notes

- The frontend build currently emits a Vite chunk-size warning because the reporting bundle includes Recharts. The app still builds successfully.
- PostgreSQL schema is included in `schema.sql`. Spring Boot is configured with `ddl-auto: update`, so the app can also manage schema evolution during development.
