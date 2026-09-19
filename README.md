# Booking System

A distributed, event-driven booking/inventory system built to demonstrate
production-grade backend design: concurrency-safe reservations, idempotency,
saga-based consistency across services, resilience, and observability.

## Status: Week 1 — Inventory Service

The `inventory-service` module owns seat/ticket inventory and exposes the
core reservation API. This is the centerpiece of the whole project: it must
never oversell inventory, even under heavy concurrent load.

### How correctness is enforced

Three layers, from primary mechanism to safety net:

- **Distributed lock (Redisson, primary mechanism)**: `ReservationService`
  acquires a Redis lock keyed per inventory item (`inventory-item-lock:<id>`)
  before touching the database, so only one thread across the whole system
  can be inside the reservation critical section for a given item at a time.
  This was a deliberate pivot during development: letting 100 concurrent
  requests race directly against MySQL row locks produced genuine InnoDB
  deadlocks (a single-row `UPDATE` plus a child `INSERT` with a foreign key
  to that row is a well-documented InnoDB deadlock pattern under heavy
  contention) — not just optimistic-lock version conflicts. Serializing at
  the application layer with a cheap Redis lock avoids that class of problem
  entirely, and is the same pattern used for flash-sale/limited-inventory
  systems in production.
- **Idempotency**: every reservation request carries a client-supplied
  `idempotencyKey`, backed by a unique DB constraint
  (`reservation.idempotency_key`). A retried request (client timeout +
  retry, duplicate delivery, etc.) finds the original row and returns the
  same result instead of reserving twice — enforced independently of the
  lock.
- **Optimistic locking** (`InventoryItem.version`, a JPA `@Version` column)
  stays on the entity as defense-in-depth: if the distributed lock is ever
  bypassed, misconfigured, or expires mid-operation, a lost update fails
  loudly (`ObjectOptimisticLockingFailureException`) instead of silently
  corrupting inventory counts.

### Proof: `ConcurrentReservationIntegrationTest`

Fires 100 concurrent requests for 1 seat each against an item with 10 seats
(real MySQL + Redis via Testcontainers) and asserts exactly 10 succeed, 90
are cleanly rejected, and the DB ends up in a consistent state. A second
test fires 10 concurrent requests with the *same* idempotency key and
asserts only one reservation is ever created.

## Running locally

```bash
# Start MySQL + Redis (and Kafka, scaffolded for Week 2+)
docker-compose up -d mysql redis

# Build and run the inventory service
mvn -pl inventory-service -am spring-boot:run
```

## Running tests

```bash
mvn test
```

Integration tests spin up real MySQL and Redis containers via Testcontainers
— Docker must be running.

## API

**Create an inventory item**
```
POST /api/v1/inventory-items
{ "name": "Concert Seat", "totalQuantity": 10 }
```

**Reserve units**
```
POST /api/v1/reservations
{ "inventoryItemId": 1, "quantity": 1, "idempotencyKey": "<client-generated-uuid>" }
```

## Project structure

```
booking-system/
├── common/              shared DTOs (ReservationRequest/Response, ErrorResponse)
├── inventory-service/   owns inventory, concurrency-safe reservations (Week 1)
├── docker-compose.yml   MySQL + Redis + Kafka (KRaft mode) + inventory-service
```

Booking Service and Notification Service (Kafka-coordinated saga flow) land
in Week 2. See the project plan for the full roadmap.
