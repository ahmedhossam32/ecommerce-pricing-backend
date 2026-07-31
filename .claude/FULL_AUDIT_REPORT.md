# DynaMart Backend — Full Systematic Code Audit

Scope: `ecommerce-backend`, Spring Boot 3.3.5, Maven, single module `com.ecommerce`. All findings are from reading the actual source (111 `.java` files) — not from `README`/docs, which are cross-checked and flagged where they diverge from what the code does.

---

## 1. Project Overview

**Package layout** (`src/main/java/com/ecommerce`): `client/` (LLMClient, MLClient), `config/` (SecurityConfig, JwtAuthFilter, RateLimitingFilter, RestTemplateConfig, CloudinaryConfig, SpringAIConfig), `controller/` (8 controllers), `dto/request` + `dto/response` (13 + 15 DTOs), `entity/` (10 JPA entities), `enums/` (5), `exception/` (GlobalExceptionHandler + 4 custom exceptions), `repository/` (10 Spring Data repos), `service/` (8 domain sub-packages: admin, auth, buyer, cart, pricing, product, upload, user, wishlist), `util/JwtUtil`. Standard layered monolith, one deployable jar.

**Build tool / Java version**: Maven, `spring-boot-starter-parent` 3.3.5. `pom.xml:21` sets `<java.version>21</java.version>` and the `Dockerfile` builds/runs on `eclipse-temurin:21` — **the actual project is Java 21, not Java 23** as assumed in the audit brief. This is the first doc/prompt-vs-code mismatch found.

**Dependencies (pom.xml, with versions):**

| Group | Artifact | Version | Purpose |
|---|---|---|---|
| org.springframework.boot | spring-boot-starter-web | 3.3.5 (parent) | REST/MVC |
| org.springframework.boot | spring-boot-starter-data-jpa | 3.3.5 | JPA/Hibernate |
| org.postgresql | postgresql | managed, runtime | DB driver |
| org.springframework.boot | spring-boot-starter-security | 3.3.5 | Security |
| org.springframework.boot | spring-boot-starter-oauth2-client | 3.3.5 | Declared but **no evidence of active use** anywhere in `SecurityConfig` or elsewhere — likely vestigial dependency |
| org.springframework.boot | spring-boot-starter-validation | 3.3.5 | Bean Validation |
| org.springframework.ai | spring-ai-starter-model-openai | 1.0.0 (via BOM) | OpenAI ChatClient |
| org.projectlombok | lombok | managed, optional | Codegen |
| org.springframework.boot | spring-boot-devtools | runtime, optional | Dev reload |
| org.springframework.boot | spring-boot-docker-compose | runtime, optional | **Disabled** (`spring.docker.compose.enabled=false`) and no `docker-compose.yml` exists — vestigial |
| org.springdoc | springdoc-openapi-starter-webmvc-ui | 2.6.0 | Swagger/OpenAPI UI |
| io.jsonwebtoken | jjwt-api/impl/jackson | 0.12.6 | JWT |
| org.springframework.boot | spring-boot-starter-data-redis | 3.3.5 | Redis (Lettuce) |
| org.springframework.boot | spring-boot-starter-mail | 3.3.5 | SMTP (Brevo) |
| org.springframework.boot | spring-boot-starter-actuator | 3.3.5 | Health/metrics |
| com.cloudinary | cloudinary-http44 | 1.36.0 | Image upload |

**`application.properties`** — real file is **gitignored** (confirmed via `.gitignore` + `git ls-files`; only `application.properties.example` is tracked). Full property table:

| Property | Value | Flag |
|---|---|---|
| `spring.application.name` | `pricing-engine` | static |
| `server.port` | `8080` | static |
| `spring.datasource.url` | `jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:ecommerce_gp}` | env-overridable |
| `spring.datasource.username` | `${DB_USERNAME:postgres}` | env-overridable |
| `spring.datasource.password` | `${DB_PASSWORD:postgres123}` | **hardcoded fallback credential** |
| `spring.jpa.hibernate.ddl-auto` | `update` | no Flyway/Liquibase — auto-migrates on every boot |
| `spring.jpa.show-sql` / `hibernate.format_sql` | `true` | dev-only verbosity left on |
| `logging.level.org.hibernate.SQL` | `DEBUG` | dev-only verbosity left on |
| `hibernate.default_batch_fetch_size` | `20` | batching, not a substitute for JOIN FETCH |
| `spring.sql.init.mode` | `always` | `data.sql` reruns every boot |
| `spring.data.redis.host/.port` | `${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}` | env-overridable |
| `spring.docker.compose.enabled` | `false` | explicitly disabled |
| `app.jwt.secret` | `${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}` | **hardcoded 64-char hex fallback secret — real-looking, not a placeholder** |
| `app.jwt.expiration` | `86400000` (24h) | access token TTL |
| `app.jwt.refresh-expiration` | `604800000` (7d) | refresh token TTL |
| `spring.ai.openai.api-key` | `${OPENAI_API_KEY}` | **no fallback — good, fails fast if unset** |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | static |
| `spring.ai.openai.chat.options.temperature` | `0.1` | static |
| `ml.service.url` | `${ML_SERVICE_URL:http://localhost:8000}` | env-overridable |
| `spring.mail.host/port` | `smtp-relay.brevo.com` / `587` | static |
| `spring.mail.username` | `${BREVO_SMTP_USERNAME:ac5f92001@smtp-brevo.com}` | **hardcoded fallback, real-looking** |
| `spring.mail.password` | `${BREVO_SMTP_PASSWORD:bskDNKXywdW6Ay8}` | **hardcoded fallback, real-looking** |
| `spring.mail.from` | `noreply@dynamart.me` | static, real domain |
| `management.endpoints.web.exposure.include` | `health` | reasonable |
| `spring.servlet.multipart.max-file-size/-request-size` | `10MB` / `50MB` | static |
| `logging.level...transaction.interceptor` | `TRACE` | **left-in debug tracing**, preceded by comment `# Transaction boundary tracing (added for verify run — remove after)` — never removed |
| `logging.level...HikariPool` | `DEBUG` | left-in debug tracing |
| `cloudinary.cloud-name/api-key/api-secret` | `${CLOUDINARY_...:dnqp6wte7 / 184965579373373 / mMx-ItQp1YA50qTrPd4rNLdxuGc}` | **hardcoded fallbacks, all real-looking** |

Verdict: the file itself is never committed (no active git leak), but every secret uses `${ENV_VAR:realDefault}` instead of `${ENV_VAR}` with no default — if the gitignore rule ever lapses or the file is copied elsewhere, real-looking working credentials travel with it. This is a **partial fix** relative to what commit `c9b22c7` ("externalize secrets to env vars") claims to have done.

`application.properties.example` (the tracked template) has drifted from the real file: it still says `smtp.gmail.com`/`YOUR_GMAIL_APP_PASSWORD` (real file uses Brevo), includes a `springdoc.swagger-ui.path` line absent from the real file, and has none of the JWT-hex-default/tracing/multipart lines — the template is stale relative to what's actually deployed.

**Docker/config files**: only a `Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-21` build stage runs `mvn clean package -DskipTests`; `eclipse-temurin:21-jre` runtime stage, `EXPOSE 8080`). No `docker-compose.yml`, no Kubernetes manifests, no CI/CD workflow files found. `src/main/resources/data.sql` seeds 69 `category_stats` rows (idempotent `ON CONFLICT DO NOTHING`) plus 9 unconditional `UPDATE category_bounds SET max_price = ...` statements for luxury categories — since `spring.sql.init.mode=always`, **any manual admin tuning of those 9 categories' bounds is silently reset on every restart.**

---

## 2. Data Layer

### Entities (10 total)

**`User`** (`entity/User.java`): `id` (IDENTITY), `name` (NOT NULL), `email` (NOT NULL, UNIQUE), `password` (nullable — OAuth/Google users legitimately have none, but nothing stops a LOCAL user having a null password at the DB level either), `role` (`Role` enum string, NOT NULL), `provider` (`AuthProvider` enum, defaulted to `LOCAL` only via `@Builder.Default`, not DB-enforced), `profilePictureUrl`, `createdAt`. Implements Spring Security `UserDetails` directly; `getAuthorities()` returns one `ROLE_<role>` authority; no `isEnabled`/account-lock overrides, so there is **no account-disable mechanism**. No back-reference `@OneToMany`s — all navigation is one-directional from child entities.

