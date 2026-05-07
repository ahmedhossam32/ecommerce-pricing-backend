# GP Backend — AI-Powered Dynamic Pricing Engine

> Graduation Project — Ahmed Hossam | May 2026

A backend-driven fullstack e-commerce system with an AI-powered dynamic pricing engine that combines Machine Learning and Large Language Models to automatically suggest optimal product prices for sellers.

---

## System Overview

The pricing engine uses a hybrid ML + LLM approach:
- **ML Model (XGBoost)** — handles generic unbranded products using 26 physical features
- **LLM (GPT-4o-mini)** — handles branded products using real market knowledge
- **Spring Boot** — orchestrates everything, applies business rules, manages routing

---

## Architecture

```
Seller fills 5 fields
        ↓
System builds 26 ML features automatically
        ↓
ML Model (FastAPI) → physical baseline price
        ↓
LLM (Spring AI)    → brand context + market range + confidence
        ↓
Spring Boot combines ML + LLM → suggested price ±15% range
        ↓
Routing Engine:
  Layer 1: Redis cache hit?         → PENDING_SELLER (skip admin)
  Layer 2: Outside category bounds? → PENDING_ADMIN
  Layer 3: Confidence gate          → HIGH/MEDIUM = PENDING_SELLER
                                       LOW = PENDING_ADMIN
  Layer 4: Admin approves           → LIVE + cached in Redis forever
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3.5 (Java 23) |
| ML Model | Python + FastAPI + XGBoost v4 |
| LLM | Spring AI + OpenAI gpt-4o-mini |
| Database | PostgreSQL |
| Cache | Redis (Memurai on Windows) |
| Auth | Spring Security + JWT |
| Email | Spring Mail + Gmail SMTP |
| API Docs | Swagger (SpringDoc) |
| Build | Maven |

---

## ML Model Performance

| Metric | Score | Meaning |
|--------|-------|---------|
| R² | 0.83 | Explains 83% of price variation |
| MAE | $18.23 | Average prediction error |
| MAPE | 16.13% | Off by 16% on average |
| Dataset | 108,896 rows | Brazilian Olist e-commerce |

---

## Build Layers

| Layer | Description | Status |
|-------|-------------|--------|
| Layer 1 | Project setup, dependencies, DB connection | Done |
| Layer 2 | JPA entities, DB schema, enums | Done |
| Layer 3 | Auth + JWT + Spring Security | Done |
| Layer 4 | Pricing Engine (ML + LLM + Routing) | Done |
| Layer 5 | Seller flow (list, accept, dispute) | Done |
| Layer 6 | Admin flow + Redis cache + Email | In Progress |
| Layer 7 | Buyer flow (browse, history, checkout) | Pending |
| Layer 8 | React frontend (3 actor views) | Pending |

---

## Three Actors

### Seller
- Lists products with 5 fields only
- Receives AI-suggested price with ±15% range
- Accepts suggested price or picks custom price within range
- Disputes price with reasoning if they want more

### Admin
- Reviews disputed and out-of-bounds pricing requests
- Approves or rejects with reasoning
- Approved decisions cached in Redis permanently
- Email notifications sent to sellers automatically

### Buyer
- Browses live products with dynamic prices
- Views price history chart
- Simulates checkout

---

## Key Endpoints

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/auth/register | Register new user |
| POST | /api/auth/login | Login, returns JWT |
| POST | /api/auth/refresh | Refresh access token |

### Seller (Products)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/products | List new product |
| POST | /api/products/{id}/accept | Accept suggested price |
| POST | /api/products/{id}/dispute | Dispute suggested price |
| GET | /api/seller/products | Seller dashboard |
| GET | /api/products/{id} | Single product details |

### Pricing
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/pricing/suggest | Preview price (no product created) |

### Admin
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/admin/requests | View pending approvals |
| POST | /api/admin/approve/{id} | Approve price request |
| POST | /api/admin/reject/{id} | Reject price request |
| POST | /api/admin/override/{id} | Override live product price |
| GET | /api/admin/stats | Dashboard statistics |

### Buyer
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/products | Browse all live products |
| GET | /api/products/{id}/history | Price history |
| POST | /api/orders | Simulate checkout |

---

## Routing Engine Logic

| Condition | Result | Admin Involved? |
|-----------|--------|----------------|
| Redis cache hit + price in range | PENDING_SELLER | No |
| No cache + within bounds + HIGH confidence | PENDING_SELLER | No |
| No cache + within bounds + MEDIUM confidence | PENDING_SELLER | No |
| No cache + within bounds + LOW confidence | PENDING_ADMIN | Yes |
| Price outside category bounds | PENDING_ADMIN | Yes |
| Admin approves | LIVE + cached in Redis | Yes (last time) |
| Admin rejects | Rejected + email sent | Yes |

---

## Database Tables

| Table | Purpose |
|-------|---------|
| users | All users (BUYER/SELLER/ADMIN) |
| products | All product listings |
| pricing_requests | Every pricing decision record |
| pricing_history | Price changes over time |
| approved_decisions | Admin approved pricing rules (Redis backup) |
| category_bounds | Min/max price per category |
| category_stats | Olist-derived category averages for ML |
| orders | Buyer purchases |

---

## How to Run

### Prerequisites
- Java 23
- Maven
- PostgreSQL
- Redis (Memurai on Windows)
- Python 3.x (for ML service)
- OpenAI API key

### Setup

**1. Clone the repository:**
```bash
git clone https://github.com/ahmedhossam32/ecommerce-pricing-backend.git
cd ecommerce-pricing-backend
```

**2. Configure application.properties:**
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Fill in your actual values
```

**3. Start ML service:**
```bash
cd ml-service
uvicorn main:app --reload
```

**4. Start Redis:**
Start Memurai service on Windows

**5. Run Spring Boot:**
```bash
mvnw.cmd spring-boot:run
```

**6. Access Swagger UI:**
http://localhost:8080/swagger-ui.html

---

## Project Structure

```
src/main/java/com/ecommerce/
├── client/
│   ├── LLMClient.java
│   └── MLClient.java
├── config/
│   ├── JwtAuthFilter.java
│   ├── SecurityConfig.java
│   └── SpringAIConfig.java
├── controller/
│   ├── AuthController.java
│   ├── PricingController.java
│   └── ProductController.java
├── dto/
│   ├── request/
│   └── response/
├── entity/
│   ├── User.java
│   ├── Product.java
│   ├── PricingRequest.java
│   └── ...
├── enums/
├── exception/
├── repository/
├── security/
└── service/
    ├── auth/
    ├── pricing/
    ├── product/
    └── admin/
```

---

## Interview Story

*"I built a fullstack e-commerce system with an AI-powered dynamic pricing engine. The backend is Spring Boot with JWT auth and role-based access for 3 actors. The pricing engine uses a hybrid approach: ML handles generic unbranded products and provides physical signals, LLM handles branded products and provides real-world market context. Spring Boot orchestrates both. Redis caches approved pricing decisions so admin workload reduces over time and the system gets smarter with every approval."*

---

## Related Repositories

- [ML Model](https://github.com/ahmedhossam32/price-prediction-ml-model) — XGBoost price prediction model
- Frontend — Coming soon