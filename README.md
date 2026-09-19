# Booking System

A distributed, event-driven booking/inventory system built to demonstrate
production-grade backend design: concurrency-safe reservations, idempotency,
saga-based consistency across services, resilience, and observability.

## Status: Week 2 — Saga Flow & Messaging

Four services now work together end-to-end:

- **`inventory-service`** — owns seat/ticket inventory (Week 1). Exposes
  reserve and release (compensating action) endpoints, both concurrency-safe.
- **`booking-service`** — orchestrates the saga: synchronously reserves
  inventory, then coordinates the async payment step and final
  confirm/cancel via Kafka.
- **`payment-service`** — simulates payment processing, consuming
  `booking.reserved` and publishing `payment.completed`/`payment.failed`.
- **`notification-service`** — consumes the final outcome events
  (`booking.confirmed`/`booking.failed`) and logs a simulated notification.

### The saga

```
Client -> POST /api/v1/bookings (Booking Service)
  1. Booking Service creates a local Booking row (PENDING), then calls
     Inventory Service synchronously to reserve. Sync here because the
     client needs an immediate answer to "is this even possible" -- no
     point accepting a booking for an already-sold-out item.
       - Reservation rejected (409, sold out) -> Booking -> FAILED,
         returned to the client immediately. Nothing was ever reserved,
         so there's nothing to compensate.
       - Reservation succeeds -> Booking -> RESERVED, publish
         `booking.reserved`, return 202 Accepted (client polls
         GET /api/v1/bookings/{id} for the final outcome).
  2. Payment Service consumes `booking.reserved`. Simulated rule
     (deterministic, not random, so the failure path is reliably
     demoable/testable): quantity >= 4 always fails.
       - Publishes `payment.completed` or `payment.failed`.
  3. Booking Service consumes the payment outcome:
       - completed -> Booking -> CONFIRMED, publish `booking.confirmed`.
       - failed -> calls Inventory Service's release endpoint
         (compensating action, reverses the reservation), Booking ->
         CANCELLED, publish `booking.failed`.
  4. Notification Service consumes `booking.confirmed`/`booking.failed`
     and logs a simulated notification -- independently of Booking
     Service, demonstrating Kafka fan-out.
```

Everything after step 1 is async via Kafka rather than direct service
calls, specifically so a crashed consumer doesn't lose the request: Kafka
retains the message on its durable log, and the consumer resumes from its
last committed offset on restart. Verified by killing `payment-service`
mid-flow (see below) -- the booking sat at `RESERVED` for the whole outage
and resolved to `CONFIRMED` the moment the service came back, with no
manual intervention.

### Why sync REST for reserve, but Kafka for everything after

This is a considered tradeoff, not a default: reserve is the one step
where the client is still waiting on the response and where a definitive
yes/no is knowable immediately (does the inventory exist right now).
Payment is the kind of step that's slow, retryable, and can fail for
reasons unrelated to this request -- exactly what an async, durable queue
is for. Doing the whole saga synchronously would mean a slow/down payment
gateway blocks the client's request thread and a crash mid-call loses the
request entirely; Kafka avoids both.

### Idempotency and compensation correctness

- **Booking Service**: `booking.idempotency_key` has a unique DB
  constraint. A duplicate `POST /api/v1/bookings` with the same key
  returns the existing booking's current status instead of creating a
  second one or re-running the saga.
- **Inventory release (compensating action)**: idempotent by construction
  -- releasing an already-`RELEASED` reservation is a no-op success (see
  `ReservationTransactionalOps.doRelease`), so a re-delivered
  `payment.failed` message (Kafka's at-least-once delivery) can safely
  trigger the release call more than once.
- **Booking Service's Kafka listeners**: both `confirmBooking` and
  `cancelBooking` check the booking's current status before acting and
  no-op if it's already past `RESERVED`, so redelivered payment-outcome
  messages don't double-process.

### Inventory Service changes since Week 1

Added `POST /api/v1/reservations/{id}/release`, the compensating action
used by the saga's failure path. It reuses the same Redisson
per-inventory-item distributed lock as the reserve path (extracted into
`ReservationService.withInventoryItemLock`) and the same
proxy-safe-transactional-bean pattern as `doReserve`
(`ReservationTransactionalOps.doRelease`) -- see Week 1's notes on why
`@Transactional` methods live on a separate bean.

### A known tradeoff: duplicated Kafka config across services

`booking-service`, `payment-service`, and `notification-service` each
have their own near-identical `KafkaConfig` class (producer/consumer
factories, trusted packages for `JsonDeserializer`). This is deliberate,
not an oversight: extracting it into the shared `common` module would
force every service -- including ones that might not need Kafka -- to
carry it as a transitive dependency, and would couple each service's
consumer/producer configuration to a single shared definition when in
practice these can reasonably diverge per service (different consumer
group semantics, retry policy, etc.). Small, explicit duplication here
was chosen over a shared abstraction that doesn't yet have a second
proven use case.

## Running locally

```bash
docker-compose up -d --build
```

Brings up all 7 containers: MySQL, Redis, Kafka (KRaft mode, no
ZooKeeper), and the four services. MySQL is exposed on host port `3307`
(mapped from the container's `3306`) to avoid clashing with a local MySQL
installation -- services talk to each other on the internal Docker
network at the normal port.

### Manual smoke test

```bash
# Create an inventory item
curl -X POST http://localhost:8081/api/v1/inventory-items \
  -H "Content-Type: application/json" \
  -d '{"name":"Concert Seat","totalQuantity":10}'

# Book 2 seats -- succeeds through the full saga
curl -X POST http://localhost:8082/api/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{"inventoryItemId":1,"quantity":2,"idempotencyKey":"demo-1"}'

# Book 4+ seats -- payment simulator declines, inventory is released
curl -X POST http://localhost:8082/api/v1/bookings \
  -H "Content-Type: application/json" \
  -d '{"inventoryItemId":1,"quantity":4,"idempotencyKey":"demo-2"}'

# Poll for the final outcome
curl http://localhost:8082/api/v1/bookings/1
curl http://localhost:8081/api/v1/inventory-items/1
```

To see Kafka durability in action: `docker stop payment-service`, create
a booking, confirm it stays `RESERVED`, then `docker start payment-service`
and watch it resolve on its own.

## Running tests

```bash
mvn test
```

Integration tests spin up real MySQL, Redis, and Kafka containers via
Testcontainers — Docker must be running.

## API

**Inventory Service** (port 8081)
```
POST /api/v1/inventory-items                  { name, totalQuantity }
GET  /api/v1/inventory-items/{id}
POST /api/v1/reservations                     { inventoryItemId, quantity, idempotencyKey }
POST /api/v1/reservations/{id}/release
```

**Booking Service** (port 8082)
```
POST /api/v1/bookings                         { inventoryItemId, quantity, idempotencyKey }
GET  /api/v1/bookings/{id}
```

## Project structure

```
booking-system/
├── common/                 shared DTOs: reservation/booking requests & responses,
│                           saga event contracts (BookingReservedEvent, etc.)
├── inventory-service/      owns inventory, concurrency-safe reservations (Week 1)
│                           + compensating release endpoint (Week 2)
├── booking-service/        saga orchestration: reserve (REST) -> pay -> confirm/cancel (Kafka)
├── payment-service/        simulated payment processor
├── notification-service/   simulated notification dispatch
├── docker-compose.yml      MySQL + Redis + Kafka (KRaft) + all 4 services
```

Resilience (circuit breakers, retry policies), observability (correlation
IDs, metrics, dashboards), and load testing land in Week 3. See the
project plan for the full roadmap.
