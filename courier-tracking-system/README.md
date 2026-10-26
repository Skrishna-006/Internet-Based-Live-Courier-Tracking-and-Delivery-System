# Internet-Based Live Courier Tracking and Delivery System

A modern full-stack logistics and parcel dispatch platform built with **React JS**, **Spring Boot**, and **PostgreSQL**.

---

## Project Structure

```
courier-tracking-system/
├── backend/                  # Spring Boot 3.4.x / Java 21 REST API & WebSocket service
│   ├── src/
│   │   ├── main/java/com/courier/tracking/
│   │   │   ├── config/       # Security, CORS, and WebSocket configuration
│   │   │   ├── controller/   # REST Controllers (API presentation layer)
│   │   │   ├── service/      # Business logic and domain service layer
│   │   │   ├── repository/   # Spring Data JPA repositories
│   │   │   ├── entity/       # JPA domain entities
│   │   │   ├── dto/          # Data Transfer Objects & API envelopes
│   │   │   └── exception/    # Global exception handling & error advice
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/ # Flyway SQL migrations
│   └── pom.xml
├── frontend/                 # React 19 + Vite + Tailwind CSS Single-Page Application
│   ├── src/
│   │   ├── components/       # Reusable UI components
│   │   ├── pages/            # View pages (Tracking, Dashboard, Booking, Dispatch)
│   │   ├── services/         # Axios API client & WebSocket adapters
│   │   ├── context/          # Global state management
│   │   ├── routes/           # React Router route definitions
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── index.html
│   ├── package.json
│   ├── tailwind.config.js
│   └── vite.config.js
└── docs/                     # Architecture & technical design documentation
    └── architecture.md
```

---

## Prerequisites

Ensure the following runtimes and tools are installed:

| Tool | Recommended Version | Verification Command |
| :--- | :--- | :--- |
| **Java** | 21 LTS (or Java 25 running 21 compatibility) | `java -version` |
| **Maven** | 3.8+ / 3.9+ | `mvn -v` |
| **Node.js** | 20+ / 24+ | `node -v` |
| **npm** | 10+ / 11+ | `npm -v` |
| **PostgreSQL** | 16+ / 18+ | `psql --version` |

---

## Configuration & Environment Variables

The backend application is configured via environment variables with developer-friendly defaults for local execution:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Spring Boot HTTP listening port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/courier_db` | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `CORS_ALLOWED_ORIGINS`| `http://localhost:5173` | Allowed frontend origins for CORS |

---

## Local Development Quickstart

### 1. Database Setup
Ensure PostgreSQL service is running and create the database if not present:
```sql
CREATE DATABASE courier_db;
```

### 2. Run the Backend
```bash
cd backend
mvn spring-boot:run
```
The backend REST API will be accessible at: `http://localhost:8080`
Health check endpoint: `http://localhost:8080/api/v1/health`

### 3. Run the Frontend
In a separate terminal window:
```bash
cd frontend
npm install
npm run dev
```
The frontend SPA will be accessible at: `http://localhost:5173`

---

## Testing & Quality Assurance

### Run Backend Tests:
```bash
cd backend
mvn clean test
```

### Run Frontend Build Check:
```bash
cd frontend
npm run build
```

---

## Project Roadmap

- [x] **Phase 1 — Architecture and Project Foundation** *(Current)*
  - System architecture document & state machine definition.
  - Layered Spring Boot 3.4.x skeleton with Java 21 compatibility.
  - React + Vite + Tailwind CSS frontend setup.
  - Flyway migration baseline.
- [ ] **Phase 2 — Backend Database Foundation & Entity Models**
  - PostgreSQL schema, Flyway migrations, and JPA entities.
  - Audit logging and indexing on tracking numbers.
- [ ] **Phase 3 — Authentication & Authorization**
  - Spring Security with JWT, role-based access for Customer, Agent, Admin.
- [ ] **Phase 4 — Shipment Booking & Tracking Engine**
  - Booking API, unique tracking number generation, consignment status workflows.
- [ ] **Phase 5 — Live Telemetry & GPS Tracking**
  - WebSocket/STOMP streaming, agent location ingestion, and map visualization with Leaflet/OpenStreetMap.
- [ ] **Phase 6 — Frontend UI & Driver Portal**
  - Responsive dashboards, agent delivery mode, proof of delivery.
