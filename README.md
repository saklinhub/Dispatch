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

| Variable | Description |
|---|---|
| `JWT_SECRET` | HS256 signing secret (minimum 256 bits) |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `MAIL_FROM` | Sender address |
| `TWILIO_ACCOUNT_SID` | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | Twilio Auth Token |
| `TWILIO_FROM_NUMBER` | Sender phone in E.164 format |
| `TWILIO_MESSAGING_SERVICE_SID` | Twilio Messaging Service SID (optional) |
| `SMS_ENABLED` | Set to `true` to enable real SMS sending |

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/users/{userId}/notifications` | Create a notification |
| `GET` | `/api/v1/notifications/{id}` | Get notification detail |
| `GET` | `/api/v1/notifications/{id}/status` | Get notification status |
| `GET` | `/api/v1/users/{userId}/notifications` | List notifications (paginated) |
| `DELETE` | `/api/v1/notifications/{id}` | Cancel a notification |
| `GET` | `/api/v1/notifications/{id}/attempts` | Get delivery attempt history |

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
