# Mini Payment Gateway Simulator

A small payment service (Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL) plus a separate service that
pretends to be the external gateway.

```
Client ──► payment-gateway (:8080) ──POST /gateway/payments──► gateway-simulator (:8081)
                  ▲                                                     │
                  └──────────────── POST /webhook (SUCCESS/FAILED) ◄─────┘   (after 2-5 s)
```

## Run it

Requirements: Java 21, Maven, PostgreSQL (local).

```bash
# 1. create the database once (psql)
CREATE DATABASE mini_payment_gateway;
# user/password in payment-gateway/src/main/resources/application.yml: adjust to your local setup

# 2. gateway simulator (from gateway-simulator/)
mvn spring-boot:run

# 3. payment service (from payment-gateway/), tables are created by Hibernate on startup
mvn spring-boot:run
```

Config lives in each `application.yml`: DB credentials, `gateway.simulator-url` (payment service),
`gateway.webhook-url` (simulator).

Tests (no Docker needed): `mvn test`

## API

Base URL: `http://localhost:8080`. All bodies are JSON. `{id}` is the payment's UUID.

### Payment object (returned by create, get, capture, refund)

```json
{
  "id": "3f6c1b0e-8d52-4c8e-9a53-0d3c3c1f6a11",
  "merchantId": "m1",
  "amount": 100.50,
  "currency": "EUR",
  "reference": "order-1",
  "status": "PENDING",
  "createdAt": "2026-10-10T08:15:30.123Z",
  "updatedAt": "2026-10-10T08:15:30.123Z"
}
```
`status` is one of `PENDING`, `SUCCESS`, `FAILED`, `CAPTURED`, `REFUNDED`.

### Error object (all errors)

```json
{
  "timestamp": "2026-10-10T08:15:31.456Z",
  "status": 409,
  "error": "Conflict",
  "message": "Cannot move payment from PENDING to CAPTURED",
  "details": []
}
```
`details` lists field errors for validation failures and is empty otherwise.

---

### 1. Create payment: `POST /payments`

Creates a payment in `PENDING` state and submits it to the gateway. Idempotent per `merchantId` + `reference`.

**Request body**

| Field | Type | Rules |
|---|---|---|
| `merchantId` | string | required, max 64 chars |
| `amount` | number | required, at least `0.01`, max 2 decimals |
| `currency` | string | required, 3 uppercase letters, valid ISO 4217 code (e.g. `EUR`) |
| `reference` | string | required, max 100 chars |

```json
{ "merchantId": "m1", "amount": 100.50, "currency": "EUR", "reference": "order-1" }
```

**Responses**

| Status | When | Body |
|---|---|---|
| `201 Created` | New payment created | Payment object (`status: PENDING`), `Location: /payments/{id}` header |
| `200 OK` | Same `merchantId` + `reference` with same amount and currency already exists (safe retry) | The original payment object, nothing new is created |
| `400 Bad Request` | Validation failed or malformed JSON | Error object with `details` |
| `409 Conflict` | Same `merchantId` + `reference` but different amount or currency | Error object |

`400` example:
```json
{
  "timestamp": "2026-10-10T08:15:31.456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": ["amount: must be at least 0.01", "reference: must not be blank"]
}
```

---

### 2. Get payment: `GET /payments/{id}`

Returns the current state of one payment. Use it to see the gateway result after 2-5 seconds.

| Status | When | Body |
|---|---|---|
| `200 OK` | Payment exists | Payment object |
| `404 Not Found` | Unknown id | Error object (`"Payment not found: {id}"`) |
| `400 Bad Request` | `{id}` is not a valid UUID | Error object |

---

### 3. Capture payment: `POST /payments/{id}/capture`

Captures a payment whose gateway result was `SUCCESS`. No request body.

| Status | When | Body |
|---|---|---|
| `200 OK` | Payment was `SUCCESS`, now `CAPTURED` | Payment object |
| `404 Not Found` | Unknown id | Error object |
| `409 Conflict` | Payment is not `SUCCESS`: still `PENDING`, `FAILED`, already `CAPTURED`, or `REFUNDED` | Error object (`"Cannot move payment from {from} to CAPTURED"`) |
| `400 Bad Request` | `{id}` is not a valid UUID | Error object |

Parallel capture requests are serialized with a row lock: exactly one gets `200`, the rest get `409`.

