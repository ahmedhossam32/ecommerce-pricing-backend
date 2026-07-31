# Audit: Project Overview & External Integrations

## 1. Project overview

**Package structure** (`com.ecommerce`, single Maven module): `client/` (LLMClient, MLClient — outbound HTTP wrappers), `config/` (Security, JWT filter, rate limiting, RestTemplate, CloudinaryConfig, SpringAIConfig), `controller/` (8 REST controllers), `dto/request` + `dto/response` (13 request DTOs, 15 response DTOs), `entity/` (10 JPA entities), `enums/` (5 enums: AuthProvider, Condition, PricingRequestStatus, ProductStatus, Role), `exception/` (custom exceptions + GlobalExceptionHandler), `repository/` (10 Spring Data JPA repos), `service/` split into 8 sub-packages by domain (admin, auth, buyer, cart, pricing, product, upload, user, wishlist), `util/JwtUtil`. Standard layered architecture, no modularization beyond packages (single deployable jar).

**Build tool**: Maven (`pom.xml`, `spring-boot-starter-parent` 3.3.5). **Java version**: `<java.version>21</java.version>` (pom.xml:21) — note the user's prompt describes this as "Java 23"; the actual configured/compiled version is **Java 21**, and the Dockerfile also builds/runs on `eclipse-temurin:21` (Dockerfile:2,10). This is a doc/prompt vs. code mismatch worth flagging back.