**`Product`**: `seller` (`@ManyToOne LAZY`, NOT NULL FK), `name` (NOT NULL), `description` (TEXT), `brand` (nullable), `category` (NOT NULL **plain string, not an FK/enum** — matched against `CategoryBounds`/`CategoryStats` by string equality only, no referential integrity), `weight`/`freightValue`/`photosQty` (all nullable), `imageUrls` via `@ElementCollection` → separate `product_images(product_id, image_url)` table (no dedicated entity/repo, expected), `price` (`numeric(10,2)`, nullable), `status` (`ProductStatus` enum, defaulted `DRAFT` only at app level, not DB), `createdAt`. **No `@Version` optimistic-locking column anywhere in the schema**, despite `Product.price`/`status` being mutated by seller, admin, and the pricing pipeline concurrently.

**`CartItem`** / **`SavedProduct`**: identical shape — `buyer`, `product` (both `@ManyToOne LAZY`, NOT NULL), timestamp, plus a DB-enforced `UNIQUE(buyer_id, product_id)` constraint. No quantity field on cart (add-again is presumably an upsert at service level).

**`Order`**: `buyer`, `product` (NOT NULL FKs), `priceAtPurchase` (nullable — an order can be persisted with a null purchase price), `createdAt`. No status/lifecycle field, no quantity — a minimal purchase record, not a full order-management entity.

