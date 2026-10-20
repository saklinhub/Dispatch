# Dispatch

**Dispatch** is a backend notification platform built with Spring Boot that reliably processes and delivers email, SMS, and in-app notifications asynchronously. It uses RabbitMQ for message queuing and PostgreSQL for persistent storage.

## Features

- **Multi-channel delivery** - Email (SMTP), SMS (Twilio), and In-App notifications
- **Asynchronous processing** - Notifications are queued via RabbitMQ; the API responds instantly without waiting for delivery
- **JWT authentication** - Stateless, user-specific access to notification history and preferences
- **Idempotent requests** - Duplicate API calls with the same idempotency key are safely deduplicated
- **Automatic retries** - Exponential backoff with configurable max attempts
- **Dead-letter queue** - Exhausted jobs are routed to a DLQ for inspection and manual replay
- **Transactional outbox** - Notification records and outgoing events are committed atomically, eliminating database/broker inconsistency
- **Delivery tracking** - Every delivery attempt is recorded with provider response and error codes
- **Stuck job recovery** - Processing jobs idle beyond a configurable threshold are automatically reset
- **In-app inbox** - Read/unread status, inbox fetch, and manual retry controls

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Message Broker | RabbitMQ 3.13 |
| SMS | Twilio |
| Authentication | JWT (JJWT) |
| Testing | JUnit 5, Mockito, Testcontainers |
| DevOps | Docker, Docker Compose, Maven |
| API Docs | SpringDoc / Swagger UI |

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- Docker and Docker Compose

### Run locally

```bash
# Start PostgreSQL and RabbitMQ
docker-compose up -d

# Run the application
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.
Swagger UI is available at `http://localhost:8080/swagger-ui.html`.
RabbitMQ management UI is available at `http://localhost:15672` (guest / guest).

## Environment Variables

Copy `.env.example` to `.env` to customize settings. In production, run with `SPRING_PROFILES_ACTIVE=prod` to enforce strict secret injection without development fallbacks.

| Variable | Description | Default (Local / Dev) | Required in Prod? |
|---|---|---|:---:|
| `PORT` | Application HTTP port | `8080` | No |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`dev`, `prod`) | `dev` | Recommended |
| `DB_HOST` | PostgreSQL hostname | `localhost` | Yes |
| `DB_PORT` | PostgreSQL port | `5433` (Docker host) | Yes |
| `DB_NAME` | Database name | `dispatch_db` | Yes |
| `DB_USERNAME` | Database user | `dispatch` | Yes |
| `DB_PASSWORD` | Database password | `dispatch` | Yes |
| `SPRING_DATASOURCE_URL` | Optional full JDBC URL override | (constructed from DB_* vars) | No |
| `RABBITMQ_HOST` | RabbitMQ broker host | `localhost` | Yes |
| `RABBITMQ_PORT` | RabbitMQ broker port | `5672` | Yes |
| `RABBITMQ_USERNAME` | RabbitMQ username | `guest` | Yes |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` | Yes |
| `JWT_SECRET` | HS256 secret (minimum 256 bits) | Dev default (blocked in prod) | **Yes** |
| `JWT_EXPIRATION_MS` | Access token lifetime (ms) | `86400000` (24h) | No |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token lifetime (ms) | `604800000` (7d) | No |
| `MAIL_HOST` | SMTP server host | `smtp.gmail.com` | Yes |
| `MAIL_PORT` | SMTP server port | `587` | No |
| `MAIL_USERNAME` | SMTP username | — | Yes |
| `MAIL_PASSWORD` | SMTP password / app password | — | Yes |
| `MAIL_FROM` | Outgoing email address | `noreply@dispatch.io` | Yes |
| `MAIL_FROM_NAME` | Outgoing email display name | `"Dispatch Notifications"` | No |
| `TWILIO_ACCOUNT_SID` | Twilio Account SID | — | Optional |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token | — | Optional |
| `TWILIO_FROM_NUMBER` | Twilio sender phone (E.164) | — | Optional |
| `TWILIO_MESSAGING_SERVICE_SID` | Twilio Messaging Service SID | — | Optional |
| `SMS_ENABLED` | Toggle real SMS delivery | `false` | No |

## API Overview

### Authentication (JWT)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new user and receive JWT tokens |
| `POST` | `/api/v1/auth/login` | Authenticate with email/password to receive JWT tokens |
| `POST` | `/api/v1/auth/refresh` | Refresh access token using a valid refresh token |
| `GET` | `/api/v1/auth/me` | Get profile for currently authenticated user |

### Notifications

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/users/{userId}/notifications` | Create a notification (idempotent) |
| `GET` | `/api/v1/notifications/{id}` | Get notification detail |
| `GET` | `/api/v1/notifications/{id}/status` | Get notification status |
| `GET` | `/api/v1/users/{userId}/notifications` | List notifications (paginated) |
| `DELETE` | `/api/v1/notifications/{id}` | Cancel a notification |
| `GET` | `/api/v1/notifications/{id}/attempts` | Get delivery attempt history |