**Dependencies (from pom.xml, with versions)**:
| Group | Artifact | Version | Purpose |
|---|---|---|---|
| org.springframework.boot | spring-boot-starter-web | (parent-managed, 3.3.5) | REST/MVC |
| org.springframework.boot | spring-boot-starter-data-jpa | 3.3.5 | JPA/Hibernate |
| org.postgresql | postgresql | 3.3.5-managed | DB driver, runtime scope |
| org.springframework.boot | spring-boot-starter-security | 3.3.5 | Security |
| org.springframework.boot | spring-boot-starter-oauth2-client | 3.3.5 | OAuth2 client (present but — see note below, no evidence of active use found in this fork's scope) |
| org.springframework.boot | spring-boot-starter-validation | 3.3.5 | Bean Validation (Jakarta) |
| org.springframework.ai | spring-ai-starter-model-openai | 1.0.0 (via spring-ai-bom) | Spring AI OpenAI ChatClient |
| org.projectlombok | lombok | parent-managed, optional | Codegen |
| org.springframework.boot | spring-boot-devtools | runtime, optional | Dev reload |
| org.springframework.boot | spring-boot-docker-compose | runtime, optional | Docker Compose support (disabled — see §2) |
| org.springdoc | springdoc-openapi-starter-webmvc-ui | **2.6.0** | Swagger/OpenAPI UI |
| org.springframework.boot | spring-boot-starter-test | test | JUnit/Mockito |
| org.springframework.security | spring-security-test | test | Security test support |
| io.jsonwebtoken | jjwt-api / jjwt-impl / jjwt-jackson | **0.12.6** | JWT (impl/jackson are runtime scope) |
| org.springframework.boot | spring-boot-starter-data-redis | 3.3.5 | Redis (Lettuce client under the hood) |
| org.springframework.boot | spring-boot-starter-mail | 3.3.5 | SMTP (Brevo) |
| org.springframework.boot | spring-boot-starter-actuator | 3.3.5 | Health/metrics |
| com.cloudinary | cloudinary-http44 | **1.36.0** | Image upload |

`spring-ai-bom` 1.0.0 is imported via `dependencyManagement` (pom.xml:164-174). Repository block adds `repo.spring.io/milestone` (pom.xml:206-213) but snapshots disabled — consistent with spring-ai 1.0.0 being a GA release, not a milestone; the milestone repo may now be vestigial.

**oauth2-client note**: the dependency is declared but nothing in the files this fork read (SecurityConfig is another fork's territory) confirms active OAuth2 login usage — flagging as a possible unused dependency for cross-check against the security fork's findings.

## 2. `application.properties` / `application.yml` — full property list

Only `.properties` format is used; there is **no `application.yml`**. Two files exist:
- `src/main/resources/application.properties` — the **real, gitignored, local dev config** (confirmed via `.gitignore`: lines list `src/main/resources/application.properties` and `.env` explicitly — so this file is **not committed to git**, only its `.example` sibling is tracked).
- `src/main/resources/application.properties.example` — committed template with placeholder values (`YOUR_DB_USERNAME`, `YOUR_JWT_SECRET_KEY_HERE`, etc.) — clean, no real secrets.

Full property breakdown of the real `application.properties`:

| Property | Value / source | Notes |
|---|---|---|
| `spring.application.name` | `pricing-engine` | static |
| `server.port` | `8080` | static |
| `spring.datasource.url` | `jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:ecommerce_gp}` | env-overridable, `localhost`/`ecommerce_gp` fallback |
| `spring.datasource.username` | `${DB_USERNAME:postgres}` | env-overridable |
| `spring.datasource.password` | `${DB_PASSWORD:postgres123}` | **hardcoded fallback password** `postgres123` — low-severity since local-only, but still a real-looking literal credential committed to a config file (file itself is gitignored, so not leaked to the repo — but pattern is risky, see below) |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | static |
| `spring.jpa.hibernate.ddl-auto` | `update` | **auto-migrates schema on every boot** — no Flyway/Liquibase; risky for prod (schema drift, no migration history) |
| `spring.jpa.show-sql` / `hibernate.format_sql` | `true` | dev-only verbosity left enabled |
| `logging.level.org.hibernate.SQL` | `DEBUG` | dev-only verbosity |
| `spring.jpa.properties.hibernate.default_batch_fetch_size` | `20` | batching helps but doesn't replace `JOIN FETCH` for true N+1 fixes |
| `spring.jpa.properties.hibernate.dialect` | `PostgreSQLDialect` | static |
| `spring.sql.init.mode` | `always` | `data.sql` re-runs every boot (idempotent via `ON CONFLICT DO NOTHING`) |
| `spring.jpa.defer-datasource-initialization` | `true` | ensures Hibernate DDL runs before `data.sql` |
| `spring.data.redis.host` / `.port` | `${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}` | env-overridable |
| `spring.docker.compose.enabled` | `false` | Docker Compose auto-detection explicitly disabled |
| `app.jwt.secret` | `${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}` | **hardcoded fallback JWT signing key** — looks like a real hex-encoded secret (64 hex chars = 32 bytes), used as default if `JWT_SECRET` env var is absent. If this default is ever used in production, tokens are forgeable by anyone who reads this repo/history. |
| `app.jwt.expiration` | `86400000` (ms = 24h) | access token TTL |
| `app.jwt.refresh-expiration` | `604800000` (ms = 7 days) | refresh token TTL |
| `spring.ai.openai.api-key` | `${OPENAI_API_KEY}` | **no fallback default** — good, will fail fast/null if unset |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | static |
| `spring.ai.openai.chat.options.temperature` | `0.1` | static, low-temperature deterministic-ish output |
| `ml.service.url` | `${ML_SERVICE_URL:http://localhost:8000}` | env-overridable, localhost fallback |
| `spring.mail.host` | `smtp-relay.brevo.com` | static |
| `spring.mail.port` | `587` | static |
| `spring.mail.username` | `${BREVO_SMTP_USERNAME:ac5f92001@smtp-brevo.com}` | **hardcoded fallback — looks like a real Brevo SMTP login** |
| `spring.mail.password` | `${BREVO_SMTP_PASSWORD:bskDNKXywdW6Ay8}` | **hardcoded fallback — looks like a real Brevo SMTP password/API key** |
| `spring.mail.properties.mail.smtp.auth` / `starttls.enable` | `true` | static |
| `spring.mail.from` | `noreply@dynamart.me` | static, real domain |
| `management.endpoints.web.exposure.include` | `health` | only health actuator exposed — reasonable |
| `spring.servlet.multipart.max-file-size` | `10MB` | static |
| `spring.servlet.multipart.max-request-size` | `50MB` | static |
| `logging.level.org.springframework.transaction.interceptor` | `TRACE` | **left-in debug tracing** — see comment on the line above it |
| `logging.level.com.zaxxer.hikari.pool.HikariPool` | `DEBUG` | **left-in debug tracing** |
| `cloudinary.cloud-name` | `${CLOUDINARY_CLOUD_NAME:dnqp6wte7}` | hardcoded fallback, real-looking cloud name |
| `cloudinary.api-key` | `${CLOUDINARY_API_KEY:184965579373373}` | **hardcoded fallback — looks like a real Cloudinary API key** |
| `cloudinary.api-secret` | `${CLOUDINARY_API_SECRET:mMx-ItQp1YA50qTrPd4rNLdxuGc}` | **hardcoded fallback — looks like a real Cloudinary API secret** |

**Notable self-documenting smell**: line 56 literally contains the comment `# Transaction boundary tracing (added for verify run — remove after)` immediately above the two DEBUG/TRACE logging lines (application.properties:56-58) — confirms these were added for a one-off debugging session and never removed. Flag for cleanup.

**Secret-handling verdict**: the file with real secrets is correctly gitignored and never committed (verified via `.gitignore` + `git ls-files`), so there is **no active secret leak in the git history for this file**. However, every sensitive property uses the `${ENV_VAR:realLookingDefault}` pattern instead of `${ENV_VAR}` with no default — meaning if the file is ever accidentally force-added, copied to another environment, or the gitignore rule is dropped, real credentials go with it. Recommend removing the literal fallback values entirely (fail fast if env var missing) rather than relying solely on gitignore.

The `.example` file is clean — all placeholders, no real values, and is the one committed to git (`git ls-files` confirms only `application.properties.example` is tracked, not `application.properties`).

`.example` file also differs from the real file in a few ways worth flagging as **doc drift** (this file is meant to model the real one for onboarding):
- Real file uses Brevo SMTP (`smtp-relay.brevo.com`); `.example` still says `smtp.gmail.com` with `YOUR_GMAIL_APP_PASSWORD` — **stale**, doesn't reflect actual provider in use.
- `.example` includes `springdoc.swagger-ui.path=/swagger-ui.html` (line 41) which is **absent from the real properties file** — meaning Swagger UI path customization documented in the example isn't actually applied; real app uses SpringDoc's default path.
- `.example` has no equivalent of the JWT hex-secret default, transaction tracing lines, or the multipart size limits — those are prod-file-only additions not reflected back into the template.

## 3. Docker/config files present

Only one: **`Dockerfile`** (repo root). Multi-stage build:
- Stage 1 (`build`): `maven:3.9-eclipse-temurin-21`, copies `pom.xml`, runs `mvn dependency:go-offline`, copies `src`, runs `mvn clean package -DskipTests` (Dockerfile:1-7). Tests are explicitly skipped in the image build.
- Stage 2 (`run`): `eclipse-temurin:21-jre` (slim JRE-only runtime), copies the built jar, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","app.jar"]` (Dockerfile:9-14).

No `docker-compose.yml` found in the repo despite `spring-boot-docker-compose` being a pom dependency (and explicitly disabled via `spring.docker.compose.enabled=false`) — the dependency appears vestigial/unused without a compose file to drive it. No `.dockerignore` found (not checked exhaustively, wasn't in scope grep, but worth another fork/pass noting if `target/` or `.git` could bloat the build context).

No Kubernetes manifests, no `.env.example` (env-style secrets are instead modeled via `application.properties.example`), no CI/CD config files (no `.github/workflows` observed in this fork's scope).

`src/main/resources/data.sql` is a seed script (69 category rows inserted into `category_stats` with `ON CONFLICT (category) DO NOTHING`, plus 9 `UPDATE category_bounds SET max_price = ...` statements hardcoding luxury-category price ceilings for fashion/electronics/computers/telephony/audio, data.sql:76-85). This runs on **every startup** (`spring.sql.init.mode=always`) — the inserts are idempotent (upsert-safe) but the `UPDATE ... WHERE category = ...` statements are unconditional overwrites; if an admin manually tunes `category_bounds.max_price` for one of these 9 categories in prod, **it will be silently reset to the hardcoded value on every restart**. This is a real operational trap.

## 4. FastAPI ML service integration (`MLClient.java`)

- Called via a plain Spring `RestTemplate` (`RestTemplateConfig.java:11-13` — `new RestTemplate()`, no custom `ClientHttpRequestFactory`, **no connect/read timeout configured at all**, meaning a hung/slow ML service could block the calling thread indefinitely rather than failing fast).
- URL: injected via `@Value("${ml.service.url}")` (MLClient.java:15), env-overridable, defaults to `http://localhost:8000` when unset.
- Call: `restTemplate.postForObject(mlServiceUrl + "/predict", request, MLResponse.class)` (MLClient.java:22-23) — single hardcoded endpoint path `/predict`, no versioning.
- **No retry logic whatsoever** — one attempt, no backoff, no circuit breaker (no Resilience4j/Spring Retry dependency in pom.xml).
- Failure handling (MLClient.java:24-32):
  ```java
  if (response == null || response.getPredictedPrice() == null) {
      throw new PricingException("ML service returned an empty response");
  }
  ...
  } catch (PricingException e) {
      throw e;
  } catch (Exception e) {
      throw new PricingException("ML service unavailable: " + e.getMessage());
  }
  ```
  Any failure (connection refused, timeout — if it ever timed out, which it won't with the default infinite-wait `RestTemplate` — malformed response, non-2xx) is wrapped into a `PricingException` and propagated up. There is **no fallback price**, no default/degraded pricing path — if the ML service is down, the entire pricing pipeline throws and (per the exception-handling fork's territory) presumably surfaces as an error response to the caller. **If the ML service simply doesn't respond at all** (network black hole rather than refused connection), the request thread hangs with no timeout — this is the most severe operational risk in this integration.

## 5. OpenAI / Spring AI integration

- Bean: `SpringAIConfig.java:10-13` — `chatClient(ChatClient.Builder builder) { return builder.build(); }` — trivial, no custom `defaultSystem()`, no custom `defaultOptions()` override at the Java-config level; model/temperature come purely from `application.properties` (`spring.ai.openai.chat.options.model=gpt-4o-mini`, `spring.ai.openai.chat.options.temperature=0.1`).
- **Model**: `gpt-4o-mini`. **Temperature**: `0.1` (low, near-deterministic).
- Two distinct LLM calls, both in `LLMClient.java`:

  **LLM Call 1 — `extractProductInfo(String description)`** (LLMClient.java:18-63). Exact prompt (verbatim, `.formatted(description)` at line 49):
  > "You are a product information extractor for an e-commerce platform. Extract structured facts from this product description. Return ONLY valid JSON, no markdown, no explanation. Product description: "%s" Rules: - brand: The most prominent brand name. Use "UNKNOWN" if none found. Never null. - condition: Classify as exactly one of: "NEW" → described as new, sealed, brand new, unopened, never used "USED" → described as used, second hand, secondhand, pre-owned, previously owned, gently used, worn, minor scratches, good condition, fair condition, like new, open box "REFURBISHED" → described as refurbished, restored, reconditioned, certified pre-owned "UNKNOWN" → no condition mentioned (assume new retail listing) - productType: What the product actually is, not the brand. Examples: "smartphone", "laptop", "running shoes", "mechanical keyboard", "handbag", "smartwatch", "wireless headphones", "gaming mouse" - modelIdentifier: Specific model if mentioned. Examples: "iPhone 17 Pro Max 256GB", "Galaxy S25 Ultra", "WH-1000XM6". Use null if no specific model mentioned. Return exactly this JSON: { "brand": "Apple", "condition": "NEW", "productType": "smartphone", "modelIdentifier": "iPhone 17 Pro Max 256GB" }"

  Failure handling: broad `catch (Exception e)` logs `"=== LLM CALL 1 FAILED: {} ==="` and returns a **degraded default** `LLMResponse.builder().brand("UNKNOWN").build()` (LLMClient.java:57-62) — pipeline continues with `brand=UNKNOWN`, all other fields null, rather than throwing. This is a graceful-degradation path (contrast with MLClient which throws).

  **LLM Call 2 — `analyzePricing(...)`** (LLMClient.java:65-125). Exact prompt (verbatim, `.formatted(...)` at line 113):
  > "You are a product pricing expert for a 2026 e-commerce marketplace. Return ONLY valid JSON, no markdown, no explanation. Product to price: - Description: "%s" - Brand: %s - Product type: %s - Specific model: %s - Condition: %s - Condition notes from seller: %s - ML physical baseline (Brazilian dataset, ignore for branded products): $%.2f Pricing instructions: - Use CURRENT 2026 market prices in USD for all known brands. - The ML baseline is only reliable for UNKNOWN brands and generic unbranded products. For any recognized brand, override it completely with real market knowledge. - Always return the CURRENT NEW RETAIL price for marketPriceMin and marketPriceMax. - Never apply condition discounts. Price every product as if it is brand new and sealed. - Condition is provided only so you can assess confidence level correctly. - The platform applies condition adjustments separately after you respond. - Be model-specific. iPhone 12 and iPhone 17 have very different prices. A 2019 laptop and a 2024 laptop are not the same price. - marketPriceMin must always be less than marketPriceMax. - Range width guide: 10-20% of midpoint for well-known products, up to 40% for vague or generic products. Confidence assignment: HIGH → Brand is well-known AND specific model is identifiable AND condition is NEW or UNKNOWN MEDIUM → Brand is known BUT condition is USED or REFURBISHED, OR brand is known but model is vague/unclear, OR product is announced but not yet widely available LOW → Brand is UNKNOWN, OR product is handmade/custom/one-of-a-kind, OR description is too vague to price reliably Return exactly this JSON: { "marketPriceMin": number (USD, never null for HIGH/MEDIUM), "marketPriceMax": number (USD, never null for HIGH/MEDIUM), "confidence": "HIGH" or "MEDIUM" or "LOW", "reasoning": "2-3 sentences: what product this is, what drives the price, and why this confidence level" }"

  **Notable design point worth flagging to the pricing-pipeline fork**: the prompt explicitly instructs the LLM to rely on its own training-data "market knowledge" for branded products rather than the ML microservice's prediction, and explicitly says condition/used-item discounting happens *elsewhere* ("The platform applies condition adjustments separately after you respond") — confirms condition-based price adjustment is business logic downstream of this call, not inside the LLM prompt itself. Also note: the prompt says "2026 market prices" — hardcoded year, will silently become stale/wrong every year unless templated with the actual current year.

  Failure handling here throws to a **different degraded default** than Call 1: `LLMResponse.builder().confidence("LOW").multiplier(1.0).reasoning("LLM unavailable").build()` (LLMClient.java:118-123) — no `marketPriceMin`/`marketPriceMax` set (they'll be null/default), confidence forced to `LOW`. Whatever downstream routing logic keys off `confidence=LOW` and null price bounds is the mechanism by which an LLM outage degrades the pipeline — cross-check with the pricing/routing fork's trace.

  Both methods share a private `clean(String raw)` helper (LLMClient.java:127-129) that strips ```` ```json ```` / ```` ``` ```` fences via regex before JSON-parsing — defensive against the model wrapping output in markdown despite being told not to.

## 6. Redis

- Client: `StringRedisTemplate` (Spring Data Redis, Lettuce underneath) — injected directly into `RoutingServiceImpl.java:23`. **No `@Cacheable`/`@CacheEvict` annotations anywhere in the codebase** — caching is 100% manual, all in `RoutingServiceImpl`.
- **Key structure**: `"pricing:" + brand.toLowerCase() + ":" + category.toLowerCase() + ":" + condition.toLowerCase() + ":" + priceBucket` (RoutingServiceImpl.java:112-115, `cacheKey()`), where `priceBucket` is one of `budget` (<200), `mid` (<500), `premium` (<1000), `luxury` (≥1000) (RoutingServiceImpl.java:117-122, `priceBucket()`). Condition is normalized via `Condition.from(condition).name().toLowerCase()`.
- **What's cached**: the value stored is a `"min:max"` string — a previously-**approved** price range for a given brand+category+condition+bucket combo (written by `cacheApprovedRange`, RoutingServiceImpl.java:75-82). This is a "has this kind of product been approved before, and in what range" cache, used to auto-fast-track future similar listings straight to `PENDING_SELLER` without needing a fresh admin bounds check (see `determineStatus`, RoutingServiceImpl.java:29-74, "Layer 1").
- **TTL**: `redisTemplate.opsForValue().set(key, min + ":" + max, 30, TimeUnit.DAYS)` (RoutingServiceImpl.java:80) — flat **30-day TTL**, no sliding expiry, no refresh-on-read.
- **Cache write logic**: `cacheApprovedRange(brand, category, approvedPrice, condition)` computes `min = approvedPrice * 0.90`, `max = approvedPrice * 1.10` (±10% band around the approved price), rounded to 2 decimals (RoutingServiceImpl.java:78-79).
- **Cache read/routing logic** (`determineStatus`, "Layer 1" comment at RoutingServiceImpl.java:35): on a cache hit, if the current candidate `price` falls within the cached `[min,max]` band, the function returns `"PENDING_SELLER"` immediately (skip admin, straight to seller-facing acceptance flow) — a cache miss or out-of-band price falls through to Layer 2 (category bounds) then Layer 3 (confidence gate). Full literal logic:
  ```java
  if (cached != null) {
      String[] parts = cached.split(":");
      if (parts.length == 2) {
          double min = Double.parseDouble(parts[0]);
          double max = Double.parseDouble(parts[1]);
          if (price >= min && price <= max) return "PENDING_SELLER";
      }
  }
  ```
- **No eviction/invalidation logic at all** — there is no `@CacheEvict`-style code path, no `redisTemplate.delete(...)` call anywhere in the grep results for this scope. Entries simply expire after 30 days; if an admin later *rejects* a previously-approved brand/category/condition/bucket combination, the stale cached range is **not invalidated** and will keep fast-tracking matching future listings to `PENDING_SELLER` until natural TTL expiry.
- **Redis-down resilience**: both cache read paths (`determineStatus`'s Layer 1, and the separate `findCachedRange` method) wrap the Redis call in `try/catch` and treat a Redis exception as a cache miss / fall-through (RoutingServiceImpl.java:38-42, 91-96) rather than failing the request — Redis is explicitly treated as an optional optimization layer, not a hard dependency. Comment confirms intent: `// Layer 1 — Redis cache check (Redis is optional; fall through on connection failure)` (RoutingServiceImpl.java:35).
- **Dead-code flag**: `findCachedRange(...)` (RoutingServiceImpl.java:85-110) iterates all 4 price buckets looking for a cached range but is a *different* lookup strategy than the one `determineStatus` actually uses (which computes one specific bucket key from the candidate price, not all 4). I did not find a caller of `findCachedRange` within this fork's scope — flag as possibly dead/unused method for the service-layer fork to confirm with a full call-site search.
- **Cache warmup** (`CacheWarmupService.java`): triggered via `@EventListener(ApplicationReadyEvent.class)` (i.e., runs once, right after the application context is fully up and the embedded server is ready to serve traffic — not `@PostConstruct`), wrapped in `@Transactional(readOnly = true)` (CacheWarmupService.java:22-23). Logic: loads **all** rows from `ApprovedDecisionRepository.findAll()` (no paging — see risk flag below), computes each decision's midpoint `(approvedMin + approvedMax) / 2.0`, and calls `routingService.cacheApprovedRange(brand, category, midpoint, null)` for every one (CacheWarmupService.java:26-35) — repopulating the same 30-day-TTL Redis keys described above from the durable `ApprovedDecision` table (source of truth), so Redis loss/restart is fully recoverable on next app boot. Entire warmup is wrapped in a broad `try/catch` that only logs a warning on failure (CacheWarmupService.java:37-39) — app boot is never blocked by a Redis outage.
- **Unpaged `findAll()` risk**: if `ApprovedDecision` grows large, `CacheWarmupService` loading the entire table into memory on every single app restart is a scaling concern (flagging for the code-quality/risk-flags fork too, since it overlaps).

## 7. Cloudinary

Upload flow (`CloudinaryService.java`), synchronous, no async/queue:
1. **Profile picture** (`uploadProfilePicture`, lines 18-32): takes `MultipartFile` + `userId`, uploads via `cloudinary.uploader().upload(file.getBytes(), ...)` with fixed options: `folder="profile_pictures"`, `public_id="user_" + userId` (deterministic — **re-uploading always overwrites the same public ID** via `overwrite=true`), `resource_type="image"`, and a `Transformation` that resizes to 300×300 with `crop("fill").gravity("face")` (face-aware cropping) — lines 24-27. Returns `result.get("secure_url").toString()`.
2. **Product image** (`uploadProductImage`, lines 34-46): similar, `folder="product_images"`, `public_id="product_" + productId + "_img_" + index` (supports multiple images per product via the `index` suffix), `overwrite=true`, no transformation applied (full original resolution kept).
3. **Delete** (`deleteImage`, lines 48-54): `cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())`.

**On upload failure**: all three methods catch only `IOException` (the checked exception `cloudinary.uploader().upload()`/`.destroy()` declare) and rethrow as an **unchecked `RuntimeException`** with a custom message (e.g., `"Profile picture upload failed: " + e.getMessage()`, lines 29-31) — the original exception/stack trace is preserved as the message text only, not chained as a cause (no `new RuntimeException(msg, e)` — just `new RuntimeException(msg)`), so **stack trace of the root cause is lost** at the point of rethrow (still logged by whatever ultimately handles it, if anything logs the full exception). This bare `RuntimeException` has no dedicated `@ExceptionHandler` in scope for this fork to confirm — cross-check with the error-handling fork for what HTTP status this maps to (likely a generic 500 via Spring's default handling unless `GlobalExceptionHandler` has a catch-all).

Any **non-IOException** failure (e.g., a Cloudinary API error like invalid credentials, rate-limit, or network timeout surfaced as a `RuntimeException` from the SDK itself rather than `IOException`) is **not caught at all** and propagates as whatever Cloudinary's SDK throws — inconsistent handling depending on failure type.

No retry logic, no timeout configuration visible in `CloudinaryConfig.java` (just `cloud_name`/`api_key`/`api_secret`/`secure=true` — CloudinaryConfig.java:18-23), Cloudinary SDK defaults apply.

## 8. Email (Brevo SMTP)

Provider: Brevo transactional SMTP relay (`smtp-relay.brevo.com:587`, STARTTLS). `EmailServiceImpl` implements 5 events, all building a shared branded HTML wrapper (`buildHtml`, lines 23-69 — dark navy header `#1C1F2E` with a Cloudinary-hosted logo image, gold `#C9A96E` title bar, white body, dark footer with "© 2026 DynaMart" + "AI-Powered Dynamic Pricing Marketplace" tagline).

Every event, its trigger, subject line, and key content:
1. **`sendApprovalEmail`** — product approved and now live. Subject: `"Your product has been approved! ✅"`. Body: seller name, product name, approved price (`$%.2f`), admin note (defaults to `"No additional notes."` if null) (lines 71-88).
2. **`sendRejectionEmail`** — listing not approved. Subject: `"Your product listing was not approved"`. Body: reason, and the acceptable `$min — $max` range so the seller can relist within bounds (lines 90-106).
3. **`sendOverrideEmail`** — admin manually changed the price. Subject: `"Your product price has been updated"`. Body: old price struck through (`<s>`), new price highlighted in gold, admin note (lines 108-125).
4. **`sendOrderConfirmationEmail`** — order placed. Subject: `"Order confirmed — " + productName`. Body: product + price paid (lines 127-142).
5. **`sendProductDeletedEmail`** — admin deleted a listing. Subject: `"Your product listing has been removed — DynaMart"`. Body: product name + reason (or a default "No specific reason was provided." string), and note the reason string is concatenated directly into the HTML via plain string concatenation (`"<tr>...</td><td>" + reason + "</td></tr>"`, lines 147-149) **without any HTML-escaping** — if `reason` is admin-supplied free text ever containing `<`/`>`/`&`, it will inject raw HTML into the email body (stored-XSS-adjacent risk in an email client, low severity since sender is admin-controlled, but worth a mention; the other four templates use `.formatted()` on the whole block rather than raw concatenation but have the same underlying lack of escaping for any `%s`-substituted free-text field like `adminNote` or `reason`).

I did not trace the actual **callers** of these five methods (that's the product/admin service layer, out of scope for this integrations-only fork) — cross-check with the service-layer fork for exactly which controller/service methods invoke each one and in what order relative to the DB write.

**Failure handling**: all five public methods funnel into a shared private `send(...)` (lines 166-179) wrapped in `try/catch (Exception e) { log.error(...); }` — **a failed email send is only logged, never rethrown**. This means a failing SMTP call **never rolls back or fails the calling transaction** — email is fire-and-forget/best-effort by design. No retry, no dead-letter/outbox queue — if Brevo is down or credentials are wrong, the email is silently lost with only a log line (`"❌ Failed to send email to {} | Subject: {} | Error: {}"`, line 177) as the only trace.

---
*Scope note: this report covers project overview + external integrations only (report sections 1 & 6 of the requested audit). Data layer, service layer, controllers, security, error-handling/validation, and code-quality/risk-flags were assigned to separate parallel workers but those did not start in this run (nested agent spawning failed — "Fork is not available inside a forked worker"). Those six sections still need to be produced, either by the coordinating session running them sequentially/directly, or by relaunching them as top-level (non-nested) forks.*