**`PricingRequest`**: `product` (`@ManyToOne LAZY`) is **the only FK in the entire schema without `nullable=false`** — a `PricingRequest` can in principle be orphaned from any product at the DB level. Carries `suggestedPrice`, `sellerPrice`, `mlBaselinePrice`, `marketPriceMin/Max` (all nullable `numeric(10,2)`), `sellerReasoning` (TEXT), `status` (`PricingRequestStatus`, defaulted `PENDING`), plus LLM-derived free-text fields `brand`, `llmConfidence` (plain `String`, not an enum despite reading like one), `condition`/`conditionNotes` (TEXT — note the `Condition` enum exists in `enums/Condition.java` but is **never referenced by this entity**; it's only used transiently in service logic), `conditionGrade`, `reasoning`, `createdAt`.

**`PricingHistory`** — **dead entity.** `product` (NOT NULL FK), `oldPrice`/`newPrice` (nullable), `changedAt`. Injected via constructor DI into both `ProductServiceImpl` (`ProductServiceImpl.java:51`) and `AdminServiceImpl` (`AdminServiceImpl.java:58`), but a whole-tree grep for `pricingHistoryRepository\.\w+\(` returns **zero matches** — nothing ever calls `save()` or `deleteByProduct()` on it. The table exists, compiles, is wired by Spring, and is never populated.

**`ApprovedDecision`**: `brand`/`category` (NOT NULL), `approvedMin`/`approvedMax` (`numeric(10,2)`), `createdAt`. No FK to `Product` — a standalone "this brand+category price band was approved before" precedent record. Confirmed live: written at `AdminServiceImpl.java:165`, counted at `:137`, bulk-read at `CacheWarmupService.java:26`.

**`CategoryBounds`**: `category` (UNIQUE, NOT NULL), `minPrice`/`maxPrice`. Used by `RoutingServiceImpl.java:56` to clamp/flag out-of-range prices.

**`CategoryStats`**: `category` (UNIQUE, NOT NULL), `avgPrice`, `avgReview`, `medianSalesCount`, `mostCommonPaymentType`, `defaultMaxInstallments`. Feeds `PricingServiceImpl`/`FeatureBuilderServiceImpl`.

### Enums

| Enum | Values |
|---|---|
| `ProductStatus` | `DRAFT, PENDING_REVIEW, LIVE, REJECTED, DELETED` — **only one pending state** |
| `Role` | `BUYER, SELLER, ADMIN` |
| `AuthProvider` | `LOCAL, GOOGLE` |
| `Condition` | `NEW, USED, REFURBISHED, UNKNOWN` + static `from(String)` that upper-cases/trims and falls back to `UNKNOWN` on any parse failure, never throws |
| `PricingRequestStatus` | `PENDING, APPROVED, REJECTED` |

**Docs mismatch #2**: the GP proposal's stated lifecycle `DRAFT → PENDING_REVIEW/PENDING_ADMIN/PENDING_SELLER → LIVE/REJECTED/DELETED` does **not** match the actual `ProductStatus` enum — there is no `PENDING_ADMIN` or `PENDING_SELLER` value anywhere in code. Those two strings exist only as transient response-DTO labels (`PricingSuggestionResponse.status`, etc.), never persisted as `Product.status`. See §3 for the real transition table.

### Repositories (10) — notable custom queries

- **`ProductRepository`**: 5 overlapping "list by status/all" method families (paged/unpaged × fetch-joined/plain) — `findAllByOrderByCreatedAtDescWithSeller`/`findByStatusOrderByCreatedAtDescWithSeller` (both `@Query` with explicit `JOIN FETCH p.seller` + explicit `countQuery`) are the N+1-safe admin-facing variants (added per commit `54ce3d8`); the plain `findByStatus`/`findAllByOrderByCreatedAtDesc` variants coexist and are unclear duplication risk. `calculateRevenueForSeller(User)` — `@Query("SELECT COALESCE(SUM(o.priceAtPurchase),0) FROM Order o WHERE o.product.seller = :seller")` — lives on `ProductRepository` but queries `Order`, and is **defined identically, verbatim, on `OrderRepository.java:16-17` too** (exact duplicate JPQL on two repos).
- **`CartItemRepository`**: `findByBuyerWithProductAndSeller` — two-level `JOIN FETCH` (cart→product→seller), the N+1-safe variant used by `CartServiceImpl.getCart`.
- **`SavedProductRepository`**: **no JOIN FETCH variant exists** — only plain `findByBuyer`, unlike cart's equivalent. Confirmed N+1 risk in `WishlistServiceImpl.getSaved` (§3).
- **`PricingRequestRepository`**: `findByStatus` (unpaged — admin queue), `findByStatusAndProduct_Status`, `findTopByProductOrderByCreatedAtDesc`, `findByProductIn` (batch).
- **`PricingHistoryRepository`**: only method is `deleteByProduct` — never called, always deletes zero rows in practice (see dead entity above).
- **`ApprovedDecisionRepository`**: no custom methods, only inherited JPA CRUD.

### Reconstructed schema (table list)

`users(id PK, name NOT NULL, email NOT NULL UNIQUE, password, role NOT NULL, provider, profile_picture_url, created_at)`
`products(id PK, seller_id NOT NULL FK→users, name NOT NULL, description TEXT, brand, category NOT NULL, weight, freight_value, photos_qty, price numeric(10,2), status, created_at)`
`product_images(product_id FK→products, image_url)` — implicit collection table, not an entity
`cart_items(id PK, buyer_id NOT NULL FK→users, product_id NOT NULL FK→products, added_at, UNIQUE(buyer_id,product_id))`
`saved_products(id PK, buyer_id NOT NULL FK→users, product_id NOT NULL FK→products, saved_at, UNIQUE(buyer_id,product_id))`
`orders(id PK, buyer_id NOT NULL FK→users, product_id NOT NULL FK→products, price_at_purchase numeric(10,2), created_at)`
`pricing_requests(id PK, product_id FK→products [nullable — only nullable FK in schema], suggested_price, seller_price, seller_reasoning TEXT, status, brand, llm_confidence, condition TEXT, condition_notes TEXT, condition_grade, reasoning TEXT, ml_baseline_price, market_price_min, market_price_max, created_at)`
`pricing_history(id PK, product_id NOT NULL FK→products, old_price, new_price, changed_at)` — **exists, never populated**
`approved_decisions(id PK, brand NOT NULL, category NOT NULL, approved_min, approved_max, created_at)`
`category_bounds(id PK, category NOT NULL UNIQUE, min_price, max_price)`
`category_stats(id PK, category NOT NULL UNIQUE, avg_price, avg_review, median_sales_count, most_common_payment_type, default_max_installments)`

### Dead data model

| Entity | Live? |
|---|---|
| User, Product, CartItem, SavedProduct, Order, PricingRequest, ApprovedDecision, CategoryBounds, CategoryStats | Live — confirmed repository calls in service layer |
| **PricingHistory** | **Dead** — table/entity/repo exist, injected, never called |

This directly weakens the "every prediction event logged for audit" requirement — see §8.

---

## 3. Service Layer

### Every service class and public method

**`PricingServiceImpl.getSuggestion(request, seller)`** (`PricingServiceImpl.java:31`) — orchestrates the full pipeline (traced in full below). Calls `CategoryStatsRepository`, `LLMService` (×2), `RoutingService` (×2), `FeatureBuilderService`, `MLService`. Deliberately **not** `@Transactional` — see §Transactional audit.

**`LLMServiceImpl`** — thin passthrough to `LLMClient.extractProductInfo`/`analyzePricing`; never throws (LLMClient swallows everything internally).

**`MLServiceImpl.predict`** — thin passthrough to `MLClient.predict`; propagates `PricingException` on failure.

**`FeatureBuilderServiceImpl.buildFeatures`** (`FeatureBuilderServiceImpl.java:16`) — pure computation, builds all fields of `MLRequest`. Private helper `sizeCategory` (`:74-79`) is **dead code**, never called.

**`RoutingServiceImpl`** — `determineStatus` (`@Transactional(readOnly=true)`, 3-layer routing, see below), `cacheApprovedRange` (Redis write, **not** wrapped in try/catch — will throw uncaught if Redis is down, inconsistent with the "Redis is optional" philosophy elsewhere in this class), `findCachedRange` (probes 4 hardcoded buckets; possibly unused — the integrations audit found no caller besides the pipeline's own early-cache-check step, which calls it directly from `PricingServiceImpl`).

**`CacheWarmupService.warmUpCache`** (`@EventListener(ApplicationReadyEvent.class)`, `@Transactional(readOnly=true)`) — loads **all** `ApprovedDecision` rows (unpaged `findAll()`) and calls `cacheApprovedRange(brand, category, midpoint, null)` for each, always passing `condition = null` → resolves to `Condition.UNKNOWN` → **every warmed cache entry is keyed under the "unknown" condition bucket**, so a NEW-condition approval and a USED-condition approval for the same brand/category collide into the same Redis key on warmup and silently overwrite each other (structural gap: `ApprovedDecision` doesn't even store `condition`). Whole method wrapped in try/catch — app still boots if this fails.

**`ProductServiceImpl`**: `listProduct` (3-transaction-split flow, see §Transactional), `acceptPrice` (seller accepts within ±10% of suggested price; `@Transactional`), `disputePrice` (`@Transactional`), `getSellerProducts` (**N+1**: one `pricingRequestRepository.findTopByProductOrderByCreatedAtDesc` call per product inside `.map()`), `getProductById`, `getDashboard` (loads all seller products then does 4 separate in-memory `.stream().filter().count()` passes instead of aggregate queries), `uploadProductImages` (`@Transactional` but loops Cloudinary network calls **inside** the open transaction — the same anti-pattern `listProduct` was explicitly restructured to avoid, not applied here), `deleteProduct` (ownership checked manually post-fetch, not via `findByIdAndSeller`; soft-delete only).

**`ProductPersistenceHelper`**: `saveDraftProduct` (creates `DRAFT` product, `brand` left null until finalize step), `finalizePricingRequest` — **`switch` on `suggestion.getStatus()` handles only the literal string `"PENDING_ADMIN"`, has no `default` case** — any other status string silently leaves the product in `DRAFT` with no error logged.

**`AdminServiceImpl`**: `getPendingRequests`, `getRequestById`, `getAllProducts` (paginated), `getStats` — correctly read-only transactional. `approveRequest`/`rejectRequest`/`overridePrice` are public, **not** `@Transactional`, and delegate to **private** methods `doApproveTransaction`/`doRejectTransaction`/`doOverrideTransaction` that **are** annotated `@Transactional` — see the critical bug below. `deleteProduct` (public, correctly `@Transactional`) — **no ownership or status precondition at all**, can hard/soft-delete a `LIVE` product with active order history.

**`CartServiceImpl`** / **`WishlistServiceImpl`**: standard CRUD, `@Transactional`. Cart's `getCart` uses the join-fetch query (N+1-safe); wishlist's `getSaved` uses plain `findByBuyer` (N+1 risk, no fetch-join equivalent exists).

**`BuyerServiceImpl`**: `getAllLiveProducts` (+ paginated overload), `getProductById`/`getProductHistory` (both throw the **same** `ResourceNotFoundException` message whether the product doesn't exist or merely isn't `LIVE` yet — looks like a deliberate but undocumented privacy choice), `placeOrder` (`@Transactional`, sends confirmation email *inside* the transaction — safe only because `EmailServiceImpl.send` swallows all exceptions), `getMyOrders`, `getOrderById` (throws `AccessDeniedException` if order doesn't belong to caller).

**`UserServiceImpl.uploadProfilePicture`** — not `@Transactional`; Cloudinary upload happens before the DB save with no compensating action if save fails afterward (orphaned Cloudinary asset, low risk).

**`AuthServiceImpl`**: `register` (`@Transactional`; explicitly downgrades any `Role.ADMIN` registration request to `Role.BUYER`, `AuthServiceImpl.java:36`), `login` (delegates to `AuthenticationManager`; **bare `orElseThrow()` with no exception supplier** on the post-auth user lookup — throws generic unmapped `NoSuchElementException` in the (should-be-unreachable) case the user vanished between auth and lookup), `refresh` (`TokenRefreshException` on invalid/expired token; **echoes back the same refresh token rather than rotating it** — see §5).

**`EmailServiceImpl`** — 5 public send methods, each builds an inline HTML template, funnels into a shared private `send()` that **catches all exceptions and only logs** — a failed send is silently swallowed, never surfaced to the caller.

**`CloudinaryService`** — `uploadProfilePicture`/`uploadProductImage` wrap `IOException` as a generic (undominated, uncaused) `RuntimeException`; `deleteImage` — **dead code, never called anywhere** — products/profile pictures are never actually removed from Cloudinary storage on deletion, so orphaned media accumulates indefinitely.

### Full pricing pipeline trace (exact logic, file:line cited)

Entry: `PricingServiceImpl.getSuggestion` (`PricingServiceImpl.java:31`), invoked from `ProductServiceImpl.listProduct` (`:60`), itself split across three short transactions specifically to avoid holding a DB connection during the multi-second LLM+ML round trip (comment at `ProductServiceImpl.java:59`; this pattern was introduced in commit `dc80b79`).

**Step 0 — category stats** (`PricingServiceImpl.java:34-36`): `categoryStatsRepository.findByCategory(category.toLowerCase()).orElse(null)`.

**Step 1 — LLM Call 1 (extraction)** (`:39`; prompt `LLMClient.java:20-49`), quoted verbatim:

> You are a product information extractor for an e-commerce platform.
> Extract structured facts from this product description.
> Return ONLY valid JSON, no markdown, no explanation.
>
> Product description: "%s"
>
> Rules:
> - brand: The most prominent brand name. Use "UNKNOWN" if none found. Never null.
> - condition: Classify as exactly one of:
>     "NEW"         → described as new, sealed, brand new, unopened, never used
>     "USED"        → described as used, second hand, secondhand, pre-owned, previously owned, gently used, worn, minor scratches, good condition, fair condition, like new, open box
>     "REFURBISHED" → described as refurbished, restored, reconditioned, certified pre-owned
>     "UNKNOWN"     → no condition mentioned (assume new retail listing)
> - productType: What the product actually is, not the brand. Examples: "smartphone", "laptop", "running shoes", "mechanical keyboard", "handbag", "smartwatch", "wireless headphones", "gaming mouse"
> - modelIdentifier: Specific model if mentioned. Examples: "iPhone 17 Pro Max 256GB", "Galaxy S25 Ultra", "WH-1000XM6". Use null if no specific model mentioned.
>
> Return exactly this JSON:
> { "brand": "Apple", "condition": "NEW", "productType": "smartphone", "modelIdentifier": "iPhone 17 Pro Max 256GB" }

The LLM's `condition` guess is requested but **never read** downstream — actual condition always comes from the seller's own form field (`request.getCondition()`). On any exception, `LLMClient.extractProductInfo` (`:57-62`) returns `LLMResponse.builder().brand("UNKNOWN").build()` — this call can never surface as an HTTP-visible failure.

**Step 2 — early cache check** (`:42-69`): probes `routingService.findCachedRange(brand, category, condition)` across 4 Redis buckets; on a hit, returns immediately with `status="PENDING_SELLER"`, `confidence="HIGH"`, skipping ML and LLM Call 2 entirely. Bucket-keyed (not exact-price-keyed), so this is an approximate shortcut by design.

**Step 3 — feature building** (`:73`; `FeatureBuilderServiceImpl.java:16-66`): 26-field `MLRequest`. `weight` = seller-supplied if `>0`, else LLM-estimated if `>0`, else hardcoded fallback `500.0` (`:71`). `estimatedVolume = weight * 5.0`, cube-root used for length/width/height (a cube approximation). Missing-stats fallbacks: `sellerAvgPrice`/`categoryAvgPrice → 100.0`, `avgReview → 4.0`, `salesCount → 50`, `maxInstallments → 12`, `paymentTypeMode → "credit_card"`. **Geography is hardcoded**: `customerState = sellerState = "SP"` (a Brazilian state code — leftover from the underlying Olist ML training dataset, not derived from any real location field).

**Step 4 — ML baseline call** (`:76-77`; `MLClient.java:20-33`): `restTemplate.postForObject(mlServiceUrl + "/predict", request, MLResponse.class)` via a plain `new RestTemplate()` with **no timeout configured anywhere** (`RestTemplateConfig.java:11-13`) and **no retry/circuit-breaker** (no Resilience4j/Spring Retry dependency). On any exception or null/missing `predictedPrice`, throws `PricingException` (`MLClient.java:24-32`) — **uncaught**, propagates through `PricingServiceImpl`/`ProductServiceImpl.listProduct`. Since the `DRAFT` product row was already committed in the prior transaction, an ML outage leaves an **orphaned `DRAFT` product with no `PricingRequest` and no retry path** (no such method exists anywhere in `ProductService`).

**Step 5 — condition resolution** (`:83-90`): seller's `condition`/`conditionGrade`/`conditionNotes` fields win; LLM's condition guess is discarded.

**Step 6 — LLM Call 2 (pricing/confidence)** (`:93-100`; prompt `LLMClient.java:70-113`), quoted verbatim:

> You are a product pricing expert for a 2026 e-commerce marketplace.
> Return ONLY valid JSON, no markdown, no explanation.
>
> Product to price:
> - Description: "%s"
> - Brand: %s
> - Product type: %s
> - Specific model: %s
> - Condition: %s
> - Condition notes from seller: %s
> - ML physical baseline (Brazilian dataset, ignore for branded products): $%.2f
>
> Pricing instructions:
> - Use CURRENT 2026 market prices in USD for all known brands.
> - The ML baseline is only reliable for UNKNOWN brands and generic unbranded products. For any recognized brand, override it completely with real market knowledge.
> - Always return the CURRENT NEW RETAIL price for marketPriceMin and marketPriceMax.
> - Never apply condition discounts. Price every product as if it is brand new and sealed.
> - Condition is provided only so you can assess confidence level correctly.
> - The platform applies condition adjustments separately after you respond.
> - Be model-specific. iPhone 12 and iPhone 17 have very different prices. A 2019 laptop and a 2024 laptop are not the same price.
> - marketPriceMin must always be less than marketPriceMax.
> - Range width guide: 10-20% of midpoint for well-known products, up to 40% for vague or generic products.
>
> Confidence assignment:
> HIGH   → Brand is well-known AND specific model is identifiable AND condition is NEW or UNKNOWN
> MEDIUM → Brand is known BUT condition is USED or REFURBISHED, OR brand is known but model is vague/unclear, OR product is announced but not yet widely available
> LOW    → Brand is UNKNOWN, OR product is handmade/custom/one-of-a-kind, OR description is too vague to price reliably
>
> Return exactly this JSON:
> { "marketPriceMin": number (USD, never null for HIGH/MEDIUM), "marketPriceMax": number (USD, never null for HIGH/MEDIUM), "confidence": "HIGH" or "MEDIUM" or "LOW", "reasoning": "2-3 sentences: what product this is, what drives the price, and why this confidence level" }

Note: the prompt hardcodes "2026" — will read as stale/wrong text every subsequent year unless templated. On exception, `LLMClient.analyzePricing` (`:117-124`) returns `confidence="LOW", multiplier=1.0, reasoning="LLM unavailable"` — swallowed, never surfaced as an HTTP error, downgrades the pipeline to LOW confidence (→ ML baseline used, routed to admin review per Step 9).

**Step 7 — UNKNOWN-brand guard** (`:109-121`): if `brand` is `"UNKNOWN"`, **overrides whatever LLM Call 2 actually returned**, forcing `confidence=LOW`, `marketPriceMin/Max=null`, regardless of what the model said.

**Step 8 — combine ML + LLM into a suggested price** (`computeSuggestedPrice`, `:176-208`):
```java
boolean hasLLMRange = llm.getMarketPriceMin() != null && llm.getMarketPriceMax() != null;
double multiplier = getConditionMultiplier(condition, conditionGrade);
return switch (llm.getConfidence().toUpperCase()) {
    case "HIGH" -> {
        if (hasLLMRange) { double mid = (min+max)/2.0; yield mid * multiplier; }
        yield marketPriceMax != null ? marketPriceMax * multiplier
            : marketPriceMin != null ? marketPriceMin * multiplier
            : mlBaseline;
    }
    case "MEDIUM" -> {
        if (hasLLMRange) {
            double min = marketPriceMin * multiplier, max = marketPriceMax * multiplier, mid = (min+max)/2.0;
            if (mlBaseline >= min && mlBaseline <= max) yield mlBaseline;
            yield mid;
        }
        yield mlBaseline;
    }
    default -> mlBaseline;   // LOW
};
```
Condition multiplier table (`:210-217`), applied only to the LLM's new-retail figure, never to the ML baseline:
```java
if (condition == null) return 1.0;
return switch (condition.toUpperCase()) {
    case "USED" -> "HEAVY".equalsIgnoreCase(conditionGrade) ? 0.45 : 0.60;
    case "REFURBISHED" -> 0.65;
    default -> 1.0;
};
```
**NEW/UNKNOWN ×1.0, USED ×0.60, USED+HEAVY ×0.45, REFURBISHED ×0.65** — confirmed by unit tests (`900 mid × 0.45 = 405.0`, `700 mid × 0.65 = 455.0`).

Final price range is a flat **±10% of the suggested price** (`:125-126`): `minRange = round(suggested*0.90)`, `maxRange = round(suggested*1.10)`.

**Step 9 — routing decision** (`RoutingServiceImpl.determineStatus`, `:31-74`), 3 layers, first match wins:
1. **Redis cache** (`:36-53`): key `pricing:{brand}:{category}:{condition}:{bucket}`, bucket = `<200→budget, <500→mid, <1000→premium, else→luxury` (`:118-121`). If cached `min:max` exists and price falls inside → **`"PENDING_SELLER"`** immediately, skipping layers 2/3. Redis errors caught, fall through.
2. **Category bounds** (`:56-64`): `CategoryBoundsRepository.findByCategory`; if price is outside `[minBound,maxBound]` → **`"PENDING_ADMIN"`**.
3. **Confidence gate** (`:67-73`): `HIGH`/`MEDIUM` → `"PENDING_SELLER"`; else (`LOW`) → `"PENDING_ADMIN"`.

**Step 10 — ML-based sanity check ("business rules against ML" mechanism)** (`PricingServiceImpl.java:132-156`):
```java
double categoryAvgPrice = stats != null && stats.getAvgPrice() != null ? stats.getAvgPrice() : mlBaseline;
double priceRatio = categoryAvgPrice > 0 ? suggested / categoryAvgPrice : 1.0;
boolean suspiciousPrice = priceRatio > 50.0 || priceRatio < 0.1;
if (suspiciousPrice && !"LOW".equalsIgnoreCase(confidence)) status = "PENDING_ADMIN";
```
**Exact thresholds: 50× or 0.1× the category average price** (or the ML baseline itself if no stats exist). Only escalates HIGH/MEDIUM-confidence prices that are wildly off; LOW-confidence prices are already `PENDING_ADMIN`.

**Step 11 — cache write on approval** happens later, from `ProductServiceImpl.acceptPrice` or `AdminServiceImpl.doApproveTransaction`/`doOverrideTransaction`, always a **fixed ±10% band around the approved price, 30-day TTL** (`RoutingServiceImpl.java:76-82`).

### Product status lifecycle (real states, real transitions)

Real `ProductStatus`: `DRAFT, PENDING_REVIEW, LIVE, REJECTED, DELETED` (5 states — no `PENDING_ADMIN`/`PENDING_SELLER`, contra the GP proposal wording).

| From | To | Trigger | Method |
|---|---|---|---|
| — | `DRAFT` | seller submits listing | `ProductPersistenceHelper.saveDraftProduct:26` |
| `DRAFT` | `DRAFT` or `PENDING_REVIEW` | pipeline finishes: `"PENDING_SELLER"`→stays `DRAFT`; `"PENDING_ADMIN"`→`PENDING_REVIEW` | `ProductPersistenceHelper.finalizePricingRequest:44-47` (no `default` case) |
| `DRAFT` | `LIVE` | seller accepts price | `ProductServiceImpl.acceptPrice:113` |
| `DRAFT` | `PENDING_REVIEW` | seller disputes price | `ProductServiceImpl.disputePrice:150` |
| `PENDING_REVIEW` | `LIVE` | admin approves | `AdminServiceImpl.doApproveTransaction:159` |
| `PENDING_REVIEW` | `REJECTED` | admin rejects | `AdminServiceImpl.doRejectTransaction:195` |
| `LIVE` | `LIVE` (price only) | admin overrides | `AdminServiceImpl.doOverrideTransaction:212-221` |
| any | `DELETED` | seller deletes own product | `ProductServiceImpl.deleteProduct:276` |
| any | `DELETED` | admin deletes any product, **no precondition** | `AdminServiceImpl.deleteProduct:316` |

**No path back from `REJECTED`/`PENDING_REVIEW` to `DRAFT`** — a rejected product cannot be revised and resubmitted; no such method exists.

### `@Transactional` audit

**Correct**: `AuthServiceImpl` (all 3), `ProductPersistenceHelper` (both), `ProductServiceImpl.acceptPrice/disputePrice/getSellerProducts/getProductById/getDashboard/deleteProduct`, `CartServiceImpl`/`WishlistServiceImpl` (all), `BuyerServiceImpl` (all 7), `AdminServiceImpl.getPendingRequests/getRequestById/getAllProducts/getStats/deleteProduct`, `RoutingServiceImpl.determineStatus`, `CacheWarmupService.warmUpCache`.

**Broken (critical)**: `AdminServiceImpl.doApproveTransaction` (`:143`), `doRejectTransaction` (`:180`), `doOverrideTransaction` (`:207`) are **`private` methods annotated `@Transactional`**. Spring's proxy-based AOP cannot intercept private methods or self-invocation — calling `this.doApproveTransaction(...)` from the public `approveRequest` wrapper bypasses the proxy entirely. **The annotation has zero effect at runtime.** The multi-step writes inside (Product save + PricingRequest save + ApprovedDecision save + Redis cache write) execute with no atomic transaction boundary — a partial failure mid-method (e.g. the Redis write in `cacheApprovedRange` throwing after DB writes already succeeded) leaves the database in a non-atomic, non-rollback-able partial state. **This is the single most important correctness bug in the codebase.**

**Anti-pattern (annotated correctly, wrong thing inside)**: `ProductServiceImpl.uploadProductImages` (`:250`) — `@Transactional` while looping Cloudinary network calls inside the open transaction; `BuyerServiceImpl.placeOrder` — `@Transactional` while making a synchronous SMTP call inside (lower risk since `EmailServiceImpl.send` swallows exceptions, so it can't cause rollback, but still holds the connection during I/O).

**By design, correctly non-transactional**: `PricingServiceImpl.getSuggestion` — the one place with a comment-documented understanding of the transaction/external-I/O tension, which makes the unguarded cases above look like an inconsistently-applied lesson.

### Dead code / N+1 (service layer)

- `FeatureBuilderServiceImpl.sizeCategory` — never called.
- `CloudinaryService.deleteImage` — never called; deleted products/pictures leave orphaned Cloudinary assets forever.
- No `TODO`/`FIXME` comments anywhere under `service/**`.
- N+1: `ProductServiceImpl.getSellerProducts` (`:164-174`, per-product `findTopByProductOrderByCreatedAtDesc` call); `WishlistServiceImpl.getSaved` (plain `findByBuyer`, no join-fetch, unlike cart's equivalent).

---

## 4. Controllers / REST API Surface

**34 endpoints across 8 controllers.**

### AuthController — `/api/auth` (no class-level auth)
| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | /register | public | RegisterRequest (@Valid) | AuthResponse |
| POST | /login | public | LoginRequest (@Valid) | AuthResponse |
| POST | /refresh | public | RefreshRequest (@Valid) | AuthResponse |

### PricingController — `/api/pricing`
| POST | /suggest | `hasRole('SELLER')` | ProductListingRequest (@Valid) | PricingSuggestionResponse — a pricing preview/dry-run, separate from actually listing the product |

### CartController — `/api/buyer/cart`, class-level `hasRole('BUYER')`
POST `/{productId}` add · GET `` list (no pagination, bounded per-buyer) · DELETE `/{productId}` remove · DELETE `` clear-all.

### UserController — `/api/user`
POST `/profile-picture` — `isAuthenticated()`, multipart (no size/type check before the service call).

### WishlistController — `/api/buyer/wishlist`, class-level `hasRole('BUYER')`
Same CRUD shape as cart.

### ProductController — mixes `/api/products/...` and `/api/seller/...` prefixes in one class
| POST | /api/products | hasRole('SELLER') | ProductListingRequest (@Valid) | PricingSuggestionResponse — creates the product + kicks off pricing |
| POST | /api/products/{id}/accept | hasRole('SELLER') | AcceptPriceRequest, **`required=false`, no `@Valid`** | AcceptPriceResponse |
| POST | /api/products/{id}/dispute | hasRole('SELLER') | DisputePriceRequest (@Valid) | DisputeResponse |
| GET | /api/seller/products | hasRole('SELLER') | — | List — **no pagination**, unbounded per-seller catalog |
| GET | /api/products/{id} | hasRole('SELLER') | — | ProductResponse |
| GET | /api/seller/dashboard | hasRole('SELLER') | — | SellerDashboardResponse |
| POST | /api/products/{id}/images | hasRole('SELLER') | multipart, hand-rolled `files.size()>5` check (no @Valid) | List<String> |
| DELETE | /api/seller/products/{id} | hasRole('SELLER') | — | Map |

### BuyerController — no class-level `@RequestMapping`, full path repeated per method
| GET | /api/buyer/products | **public** | page/size params | Page<BuyerProductResponse> — paginated, default size 12 |
| GET | /api/buyer/products/{id} | public | — | BuyerProductResponse |
| GET | /api/buyer/products/{id}/history | **public**, **no pagination** | — | List<PriceHistoryResponse> — unbounded, grows over product lifetime |
| POST | /api/orders | hasRole('BUYER') | OrderRequest (@Valid) | OrderResponse |
| GET | /api/orders/my | hasRole('BUYER') | — | List — **no pagination**, unbounded order history |
| GET | /api/orders/{orderId} | hasRole('BUYER') | — | OrderResponse |

### AdminController — `/api/admin`
| GET | /requests | hasRole('ADMIN') | — | List — **no pagination at all, not even query params, platform-wide** — the single worst pagination gap in the codebase |
| GET | /requests/{id} | hasRole('ADMIN') | — | AdminRequestResponse |
| POST | /approve/{id} | hasRole('ADMIN') | ApproveRequest (@Valid) | Map |
| POST | /reject/{id} | hasRole('ADMIN') | RejectRequest (@Valid) | Map |
| POST | /override/{id} | hasRole('ADMIN') | OverrideRequest (@Valid) | Map |
| GET | /products | hasRole('ADMIN') | status/page/size | Page<AdminProductResponse> — paginated, default size 10 |
| GET | /stats | hasRole('ADMIN') | — | AdminStatsResponse |
| DELETE | /products/{id} | hasRole('ADMIN') | DeleteProductRequest, **`required=false`, no `@Valid`** | Map |

### DTO validation gaps

- **`AcceptPriceRequest.chosenPrice`** has `@Positive` but the controller omits `@Valid` — the constraint **never runs**; a negative `chosenPrice` reaches the service unchecked. This is the most actionable single fix.
- **`RegisterRequest.role`** is bound directly to the `Role` enum with only `@NotNull` — nothing at the DTO level stops a client from submitting `"ADMIN"` (the only guard is the service-layer downgrade in `AuthServiceImpl.register`, which is correct in practice but leaves the DTO itself permissive).
- **`ProductListingRequest`**: `name`/`description` have no upper `@Size` bound (unbounded payload risk); `category` is free-text `@NotBlank` with no enum/whitelist (typos silently miss category-bounds lookups downstream); `conditionNotes`/`conditionGrade` have zero constraints.
- **`OrderRequest.productId`**: `@NotNull` only, no `@Positive`/`@Min(1)`.
- Three request bodies skip `@Valid` entirely: `AcceptPriceRequest` (ProductController.acceptPrice), `DeleteProductRequest` (AdminController.deleteProduct — low impact, DTO has no constraints anyway). All `@Valid`-related annotations elsewhere are applied correctly.

### Pagination gaps (ranked by risk)

1. `GET /api/admin/requests` — **unbounded, platform-wide, zero params.** Highest risk.
2. `GET /api/buyer/products/{id}/history` — unbounded, public, grows per product.
3. `GET /api/orders/my` — unbounded per buyer.
4. `GET /api/seller/products` — unbounded per seller.
5. Cart/wishlist lists — unpaginated but naturally bounded (one user's cart/wishlist), low risk.

### Verb/path notes

No GET mutates state. Action-style URLs (`/accept`, `/dispute`, `/approve`, `/reject`, `/override`) are a consistent convention, not a bug. `BuyerController`'s lack of a class-level `@RequestMapping` and `ProductController`'s two-prefix mixing are style inconsistencies, not functional bugs.

---

## 5. Security

### JWT

`JwtUtil.java` — HMAC-SHA signing (`Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`), key sourced from `app.jwt.secret` (see §1's hardcoded-fallback finding). **Claims are only `subject` (email) + `issuedAt`/`expiration`** (`JwtUtil.java:56-62`) — **no role claim, no token-type claim, no `jti`**. Access and refresh tokens are structurally identical; the only difference is TTL (24h vs 7d, `application.properties:30-31`). Because there's no "type" claim, a leaked refresh token can be used directly as a Bearer access token for its full 7-day life. `isValid(token, email)` checks email match + non-expiry; signature verification happens implicitly inside `extractClaim`'s `Jwts.parser()` call (an invalid signature throws before `isValid` is reached). **No revocation/blacklist mechanism** — logout is client-side only.

**Refresh flow**: `AuthServiceImpl.refresh` (`:62-78`) validates the refresh token and issues a new access token, but **echoes back the same refresh token rather than rotating it** — no rotation, no reuse detection.

### Password hashing

`BCryptPasswordEncoder()` with default constructor → strength 10 (library default, not customized). Used in `AuthServiceImpl.register` (encode) and implicitly by `DaoAuthenticationProvider` at login.

### Filter chain & authorization rules

Order: `rateLimitingFilter` → `jwtAuthFilter` → `UsernamePasswordAuthenticationFilter` (registered but functionally unused — login goes through `AuthenticationManager.authenticate` directly in `AuthServiceImpl`, not this filter). CSRF disabled, session `STATELESS` — correct for pure-JWT auth.

`authorizeHttpRequests` rules, verbatim order (`SecurityConfig.java:37-49`):
```java
.requestMatchers("/api/auth/**", "/api/buyer/products", "/api/buyer/products/**",
                  "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
.requestMatchers("/actuator/health").permitAll()
.requestMatchers(HttpMethod.POST, "/api/user/profile-picture").authenticated()
.requestMatchers(HttpMethod.POST, "/api/products/*/images").hasRole("SELLER")
.anyRequest().authenticated()
```
- `/api/buyer/products/**` is `permitAll()` for **all HTTP methods**, not just GET. Confirmed against the controller table: only GETs live under that prefix today, so this is not currently exploitable, but the rule itself doesn't enforce that constraint — a future mutating endpoint added under this prefix would be public by default.
- Swagger/OpenAPI fully public in every environment, no profile gating — fine for a GP demo, a real exposure surface if shipped to production as-is.
- **Role separation beyond the one explicit `hasRole("SELLER")` image-upload rule is entirely delegated to `@PreAuthorize` at the controller/method level** — `SecurityConfig` provides no defense-in-depth for `/api/admin/**`; a missing `@PreAuthorize` on any future admin method would silently downgrade it to "any authenticated user." (Cross-checked against §4's endpoint table: every current admin/seller-restricted method does carry an explicit `hasRole(...)`, so there is no live gap today — but the architecture has no second layer of protection.)
- `JwtAuthFilter` silently swallows `JwtException` (`catch (JwtException ignored) {}`) and falls through unauthenticated — correct so `permitAll()` routes still work with a garbage Bearer header, but it means `GlobalExceptionHandler`'s `JwtException` mapping (`:44-47`) is **effectively dead code for the filter path** — a malformed token on a protected route gets Spring Security's generic 401, not this handler's custom message. The handler only fires for a `JwtException` thrown elsewhere, e.g. `AuthServiceImpl.refresh`'s unguarded `jwtUtil.extractEmail(refreshToken)` call.

### Role enum

`Role.java`: `BUYER, SELLER, ADMIN` — exactly 3, no sub-roles/scopes, one authority per user. Self-registration as ADMIN is explicitly blocked in `AuthServiceImpl.register` (`:35-38`).

### CORS

**No CORS configuration exists anywhere** — no `CorsConfigurationSource` bean, no `.cors(...)` call, no `@CrossOrigin` annotation (whole-tree grep, zero matches). Spring Security's default (CORS not enabled) applies. If the GP frontend is a separately-hosted SPA on a different origin, this will manifest as browser CORS errors unless something in front of the app (reverse proxy/gateway) adds the headers — this is a **MISSING** item, not a misconfiguration, since nothing attempts it at all.

### GlobalExceptionHandler

`@RestControllerAdvice`, uniform error body `{timestamp, status, message}` except validation errors (which get a `{timestamp, status, errors:{field:msg}}` shape):

| Exception | Status | Message |
|---|---|---|
| `EmailAlreadyExistsException` | 409 | `ex.getMessage()` |
| `TokenRefreshException` | 401 | `ex.getMessage()` |
| `BadCredentialsException` | 401 | hardcoded `"Invalid email or password"` |
| `ResourceNotFoundException` | 404 | `ex.getMessage()` |
| `PricingException` | 503 | `ex.getMessage()` |
| `JwtException` | 401 | hardcoded `"Invalid or expired token"` (mostly dead path, see above) |
| `IllegalArgumentException` | 400 | `ex.getMessage()` |
| `IllegalStateException` | 400 | `ex.getMessage()` |
| `MethodArgumentNotValidException` | 400 | field-error map |
| `AccessDeniedException` | 403 | hardcoded `"Access denied"` |
| `HttpRequestMethodNotSupportedException` | 405 | `"HTTP method not supported: " + method` |
| `Exception` (catch-all) | 500 | hardcoded `"An unexpected error occurred"` |

Gaps: no handler for `HttpMessageNotReadableException` (malformed JSON → falls to generic 500 instead of 400) or `DataIntegrityViolationException` (constraint violations → 500). Broad `IllegalArgumentException`/`IllegalStateException` → 400 mapping means any non-client-input-related use of these (framework/library internals) would mislabel a server bug as a 400.

### RateLimitingFilter

In-memory, per-`(method+path+client-IP)` fixed-window counter (`ConcurrentHashMap<String,Window>`, `AtomicInteger` count, `synchronized` mutation). Exact-match rule table, only 4 covered routes:
```java
"POST:/api/auth/login"      -> 5 req / 60s
"POST:/api/auth/register"   -> 5 req / 60s
"POST:/api/products"        -> 10 req / 60s
"POST:/api/pricing/suggest" -> 5 req / 60s
```
**Client IP resolution trusts `X-Forwarded-For` verbatim with no validation that the request came through a trusted proxy** — any client can spoof this header to reset their own bucket, trivially bypassing the limiter, unless a trusted reverse proxy strips/overwrites it upstream (nothing in this codebase enforces that). On limit exceeded: 429 + `Retry-After` header + JSON body, written directly (bypasses `GlobalExceptionHandler` since this filter runs pre-`DispatcherServlet`). **The class's own doc comment self-flags** that this is single-instance/in-memory and won't hold under horizontal scaling — confirmed accurate, no Redis backing despite Redis being available elsewhere in the stack. Ordering (rate-limit before JWT auth) is correct.

---

## 6. External Integrations

### FastAPI ML microservice (`MLClient.java`)

Called via a bare `new RestTemplate()` (`RestTemplateConfig.java:11-13`) — **no connect/read timeout configured at all, no retry, no circuit breaker**. URL from `${ml.service.url}` (default `http://localhost:8000`), single hardcoded path `/predict`, no versioning. Any failure (refused connection, malformed response, or non-2xx) is wrapped into `PricingException` and propagated uncaught (`MLClient.java:24-32`) — **no fallback/degraded price exists**. **If the service hangs rather than refuses**, the calling thread blocks indefinitely — this is the most severe operational risk in the integration layer, since there's no timeout to fail fast into the existing exception-handling path.

### OpenAI / Spring AI

`SpringAIConfig.java` — trivial `chatClient(builder) -> builder.build()`, no custom options at the Java-config level; model (`gpt-4o-mini`) and temperature (`0.1`) come from properties. Both prompts quoted in full in §3 above. LLM Call 1 failure → graceful fallback (`brand=UNKNOWN`); LLM Call 2 failure → graceful fallback (`confidence=LOW, reasoning="LLM unavailable"`) — both swallowed internally in `LLMClient`, never surfaced as HTTP errors. Both methods share a `clean(String raw)` helper that strips markdown code fences before JSON parsing, defensive against the model ignoring the "no markdown" instruction.

### Redis

Manual caching only — **no `@Cacheable`/`@CacheEvict` anywhere**; all logic lives in `RoutingServiceImpl` via `StringRedisTemplate`. Key format: `pricing:{brand}:{category}:{condition}:{bucket}`, value is a `"min:max"` string representing a previously-approved price range. **TTL: flat 30 days**, no sliding expiry. Write logic computes `min = approved*0.90, max = approved*1.10`. **No eviction/invalidation logic exists at all** — if an admin later rejects a brand/category/condition/bucket combo that was previously cached as approved, the stale cache entry is not invalidated and keeps fast-tracking matching listings to `PENDING_SELLER` until natural 30-day expiry. Both cache-read paths treat a Redis exception as a cache miss (Redis is explicitly optional, comment-documented). **Warmup**: `CacheWarmupService`, `@EventListener(ApplicationReadyEvent.class)` (not `@PostConstruct`), loads all `ApprovedDecision` rows unpaged and repopulates Redis from the durable source of truth — fully recoverable on Redis loss, but always keys warmed entries under the `unknown` condition bucket (see §3 finding).

### Cloudinary

Synchronous upload, no queue. Profile picture: fixed 300×300 face-aware crop, deterministic `public_id` (`user_<id>`, overwrite=true). Product image: original resolution, `public_id = product_<id>_img_<index>` (supports multiple images). **Only `IOException` is caught** and rethrown as a bare `RuntimeException` (message-only, original exception not chained as `cause` — stack trace context lost at the rethrow site); any non-`IOException` failure from the Cloudinary SDK (auth error, rate limit) propagates uncaught and unhandled. `deleteImage` exists but is never called (§3) — deleted products/pictures leave orphaned Cloudinary assets permanently.

### Brevo SMTP (Email)

5 trigger events, all via `EmailServiceImpl`, all funneling into a shared `send()` that **catches all exceptions and only logs** — failures are always silently swallowed, no retry, no dead-letter queue:
1. `sendApprovalEmail` — "Your product has been approved! ✅"
2. `sendRejectionEmail` — "Your product listing was not approved" (includes acceptable price range)
3. `sendOverrideEmail` — "Your product price has been updated" (old price struck through, new highlighted)
4. `sendOrderConfirmationEmail` — "Order confirmed — {productName}"
5. `sendProductDeletedEmail` — "Your product listing has been removed — DynaMart"

`sendProductDeletedEmail`'s `reason` field is concatenated into the HTML body via **raw string concatenation with no HTML-escaping** (`"<td>" + reason + "</td>"`) — a stored-XSS-adjacent risk in an email client if admin-supplied free text ever contains `<`/`>`/`&` (low severity since only admins can trigger it, but a real gap for any future front-end-driven admin note field).

---

## 7. Error Handling & Validation

Covered in full in §5 (GlobalExceptionHandler table) and §4 (per-DTO validation audit). Summary of the two biggest gaps:
- `AcceptPriceRequest`'s `@Positive` constraint on `chosenPrice` never runs because the controller omits `@Valid`.
- No handler for malformed-JSON (`HttpMessageNotReadableException`) or DB constraint violations (`DataIntegrityViolationException`) — both fall through to a generic 500.

---

## 8. Cross-Check Against GP Proposal Requirements

| Requirement | Verdict | Evidence |
|---|---|---|
| Price prediction requests routed to ML microservice via REST | **PASS** | `MLClient.predict` (`MLClient.java:20-33`): `restTemplate.postForObject(mlServiceUrl+"/predict", request, MLResponse.class)`. |
| System validates/adjusts/rejects ML-generated prices against business rules before use | **PASS** | Category-bounds check (`RoutingServiceImpl.java:56-64`), confidence gate (`:67-73`), and the 50×/0.1×-category-average sanity check (`PricingServiceImpl.java:138-150`) all override/escalate the routing decision. |
| Final approved price persisted; every prediction event logged (inputs, predicted value, rules applied) for audit | **PARTIAL** | `PricingRequest` persists inputs/outputs (`suggestedPrice`, `brand`, `llmConfidence`, `mlBaselinePrice`, `marketPriceMin/Max`, `condition`, `reasoning`) — solid structured data. But **`PricingHistory` (the entity that structurally looks purpose-built for an old→new price audit timeline) is never written to** (§2, §3), and the *routing reason* (which of the 3 layers fired, or whether the ML-sanity-check override fired) is never persisted — only reconstructed heuristically and *differently* later in `AdminServiceImpl.toAdminResponse` (`:272-281`, which doesn't even know about the sanity-check path). "Rules applied" is not fully auditable after the fact. |
| Admin can view products, trigger predictions, manually override/approve prices | **PARTIAL** | View/override/approve all PASS (`AdminServiceImpl.getAllProducts/.overridePrice/.approveRequest`). **No admin-initiated re-prediction endpoint exists** — predictions only happen automatically at seller listing time; there's no method anywhere to trigger a fresh prediction for an existing product. |
| Predictions generated within a few seconds (near real-time) | **PARTIAL/unverifiable from code** | 2 sequential LLM calls + 1 ML call per request, no cache hit; no timeout configured on the ML `RestTemplate` means a hung ML service has no upper bound at all, not just "slow." Cache-first check (Step 2) mitigates repeat cases. |
| Graceful handling of microservice failures, data integrity maintained | **PARTIAL** | LLM failures are fully graceful (caught, fallback DTOs). **ML failures are not graceful for data integrity**: `PricingException` propagates uncaught, and since the `DRAFT` product row is already committed by a prior transaction, an ML outage leaves an orphaned `DRAFT` product with no `PricingRequest` and no retry path. |
| Auth/authorization restricts admin and backend operations to authorized users | **PASS** (with one architectural caveat) | Every current admin/seller endpoint carries an explicit `@PreAuthorize`/`hasRole`; self-registration as ADMIN is explicitly blocked. Caveat: `SecurityConfig` itself provides no role-based defense-in-depth beyond one rule — protection for `/api/admin/**` depends entirely on controller-level annotations being present and correct, with no central enforcement. |
| Architecture supports independent evolution/redeployment of ML service and backend | **PASS** | `MLClient`/`LLMClient` are the only two external integration points, both behind service interfaces, URL fully externalized (`${ml.service.url}`). |

---

## 9. Code Quality & Risk Flags

**Dead code:**
- `entity/PricingHistory.java` + `PricingHistoryRepository` — table/entity/repo exist, injected, never called (§2).
- `FeatureBuilderServiceImpl.sizeCategory()` — never called.
- `CloudinaryService.deleteImage()` — never called; deleted media is never actually removed from Cloudinary storage.
- `RoutingServiceImpl.findCachedRange` may be effectively single-purpose/near-dead outside the one early-cache-check call site — confirmed used only by `PricingServiceImpl`'s Step 2, not elsewhere.

**TODO/FIXME**: none found anywhere in `service/**` (grepped, zero matches); none reported by any other fork either.

**Hardcoded values that should be config / magic numbers:**
- `application.properties`: real-looking default secrets for JWT signing key, DB password, Brevo SMTP creds, Cloudinary API key/secret (all `${ENV_VAR:realDefault}` pattern — §1).
- `# Transaction boundary tracing (added for verify run — remove after)` comment above two DEBUG/TRACE logging lines in `application.properties` that were never cleaned up.
- `FeatureBuilderServiceImpl`: hardcoded `customerState=sellerState="SP"`, `weight` fallback `500.0`, `paymentTypeMode` fallback `"credit_card"`, and several other numeric defaults (`100.0`, `4.0`, `50`, `12`) that stand in for missing category/seller stats.
- LLM prompt hardcodes the literal year "2026" — will read as stale text in future years.
- `data.sql`'s 9 unconditional `UPDATE category_bounds` statements silently reset any manual admin tuning of those categories on every restart (`spring.sql.init.mode=always`).
- `RoutingServiceImpl.priceBucket` thresholds (200/500/1000) are magic numbers with no named constants.
- `PricingServiceImpl`'s ±10% price-range band and the 50×/0.1× sanity-check thresholds are inline literals, not named constants or config values.

**N+1 query risks**: `ProductServiceImpl.getSellerProducts` (per-product pricing-request lookup), `WishlistServiceImpl.getSaved` (no join-fetch, unlike the cart equivalent) — both detailed in §3.

**Missing/broken transactions**: `AdminServiceImpl`'s three private `@Transactional` methods (critical, §3); `ProductServiceImpl.uploadProductImages` and `BuyerServiceImpl.placeOrder` hold DB connections open during external I/O (§3).

**Would break in multi-instance/production deployment:**
- `RateLimitingFilter` — in-memory `ConcurrentHashMap`, self-documented as broken across horizontally-scaled instances; also trusts unvalidated `X-Forwarded-For`.
- `spring.jpa.hibernate.ddl-auto=update` — no migration tool (Flyway/Liquibase); schema drift risk across environments/deployments.
- No `@Version` optimistic locking on any entity, `Product` in particular, despite concurrent mutation by seller/admin/pricing-pipeline.
- No CORS configuration — will surface as browser errors the moment frontend and backend are on different origins with no proxy in between.
- `CacheWarmupService` and `AdminController.getPendingRequests` both call unpaged `findAll()`-style repository methods that will degrade as `ApprovedDecision`/`PricingRequest` tables grow.

**Missing pagination**: `GET /api/admin/requests` (worst case — platform-wide, zero params), `GET /api/buyer/products/{id}/history`, `GET /api/orders/my`, `GET /api/seller/products` (§4).

**Rate limiting / API versioning / Swagger — current state:**
- Rate limiting: implemented (`RateLimitingFilter`), but in-memory-only and IP-spoofable — not production-hardened, no TODO comment marking it as such (it just self-documents the limitation in its Javadoc).
- API versioning: **none** — no `/v1/` prefix or `Accept`-header versioning anywhere; all paths are unversioned.
- Swagger/OpenAPI: **fully implemented and live** (`springdoc-openapi-starter-webmvc-ui` 2.6.0, publicly exposed at `/swagger-ui/**` and `/v3/api-docs/**` with no profile gating) — this is further along than a "pending" state, contrary to what a GP-stage project might be assumed to have.

---

## 10. Summary

**Is this production-ready or GP-demo-ready?** This is a solid, coherent **GP-demo-ready** backend with genuinely well-thought-out pricing logic (the two-LLM-call + ML-baseline + three-layer routing + sanity-check design is more sophisticated than most student projects attempt), but it is **not production-ready**, and the gap is concentrated in exactly the places that don't show up in a demo: transaction correctness under partial failure, a single-instance-only rate limiter, no schema migration tool, no CORS, real-looking default secrets sitting in a config file, and an ML integration with no timeout that can hang a request thread forever. None of these would surface in a supervised demo walkthrough; all of them would surface under real concurrent traffic or a flaky network.

**Top 10 fixes, ranked by risk × effort (cheapest/highest-impact first):**

1. **Make the admin approve/reject/override transactions real** — move `doApproveTransaction`/`doRejectTransaction`/`doOverrideTransaction`'s logic into the public methods (or make them non-private and call through a self-injected proxy/separate bean) so `@Transactional` actually applies. Highest risk (silent data corruption on partial failure), low effort.
2. **Add a timeout (and ideally one retry) to the `RestTemplate` used for `MLClient`** — a hung ML service currently blocks a request thread forever. High risk, very low effort (a few lines in `RestTemplateConfig`).
3. **Add `@Valid` to `ProductController.acceptPrice`'s `AcceptPriceRequest` parameter** so the existing `@Positive` constraint runs. Trivial effort, closes a real input-validation hole.
4. **Remove hardcoded fallback secrets from `application.properties`**, switch to `${ENV_VAR}` with no default (fail fast if unset) for JWT secret, DB password, Brevo creds, Cloudinary keys. Medium risk if the file ever escapes `.gitignore`, low effort.
5. **Paginate `GET /api/admin/requests`** — currently the only fully unbounded, platform-wide list endpoint with zero query params. Low effort, prevents a real future outage as request volume grows.
6. **Replace the in-memory `RateLimitingFilter` with a Redis-backed one** (Redis is already in the stack) and stop trusting raw `X-Forwarded-For` without proxy validation. Medium effort, needed before any multi-instance deployment.
7. **Add CORS configuration** (a `CorsConfigurationSource` bean) if the frontend will ever be served from a different origin than the backend. Low effort, currently entirely absent.
8. **Add a migration tool (Flyway/Liquibase)** and turn off `ddl-auto=update` before any real deployment — schema drift across environments is currently unmanaged.
9. **Add `@Version` optimistic locking to `Product`** given it's mutated concurrently by seller, admin, and the pricing pipeline with no conflict detection today.
10. **Decide what to do with `PricingHistory`** — either wire it up to actually log old→new price transitions (closing the audit-trail gap in the GP proposal's non-functional requirements) or remove the dead entity/table/repository entirely. Low effort either way, currently it's neither.

**Most surprising findings versus the project's own documentation:**
- The actual product lifecycle has only one pending state (`PENDING_REVIEW`) — `PENDING_ADMIN`/`PENDING_SELLER` are response-DTO strings, never persisted `ProductStatus` values, contrary to how the lifecycle is described.
- The project is Java 21, not Java 23.
- `PricingHistory` — the entity that most closely matches "audit trail for price changes" — is completely dead code; the actual audit data lives in a differently-shaped table (`PricingRequest`).
- Commit `c9b22c7` claims to have externalized secrets to env vars, but every one of those properties still ships a real-looking hardcoded default value, so the fix is only as strong as `.gitignore` never lapsing.
- Swagger/OpenAPI docs are fully implemented and publicly live — further along than a typical "pending" doc item, and public in every environment with no profile gating.
- The LLM pricing prompt explicitly tells the model to ignore condition/used-item discounting ("The platform applies condition adjustments separately after you respond") — confirming condition-based pricing is deliberately kept out of the LLM's hands and handled entirely by the deterministic multiplier table in `PricingServiceImpl`, a subtlety worth noting for anyone assuming the LLM sets the final price.
