# E-Commerce Backend — Spring Boot Microservices

A microservices-based e-commerce backend built with Spring Boot and Spring Cloud.

---

## Project Structure

```
.
├── ServiceDiscovery/    # Eureka server — service registry (8761)
├── APIGateway/          # Single entry point, JWT auth filter (8888)
├── UserService/         # User signup, login, JWT token issuance (8080)
├── ProductService/      # Products, categories, Redis cache (8081)
├── ReviewService/       # Product reviews (8082)
├── OrderService/        # Orders, Kafka producer (8083)
├── PaymentService/      # Stripe payments, Kafka producer (8084)
└── EmailService/        # Kafka consumer, sends transactional emails (9000)
```

---

## Service Flow

```
Client
  └── HTTP ──► API Gateway (8888)
                    │  validates JWT, injects X-User-Id & X-User-Role
                    ├──► UserService    (8080)  ──► userdb
                    ├──► ProductService (8081)  ──► productdb + Redis Cache
                    ├──► ReviewService  (8082)  ──► reviewdb
                    ├──► OrderService   (8083)  ──► orderdb ──► Kafka
                    └──► PaymentService (8084)  ──► paymentdb + Stripe ──► Kafka
                                                                    │
                                                              EmailService (9000)
                                                         consumes Kafka events
                                                         and sends emails via SMTP
```

All services register themselves with **Eureka (8761)** on startup.

---

## How to Start

Start services **in this exact order**. Each step depends on the one before it.

### 1. Prerequisites — must be running before any service starts

- **PostgreSQL** — create these databases:
  ```sql
  CREATE DATABASE userdb;
  CREATE DATABASE productdb;
  CREATE DATABASE reviewdb;
  CREATE DATABASE orderdb;
  CREATE DATABASE paymentdb;
  ```
- **Redis** — running on `localhost:6379`
- **Apache Kafka** — running on `localhost:9092`

### 2. Start ServiceDiscovery first

```
ServiceDiscovery → http://localhost:8761
```

Wait for the Eureka dashboard to be accessible before starting anything else.

### 3. Start core services (any order)

```
UserService      → 8080
ProductService   → 8081
ReviewService    → 8082
```

### 4. Start APIGateway

```
APIGateway → 8888
```

Needs Eureka and UserService to be up (gateway calls UserService to validate tokens).

### 5. Start transactional services

```
OrderService   → 8083   (needs Kafka)
PaymentService → 8084   (needs Kafka + Stripe key)
```

### 6. Start EmailService last

```
EmailService → 9000   (needs Kafka + SMTP credentials)
```

---

## Required Environment Variables

Set these in each service's IntelliJ run configuration before starting.

| Variable | Required by | Description |
|---|---|---|
| `DB_PASSWORD` | All services | PostgreSQL password |
| `ADMIN_SIGNUP_SECRET` | UserService | Secret for admin registration |
| `STRIPE_SECRET_KEY` | PaymentService | Stripe test key (`sk_test_...`) |
| `MAIL_USERNAME` | EmailService | SMTP username |
| `MAIL_PASSWORD` | EmailService | SMTP app password |

> All other config (ports, DB URLs, Kafka address, Redis host) defaults to localhost and requires no changes for local development.

---

## Running HTTP Tests

Each service has IntelliJ HTTP Client tests under `<service>/http-tests/`.

1. Add credentials to `http-client.private.env.json` (git-ignored)
2. Select environment: `dev` (direct to service) or `gateway-only` (through gateway)
3. Run setup requests (`0a` → `0b` → ...) before the main tests — they create the required data automatically