---

### 4. Refund payment: `POST /payments/{id}/refund`

Refunds a captured payment. A payment can be refunded only once. No request body.

| Status | When | Body |
|---|---|---|
| `200 OK` | Payment was `CAPTURED`, now `REFUNDED` | Payment object |
| `404 Not Found` | Unknown id | Error object |
| `409 Conflict` | Payment is not `CAPTURED`: not captured yet, `FAILED`, or already `REFUNDED` | Error object (`"Cannot move payment from {from} to REFUNDED"`) |
| `400 Bad Request` | `{id}` is not a valid UUID | Error object |

---

### 5. Gateway webhook: `POST /webhook`

Called by the (simulated) gateway to report the outcome of a payment.

**Request body**

| Field | Type | Rules |
|---|---|---|
| `paymentId` | string (UUID) | required |
| `status` | string | required, `SUCCESS` or `FAILED` |

```json
{ "paymentId": "3f6c1b0e-8d52-4c8e-9a53-0d3c3c1f6a11", "status": "SUCCESS" }
```

| Status | When | Body |
|---|---|---|
| `200 OK` | Payment was `PENDING` and is now updated | empty |
| `200 OK` | Payment is no longer `PENDING` (duplicate or late update): logged and ignored, nothing changes | empty |
| `404 Not Found` | Unknown `paymentId` | Error object |
| `400 Bad Request` | Missing field, bad UUID, or unknown status value | Error object |
| `409 Conflict` | `status` is `CAPTURED` or `REFUNDED` for a `PENDING` payment (not a legal move) | Error object |

Duplicates return `200` so that gateway retries stop and are harmless.

---

### Gateway simulator (separate service): `POST http://localhost:8081/gateway/payments`

Called by the payment service, not by clients. It accepts the payment, waits 2-5 seconds, picks `SUCCESS` or
`FAILED` at random, and calls `POST /webhook` on the payment service.

```json
{ "paymentId": "3f6c1b0e-8d52-4c8e-9a53-0d3c3c1f6a11" }
```

| Status | When |
|---|---|
| `202 Accepted` | Accepted, the result arrives later via webhook |
| `400 Bad Request` | `paymentId` missing |

---

## Database Schema

### `payments`: current state of each payment

| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `id` | UUID | Primary key | Unique payment identifier, generated by the app. Used in all API paths. |
| `merchant_id` | VARCHAR | Not null, part of unique key | Identifies the merchant who owns the payment. |
| `amount` | NUMERIC(19,2) | Not null | Payment amount. Exact decimal, never floating point. |
| `currency` | VARCHAR(3) | Not null | ISO 4217 currency code, e.g. `EUR`. |
| `reference` | VARCHAR | Not null, part of unique key | Merchant's own order or payment reference. Together with `merchant_id` it makes create idempotent. |
| `status` | VARCHAR | Not null | Current state: `PENDING`, `SUCCESS`, `FAILED`, `CAPTURED`, `REFUNDED`. |
| `version` | BIGINT | | Optimistic-lock counter, increased on every update. Safety net on top of the row lock. |
| `created_at` | TIMESTAMPTZ | Not null | When the payment was created. |
| `updated_at` | TIMESTAMPTZ | Not null | When the payment last changed. |

Unique constraint: `(merchant_id, reference)`. The database refuses a duplicate payment, even under concurrent requests.

### `payment_events`: append-only audit log

| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `id` | BIGSERIAL | Primary key | Unique event id. Increasing order = order of events. |
| `payment_id` | UUID | Not null | The payment this event belongs to (links to `payments.id`). |
| `from_status` | VARCHAR | Nullable | State before the change. `NULL` only for the creation event. |
| `to_status` | VARCHAR | Not null | State after the change. |
| `source` | VARCHAR | Not null | What caused the change: `API` (client call) or `WEBHOOK` (gateway callback). |
| `created_at` | TIMESTAMPTZ | Not null | When the transition happened. |

One row is written for every state transition, in the same transaction as the status change. Rows are never updated or deleted.

## Payment lifecycle

```
PENDING ──► SUCCESS ──► CAPTURED ──► REFUNDED
   └──────► FAILED
```
`FAILED` and `REFUNDED` are final. The allowed moves are defined once, in `PaymentStatus.canMoveTo()`.

## Design decisions

