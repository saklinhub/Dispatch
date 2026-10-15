# Dispatch 🚀

**Reliable Asynchronous Notification Service** — sends email, SMS, and in-app notifications through a durable, queue-backed pipeline.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Broker | RabbitMQ 3.13 |
| SMS | Twilio |
| Auth | JWT (JJWT 0.12) |
| Testing | JUnit 5, Mockito, Testcontainers |
| DevOps | Docker, Docker Compose, Maven |

---

## Quick Start

### 1 — Start Infrastructure

```bash
docker-compose up -d
```

- PostgreSQL → `localhost:5433` (mapped to internal `5432`)
- RabbitMQ → `localhost:5672` | Management UI → http://localhost:15672 (`guest/guest`)

### 2 — Run the Application

```bash
mvn spring-boot:run
```

Flyway applies all 6 migrations automatically on startup.

### 3 — Verify

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

---

## Database Schema

| Table | Purpose |
|---|---|
| `users` | Registered users with email and E.164 phone number |
| `notifications` | All notification requests (EMAIL, SMS, IN_APP) |
| `delivery_attempts` | Append-only audit log of every delivery attempt |
| `notification_preferences` | Per-user channel opt-ins, preferred addresses, and quiet hours |
| `inbox_messages` | Persisted in-app messages |
| `outbox_events` | Transactional outbox for reliable broker publishing |

---

## Project Roadmap

| Part | Module / Topic | Scope & Key Components | Status |
|---|---|---|---|
| **01** | **Core Foundation & Persistence Layer** | Java 21/Spring Boot 3.3 setup, Docker infrastructure, Flyway migrations (V1–V6), JPA Entities, Spring Data JPA Repositories, JPA Auditing, and integration tests | ✅ Completed |
| **02** | **Authentication & Authorization** | Stateless Spring Security configuration, JWT token provider (HS256), login/register endpoints, UserDetails service, password hashing, and role checks | ⏳ Pending |
| **03** | **Notification REST APIs & Validation** | Notification ingestion endpoint (`POST /api/v1/notifications`), status inspection APIs, request validation, DTOs, and global exception handling | ⏳ Pending |
| **04** | **Transactional Outbox & Broker Topology** | RabbitMQ exchange/queue/DLQ bindings, transactional outbox event publisher, scheduled outbox poller, and delivery reliability | ⏳ Pending |
| **05** | **Asynchronous Consumers & Multi-Channel Delivery** | RabbitMQ message consumers, Strategy pattern for delivery providers (JavaMailSender for Email, Twilio SDK for SMS, Inbox persistence for In-App) | ⏳ Pending |
| **06** | **Resilience, Retries & Dead Letter Queue (DLQ)** | Error classification (transient vs permanent), exponential backoff retries, delivery attempt audit logs, DLQ routing, and stuck-job recovery | ⏳ Pending |
| **07** | **Idempotency & Duplicate Prevention** | Distributed/database idempotency key validation, concurrent request deduplication, and cached idempotent response delivery | ⏳ Pending |
| **08** | **User Preferences & Quiet Hours Evaluation** | Channel opt-in/opt-out filters, address overrides, timezone-aware quiet hours evaluation, and deferred notification scheduling | ⏳ Pending |
| **09** | **In-App Inbox & Delivery Audit History** | User inbox APIs (`GET /api/v1/inbox`, read/unread toggles, unread counter badge), delivery attempt tracking history endpoints | ⏳ Pending |
| **10** | **End-to-End Testing, Observability & Containerization** | Testcontainers integration suite (PostgreSQL + RabbitMQ), Actuator metrics, production Dockerfile, and container packaging | ⏳ Pending |