### Transactional Outbox

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/outbox/events` | List transactional outbox events (paginated, filter by status) |
| `GET` | `/api/v1/outbox/stats` | Get metrics on pending, published, and failed outbox events |
| `POST` | `/api/v1/outbox/events/{id}/retry` | Manually re-trigger publishing of an outbox event to RabbitMQ |

## Project Roadmap

| Part | Module / Topic | Scope & Key Components | Status |
|---|---|---|---|
| **01** | **Core Foundation & Persistence Layer** | Java 21/Spring Boot 3.3 setup, Docker infrastructure, Flyway migrations (V1–V6), JPA Entities, Spring Data JPA Repositories, JPA Auditing, and integration tests | ✅ Completed |
| **02** | **Authentication & Authorization** | Stateless Spring Security configuration, JWT token provider (HS256), login/register/refresh endpoints, UserDetails service, password hashing, and role checks | ✅ Completed |
| **03** | **Notification REST APIs & Validation** | Notification ingestion endpoint (`POST /api/v1/notifications`), status inspection APIs, request validation, DTOs, and global exception handling | ✅ Completed |
| **04** | **Transactional Outbox & Broker Topology** | RabbitMQ exchange/queue/DLQ bindings, transactional outbox event publisher, scheduled outbox poller, and delivery reliability | ✅ Completed |
| **05** | **Asynchronous Consumers & Multi-Channel Delivery** | RabbitMQ message consumers, Strategy pattern for delivery providers (JavaMailSender for Email, Twilio SDK for SMS, Inbox persistence for In-App) | ✅ Completed |
| **06** | **Resilience, Retries & Dead Letter Queue (DLQ)** | Error classification (transient vs permanent), exponential backoff retries, delivery attempt audit logs, DLQ routing, and stuck-job recovery | ⏳ Pending |
| **07** | **Idempotency & Duplicate Prevention** | Distributed/database idempotency key validation, concurrent request deduplication, and cached idempotent response delivery | ⏳ Pending |
| **08** | **User Preferences & Quiet Hours Evaluation** | Channel opt-in/opt-out filters, address overrides, timezone-aware quiet hours evaluation, and deferred notification scheduling | ⏳ Pending |
| **09** | **In-App Inbox & Delivery Audit History** | User inbox APIs (`GET /api/v1/inbox`, read/unread toggles, unread counter badge), delivery attempt tracking history endpoints | ⏳ Pending |
| **10** | **End-to-End Testing, Observability & Containerization** | Testcontainers integration suite (PostgreSQL + RabbitMQ), Actuator metrics, production Dockerfile, and container packaging | ⏳ Pending |

## Database Schema

| Table | Purpose |
|---|---|
| `users` | Registered users |
| `notifications` | All notification requests |
| `delivery_attempts` | Append-only delivery audit log |
| `notification_preferences` | Per-user channel opt-ins and quiet hours |
| `inbox_messages` | Persisted in-app messages |
| `outbox_events` | Transactional outbox events |

## License

MIT