- **Layers:** controller → service → repository, with the state rules in the domain (`Payment.transitionTo`),
  DTOs at the edges, and one `@RestControllerAdvice` for errors.
- **Idempotency:** the database enforces `UNIQUE (merchant_id, reference)`; the code check is only a fast path.
  If two requests race, the loser catches the constraint error and returns the winner's payment. The insert runs
  in its own short transaction so that this catch is safe. Reusing a key with different details returns `409`.
- **Concurrency:** capture, refund and webhook load the row with `SELECT ... FOR UPDATE`, so a parallel request
  waits, then sees the new state and is rejected by the state rules. "Refund once" needs no extra flag, because
  `REFUNDED` has no outgoing moves. `@Version` is kept as a safety net.
- **Audit trail:** every transition writes a `payment_events` row in the same transaction as the status change.
  The table is append-only: no update/delete methods in code, `@Immutable`, and a DB trigger rejects UPDATE/DELETE.
- **Webhook:** it only resolves `PENDING` payments. Duplicate or late updates are logged and ignored with `200`,
  so gateway retries are harmless.
- **Gateway simulator:** a separate Spring Boot app. The payment service calls it after the create transaction has
  committed, asynchronously, so `POST /payments` returns right away. The simulator replies `202`, waits 2-5 s, picks
  `SUCCESS` or `FAILED` at random and calls `/webhook`. The services share only the JSON contract, not code.
- **Money:** `BigDecimal` / `NUMERIC(19,2)`, never `double`.
- **Repeated capture or refund returns `409`** (not `200`), because these are state changes, not idempotent creates.
  
## Project structure

```
.
├── README.md
├── schema.sql                          # reference schema (the app creates it via Hibernate)
│
├── payment-gateway/                    # main service (:8080)
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java/com/example/paymentgateway
│       │   │   ├── PaymentGatewayApplication.java
│       │   │   ├── controller/         # HTTP layer only: validate, delegate, map to response
│       │   │   │   ├── PaymentController.java      # create, get, capture, refund
│       │   │   │   └── WebhookController.java      # gateway callbacks
│       │   │   ├── service/            # business flow, transactions, locking
│       │   │   │   ├── PaymentService.java
│       │   │   │  
│       │   │   ├── Entity/             # entities and the state rules
│       │   │   │   ├── Payment.java                # transitionTo() enforces legal moves
│       │   │   │   ├── PaymentStatus.java          # canMoveTo(): the single rule table
│       │   │   │   ├── PaymentEvent.java           # append-only audit row
│       │   │   │   └── EventSource.java            # API or WEBHOOK
│       │   │   ├── repository/
│       │   │   │   ├── PaymentRepository.java      # includes findByIdForUpdate (row lock)
│       │   │   │   └── PaymentEventRepository.java # save and read only, no delete
│       │   │   ├── gateway/
│       │   │   │   └── GatewayClient.java          # async call to the gateway service
│       │   │   ├── dto/                # request/response records
│       │   │   ├── exception/          # custom exceptions + GlobalExceptionHandler
│       │   │   └── config/
│       │   │       └── AppConfig.java              # @EnableAsync, RestClient with timeouts
│       │   └── resources/application.yml
│       └── test/java/com/example/paymentgateway
│           ├── PaymentTest.java             # state transition rules
│           └── service/PaymentServiceTest.java     # Mockito: idempotency, events, webhook
│
└── gateway-simulator/                  # fake external gateway (:8081), no database
    ├── pom.xml
    └── src/main
        ├── java/com/example/gatewaysimulator
        │   ├── GatewaySimulatorApplication.java
        │   ├── controller/SimulatorController.java # POST /gateway/payments -> 202
        │   ├── service/SimulatorService.java       # @Async: wait 2-5 s, then call /webhook
        │   ├── dto/                                # SimulationRequest, WebhookPayload
        │   └── config/AppConfig.java
        └── resources/application.yml
```

## Tests
run the command "mvn test" inside payment-gateway folder to run test cases
- `PaymentTest`: every legal and illegal state transition (capture only from `SUCCESS`, refund only from
  `CAPTURED`, refund once).
- `PaymentServiceTest` (Mockito): creation and audit events, idempotent replay, key reuse conflict, losing the
  create race, no gateway call on replay, capture, and webhook handling including ignored duplicates.
