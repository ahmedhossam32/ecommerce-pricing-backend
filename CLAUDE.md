# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application (starts Docker Compose PostgreSQL automatically via spring-boot-docker-compose)
./mvnw spring-boot:run

# Compile only
./mvnw compile

# Build JAR
./mvnw package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Clean build artifacts
./mvnw clean
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Architecture

**Stack**: Spring Boot 3.3.5 · Java 17 · PostgreSQL · Maven

The app follows a strict layered architecture under `src/main/java/com/ecommerce/`:

```
controller/   → REST endpoints (Spring MVC)
service/      → Business logic, organized by domain:
    auth/         JWT + OAuth2 flows
    product/      Catalog management
    order/        Checkout and order lifecycle
    cart/         Cart item management
    seller/       Seller account management
    pricing/      AI-driven dynamic pricing (Spring AI + OpenAI)
repository/   → Spring Data JPA repositories
entity/       → JPA-mapped domain models
dto/
    request/      Inbound API payloads
    response/     Outbound API payloads
config/       → Security config, CORS, filters, beans
client/       → External API clients (OpenAI wrapper)
exception/    → Custom domain exceptions
util/         → Shared utilities and helpers
```

**Database**: PostgreSQL, managed by Docker Compose (`compose.yaml`). Spring Boot auto-starts it via `spring-boot-docker-compose`. JPA/Hibernate manages the schema.

**Security**: Spring Security with JWT authentication and OAuth2 client (social login/SSO).

**AI**: Spring AI (`spring-ai-starter-model-openai`) powers the `pricing/` service for dynamic/intelligent pricing. OpenAI API key must be configured in environment/properties.

**API Docs**: Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs` (springdoc-openapi v2.6.0).

**Code Generation**: Lombok is used throughout — always add the Lombok annotation processor to any new IDE setup.

## Key Configuration

`compose.yaml` defines the local PostgreSQL instance:
- DB: `mydatabase`, user: `myuser`, password: `secret`, port: `5432`

Application config lives in `src/main/resources/application.properties`. Secrets and environment-specific settings (DB URL, JWT secret, OpenAI API key) are expected via environment variables or profile-specific properties files (e.g., `application-dev.properties`).
