# Concurrent Ticket Booking System

A production-grade, Ticket Booking and Flash Sale System built in Java 21 and Spring Boot 3.x. This project explicitly demonstrates how a high-concurrency backend safely handles thousands of concurrent users competing for limited resources without double bookings, overselling, or deadlocks.

---

## 1. Project Overview

In high-concurrency scenarios like flash sales, concert ticket drops, or train booking platforms (e.g., IRCTC, BookMyShow), thousands of requests hit the system simultaneously for a small number of available seats.

This system guarantees:
- Zero double booking (no two users reserve the exact same seat).
- Zero overselling (total confirmed bookings never exceed total seats).
- Exact inventory consistency.
- Safe state transitions and rollback on payment failure.
- Idempotent request handling.
- Deadlock prevention when booking multiple seats.

---

## 2. System Architecture

```text
Client (HTTP Requests)
       |
       v
Spring Boot REST API Controllers
       |
       v
Service Layer & Idempotency Check
       |
       v
Lock Strategy Layer (Pluggable)
 -------------------------------------------------------------
 | InMemory (ReentrantLock)  | Pessimistic (@Lock FOR UPDATE)|
 | Optimistic (@Version CAS) | Redis Distributed (Redisson)  |
 -------------------------------------------------------------
       |                                   |
       v                                   v
PostgreSQL Database                  Redis Cache & Lock Store
(User, Event, Seat, Booking)         (Distributed Locks & DECR)
```

---

## 3. Tech Stack

- Backend: Java 21, Spring Boot 3.3, Spring Data JPA, Spring Validation, Spring Actuator
- Database: PostgreSQL 16 with Flyway Migrations
- Distributed Cache & Lock: Redis 7.2, Redisson 3.32
- Metrics & Observability: Micrometer, Prometheus
- API Documentation: OpenAPI 3 / Swagger UI
- Containerization: Docker, Docker Compose
- Testing: JUnit 5, Mockito, Testcontainers

---

## 4. Key Concurrency & Locking Strategies

The system implements four distinct locking mechanisms to demonstrate their trade-offs and use cases:

### Strategy 1: Java ReentrantLock (In-Memory)
- Uses `ReentrantLock` stored inside a `ConcurrentHashMap`.
- Scope: Single JVM process.
- Pros: Sub-microsecond lock overhead, zero network latency.
- Cons: Cannot coordinate locks across multiple application nodes.

### Strategy 2: Database Pessimistic Locking
- Uses JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` generating SQL `SELECT ... FOR UPDATE`.
- Scope: Database row level.
- Pros: Strict serializability enforced directly by PostgreSQL.
- Cons: Holds DB connection open during processing, reducing connection pool throughput under high concurrency.

### Strategy 3: JPA Optimistic Locking
- Uses JPA `@Version` on entities.
- Scope: Database row level version checks (Compare-And-Swap).
- Pros: Non-blocking reads, excellent for low-contention scenarios.
- Cons: Throws `OptimisticLockException` under contention; requires application-level retries or friendly error responses.

### Strategy 4: Redis Distributed Lock (Redisson)
- Uses Redisson `RLock` over Redis.
- Scope: Multi-JVM distributed environment.
- Pros: Coordinates locking across horizontal scale outs, prevents DB connection starvation.
- Cons: Network call latency to Redis, dependency on Redis availability.

---

## 5. Deadlock Prevention

When a user attempts to book multiple seats (e.g., Seat 1 and Seat 2), concurrent requests in reverse order could cause deadlocks:
- Request A locks Seat 1, waits for Seat 2.
- Request B locks Seat 2, waits for Seat 1.

Solution:
All seat lock requests sort seat IDs in strict ascending order before acquiring locks.
`1 -> 2 -> 3 -> ...`
This guarantees a global lock ordering and eliminates circular waiting.

---

## 6. Flash Sale Architecture

Flash sales use a Redis-first atomic counter approach to sustain extreme traffic spikes:
1. Redis atomic `DECR` reduces available ticket count in nanoseconds.
2. If `DECR` result is less than 0, the request is rejected immediately at the cache layer without querying PostgreSQL.
3. If `DECR` succeeds, the request proceeds to PostgreSQL to save the purchase record.
4. If the DB transaction fails, Redis atomically increments `INCR` the count back.

---

## 7. Idempotency

All booking POST requests require an `Idempotency-Key` header.
- First request processes normally and stores the result in both Redis and the `idempotency_records` table.
- Duplicate requests return the stored result immediately without re-executing business logic or reserving extra seats.

---

## 8. Database Schema

The system uses six core tables:
- `users`: User profiles.
- `events`: Event details and aggregate available seat counters.
- `seats`: Seat numbers, status (`AVAILABLE`, `LOCKED`, `BOOKED`), and version number for optimistic locking.
- `bookings`: Booking status (`PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `FAILED`), reference code, and idempotency key.
- `booking_seats`: Junction table linking bookings to seats.
- `flash_sales` & `flash_sale_purchases`: Flash sale metadata and purchase tracking.
- `idempotency_records`: Unique idempotency key entries.

---

## 9. Running the Application

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local builds)
- Maven 3.9+

### Quick Start with Docker Compose

1. Clone the repository:
```bash
git clone https://github.com/priyanjul-beep/Ticket-Booking-System.git
cd Ticket-Booking-System
```

2. Start PostgreSQL, Redis, and Spring Boot app:
```bash
docker compose up --build -d
```

3. Verify application health:
```bash
curl http://localhost:8080/actuator/health
```

4. Swagger UI Documentation:
Open `http://localhost:8080/swagger-ui.html` in your browser.

---

## 10. Running Concurrency & Load Tests

Execute Maven test suites to test concurrent behavior:

```bash
mvn test
```

To run the load testing module and race condition benchmarks:
```bash
mvn test -Dtest=ConcurrencyTest
```

---

## 11. API Quick Reference

- User API:
  - `POST /api/users` - Create a user
  - `GET /api/users/{id}` - Fetch user by ID

- Event API:
  - `POST /api/events` - Create event with seats
  - `GET /api/events` - List events
  - `GET /api/events/{id}` - Fetch event details
  - `GET /api/events/{id}/seats` - List seats for event

- Booking API:
  - `POST /api/bookings?strategy=REDIS` - Create a booking (Strategies: `IN_MEMORY`, `PESSIMISTIC`, `OPTIMISTIC`, `REDIS`)
  - `GET /api/bookings/{id}` - Get booking details
  - `POST /api/bookings/{id}/pay` - Process payment for booking

- Flash Sale API:
  - `POST /api/flash-sales` - Create flash sale
  - `POST /api/flash-sales/{id}/purchase` - Purchase flash sale ticket

---

## 12. License

This project is licensed under the MIT License.
