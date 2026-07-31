# Service Layer Audit — DynaMart Backend

Scope: `src/main/java/com/ecommerce/service/**`, `client/LLMClient.java`, `client/MLClient.java` (call-tracing only), `src/test/java/com/ecommerce/service/pricing/*`.

---

## 1. Every service class and public method

### `service/pricing/PricingServiceImpl` (`PricingService`)
- **`getSuggestion(ProductListingRequest request, User seller)`** — `PricingServiceImpl.java:31`
  Orchestrates the entire pricing pipeline (see §2). Calls: `CategoryStatsRepository.findByCategory`, `LLMService.extractProductInfo`, `RoutingService.findCachedRange`, `FeatureBuilderService.buildFeatures`, `MLService.predict`, `LLMService.analyzePricing`, `RoutingService.determineStatus`. No explicit try/catch — propagates whatever `MLClient`/`LLMClient` throw (`PricingException` from ML if it fails; LLM failures are swallowed inside `LLMClient` and returned as fallback DTOs, so `PricingServiceImpl` itself never sees an LLM exception). Not annotated `@Transactional` — this method is the "no DB connection held" middle step by design (see `ProductServiceImpl.listProduct`, §4).

### `service/pricing/LLMServiceImpl` (`LLMService`)
- `extractProductInfo(String description)` — L15, thin passthrough to `LLMClient.extractProductInfo`.
- `analyzePricing(...)` — L20, thin passthrough to `LLMClient.analyzePricing`.
No exception handling of its own; `LLMClient` already catches everything internally (see §2), so in practice this never throws.

### `service/pricing/MLServiceImpl` (`MLService`)
- `predict(MLRequest request)` — L16, thin passthrough to `MLClient.predict`. Propagates `PricingException` if the ML call fails (thrown by `MLClient`, see §2).

### `service/pricing/FeatureBuilderServiceImpl` (`FeatureBuilderService`)
- **`buildFeatures(ProductListingRequest request, LLMResponse llm, User seller, CategoryStats stats)`** — L16. Pure computation, no I/O, no exceptions. Builds all 26 `MLRequest` fields (see §2 step 3). Two private helpers: `resolveWeight` (L68, seller weight → LLM weight → 500g default), `sizeCategory` (L74) — **dead code**, never called anywhere in the class or referenced elsewhere (`sizeCategory` computes a string bucket that is not part of `MLRequest` and not used).

### `service/pricing/RoutingServiceImpl` (`RoutingService`)
- **`determineStatus(double price, String brand, String category, String confidence, String condition)`** — L31, `@Transactional(readOnly = true)`. Three-layer routing logic (see §2 step 7). Reads Redis (`StringRedisTemplate`) and `CategoryBoundsRepository`. Redis failures are caught and logged, falling through to layer 2. `CategoryBoundsRepository.findByCategory` can throw a `NullPointerException` if `category` is null (no null-check before `.toLowerCase()`); not otherwise guarded.
- `cacheApprovedRange(String brand, String category, double approvedPrice, String condition)` — L76. Writes to Redis with a 30-day TTL. **Not** wrapped in try/catch — if Redis is down when this is called (e.g., from `AdminServiceImpl.doApproveTransaction` or `ProductServiceImpl.acceptPrice`), it will throw and propagate uncaught (inconsistent with `determineStatus`'s "Redis is optional" philosophy).
- `findCachedRange(String brand, String category, String condition)` — L85. Loops over 4 hardcoded price buckets (`budget/mid/premium/luxury`) probing Redis keys; catches Redis exceptions per bucket-iteration but returns `Optional.empty()` immediately on the **first** Redis exception (does not continue trying remaining buckets — arguably fine since a down Redis will fail on every subsequent call too, but not obviously intentional).
- Private: `cacheKey`, `priceBucket` (thresholds: `<200` budget, `<500` mid, `<1000` premium, else luxury — L118-121).

### `service/pricing/CacheWarmupService` (not an interface-backed service, plain `@Component`)
- **`warmUpCache()`** — L24, `@EventListener(ApplicationReadyEvent.class)`, `@Transactional(readOnly = true)`. On startup, loads **all** `ApprovedDecision` rows (`findAll()` — no pagination/limit — see §9 risk) and calls `routingService.cacheApprovedRange(brand, category, midpoint, null)` for each. Note: passes `condition = null`; `RoutingServiceImpl.cacheKey` calls `Condition.from(null)` which safely resolves to `UNKNOWN` (`Condition.java:7`), so this doesn't crash, but it means **every warmed cache entry is keyed under the `unknown` condition bucket regardless of the approved decision's actual condition** — a NEW-condition approval and a USED-condition approval for the same brand/category will collide into the same Redis key on warmup, silently overwriting each other. `ApprovedDecision` entity doesn't even store `condition` (see data-layer notes), so this is a structural gap, not just a bug in this method.
Entire method wrapped in try/catch — any failure (DB or Redis) just logs a warning; app still starts.

### `service/product/ProductServiceImpl` (`ProductService`)
- **`listProduct(ProductListingRequest request, User seller)`** — L55. Three-step flow explicitly split into three separate transactions to avoid holding a DB connection during the LLM/ML pipeline (see §4). Calls `ProductPersistenceHelper.saveDraftProduct`, `PricingService.getSuggestion`, `ProductPersistenceHelper.finalizePricingRequest`.
- **`acceptPrice(Long productId, AcceptPriceRequest request, User seller)`** — L86, `@Transactional`. Seller accepts (or picks a price within ±10% of the suggested price). Throws `ResourceNotFoundException` (product/pricing-request not found), `IllegalStateException` (product not `DRAFT`), `IllegalArgumentException` (chosen price outside `[suggested*0.90, suggested*1.10]`). Sets `Product.status = LIVE`, `PricingRequest.status = APPROVED`, calls `RoutingService.cacheApprovedRange`.
- **`disputePrice(Long productId, DisputePriceRequest request, User seller)`** — L134, `@Transactional`. Seller disputes the suggested price → `Product.status = PENDING_REVIEW`, `PricingRequest.status = PENDING` (resets to pending, now for admin). Throws `ResourceNotFoundException`, `IllegalStateException` (not `DRAFT`).
- `getSellerProducts(User seller)` — L164, `@Transactional(readOnly = true)`. **N+1 risk**: for each product, does a separate `pricingRequestRepository.findTopByProductOrderByCreatedAtDesc(p)` call inside the `.map()` (L167) — one extra query per product in the list.
- `getProductById(Long productId, User seller)` — L178, `@Transactional(readOnly = true)`. Throws `ResourceNotFoundException`.
- `getDashboard(User seller)` — L217, `@Transactional(readOnly = true)`. Loads all seller products into memory then filters in Java with 4 separate `.stream().filter().count()` passes (L222-225) instead of aggregate repository queries — inefficient but not a correctness bug at seller-listing scale.
- **`uploadProductImages(Long productId, List<MultipartFile> files, User seller)`** — L250, `@Transactional`. **Transaction-boundary issue**: this method is `@Transactional` and loops calling `cloudinaryService.uploadProductImage(...)` (external network I/O to Cloudinary) once per file *inside* the transaction (L255-257) — holds a DB connection open for the duration of N sequential HTTP uploads. This is exactly the anti-pattern `listProduct` was deliberately restructured to avoid (see the comment at `ProductServiceImpl.java:59`), but it was not applied here.
- **`deleteProduct(Long productId, User seller)`** — L268, `@Transactional`. Ownership check throws `org.springframework.security.access.AccessDeniedException` if `product.getSeller().getId() != seller.getId()` (note: uses `productRepository.findById`, not `findByIdAndSeller` like the other methods — ownership is checked manually after fetch rather than in the query). Soft-deletes via `status = DELETED`. Throws `ResourceNotFoundException`.

### `service/product/ProductPersistenceHelper` (plain service, not interface-backed)
- **`saveDraftProduct(ProductListingRequest request, User seller)`** — L26, `@Transactional`. Creates and saves a `DRAFT` `Product`. Note: does **not** set `brand` (brand is set later in `finalizePricingRequest`) — between these two calls the product briefly exists in DB with `brand = null`.
- **`finalizePricingRequest(Product product, ProductListingRequest request, PricingSuggestionResponse suggestion)`** — L40, `@Transactional`. Sets `product.brand`; `switch` on `suggestion.getStatus()`: only handles the literal string `"PENDING_ADMIN"` (→ `ProductStatus.PENDING_REVIEW`), with a comment noting `"PENDING_SELLER"` intentionally leaves status as `DRAFT`. **This switch has no `default` case** — if `PricingService`/`RoutingService` ever return any other status string, it silently falls through and product stays `DRAFT` with no warning logged (a silent-failure risk if the routing string constants ever drift, since they're untyped `String`, not the `ProductStatus` enum — see §3). Persists `PricingRequest` with `status = PENDING`.

### `service/admin/AdminServiceImpl` (`AdminService`)
- `getPendingRequests()` — L62, `@Transactional(readOnly = true)`. Returns pricing requests with `status = PENDING` **and** `product.status = PENDING_REVIEW`.
- `approveRequest(Long requestId, ApproveRequest request)` — L71 **NOT `@Transactional`** itself; delegates to private `doApproveTransaction` (L143, `@Transactional`) then sends email outside the transaction. **Critical finding: `doApproveTransaction`, `doRejectTransaction`, `doOverrideTransaction` are `private` methods annotated `@Transactional` (L143, L180, L207).** Spring's proxy-based AOP (both JDK dynamic proxies and CGLIB) cannot intercept private methods or self-invocation — calling `this.doApproveTransaction(...)` from `approveRequest` bypasses the proxy entirely, so **`@Transactional` on these three methods has no effect at runtime**. All the multi-step writes inside them (`product.save` + `pricingRequest.save` + `approvedDecisionRepository.save` + `routingService.cacheApprovedRange`) execute with Spring's default (no explicit transaction demarcation from these annotations) rather than as one atomic unit — a partial failure mid-method (e.g., Redis write failing in `cacheApprovedRange` after the DB writes succeeded) will **not** roll back the DB changes. This is the single most important correctness bug found in the service layer.
- `rejectRequest(...)` — L80, same pattern/bug as above via `doRejectTransaction`.
- `overridePrice(Long productId, OverrideRequest request)` — L89, same pattern via `doOverrideTransaction`; only allowed when `product.status == LIVE` (`IllegalStateException` otherwise).
- `getRequestById`, `getAllProducts` (paginated, optional status filter), `getStats` — read-only, correctly `@Transactional(readOnly = true)`.
- **`deleteProduct(Long productId, DeleteProductRequest request)`** — L311, `@Transactional` (correctly, this one is `public`, not private, so the annotation *does* work). Soft-delete (`status = DELETED`) + sends deletion email. **No ownership/state check at all** — unlike the seller's own `deleteProduct`, this doesn't verify the product isn't already `DELETED`, and can be called on a product in any status including already-`LIVE` orders in customers' order history (orders reference the product by FK, not a snapshot, aside from `priceAtPurchase`).
- Private helpers `toAdminProductResponse`, `toAdminResponse` (routing-reason heuristic re-derives bounds-check logic independently from `RoutingServiceImpl`, L272-281 — duplicated logic, could drift out of sync since it's not shared).

### `service/cart/CartServiceImpl` (`CartService`)
- `addToCart` — `@Transactional`. Throws `ResourceNotFoundException`, `IllegalStateException` (product not `LIVE`, seller buying own product, duplicate cart entry).
- `getCart` — `@Transactional(readOnly = true)`, uses `findByBuyerWithProductAndSeller` (join-fetch, N+1-safe).
- `removeFromCart`, `clearCart` — `@Transactional`.

### `service/wishlist/WishlistServiceImpl` (`WishlistService`)
- `saveProduct`, `getSaved`, `unsaveProduct`, `clearWishlist` — same shape as cart service. `getSaved` uses plain `findByBuyer` (no join fetch mentioned) — potential N+1 on `product`/`seller` access in `toResponse`, unlike cart's join-fetch variant.

### `service/buyer/BuyerServiceImpl` (`BuyerService`)
- `getAllLiveProducts()` / paginated overload — `@Transactional(readOnly = true)`.
- `getProductById`, `getProductHistory` — throw `ResourceNotFoundException` both when missing and when not `LIVE` (same exception/message for both cases — a buyer can't distinguish "doesn't exist" from "not live yet", which is a deliberate-looking privacy choice but undocumented).
- `placeOrder(OrderRequest request, User buyer)` — `@Transactional`. Throws `ResourceNotFoundException`, `IllegalStateException` (not LIVE / buying own product). Sends order-confirmation email **inside** the `@Transactional` method (synchronous SMTP call inside a DB transaction — smaller blast radius than the LLM/ML case but same category of anti-pattern: if `EmailServiceImpl.send` were to throw instead of swallowing exceptions, it would roll back an otherwise-valid order; currently protected because `send()` catches all exceptions internally, §6).
- `getMyOrders`, `getOrderById` — `getOrderById` throws `AccessDeniedException` if the order doesn't belong to the requesting buyer.

### `service/user/UserServiceImpl` (`UserService`)
- `uploadProfilePicture(MultipartFile file, User user)` — **not `@Transactional`**, calls Cloudinary then `userRepository.save(user)`. No transaction needed strictly (single save) but also no rollback safety if `save` fails after upload succeeds — orphaned Cloudinary asset, minor risk.

### `service/auth/AuthServiceImpl` (`AuthService`)
- `register(RegisterRequest request)` — `@Transactional`. Throws `EmailAlreadyExistsException`. **Role safety net**: `if (role == null || role == Role.ADMIN) role = Role.BUYER;` (L36) — explicitly prevents self-registration as ADMIN even if the request DTO carries `Role.ADMIN`.
- `login(LoginRequest request)` — `@Transactional(readOnly = true)`. Delegates credential check to `AuthenticationManager` (throws Spring Security's `AuthenticationException` subtypes, e.g. `BadCredentialsException`, uncaught here — see GlobalExceptionHandler in a separate audit fork). `userRepository.findByEmail(...).orElseThrow()` — **bare `orElseThrow()` with no exception supplier**, throws generic `NoSuchElementException` if the user vanished between successful auth and lookup (should be unreachable in practice, but it's an uncaught/unmapped exception type if it ever fires).
- `refresh(String refreshToken)` — `@Transactional(readOnly = true)`. Throws `TokenRefreshException` (user not found, or token invalid/expired per `JwtUtil.isValid`).

### `service/auth/UserDetailsServiceImpl` (`UserDetailsService`)
- `loadUserByUsername(String email)` — throws `UsernameNotFoundException`. Note: `User` entity itself must implement `UserDetails` for this direct return to type-check (confirmed by return type); worth the security-layer audit fork double-checking `User.java`'s `getAuthorities()`/`isEnabled()` implementation for correctness.

### `service/admin/EmailServiceImpl` (`EmailService`)
5 public methods: `sendApprovalEmail`, `sendRejectionEmail`, `sendOverrideEmail`, `sendOrderConfirmationEmail`, `sendProductDeletedEmail` — each builds an inline HTML string (branded template, hardcoded Cloudinary logo URL at L21) and calls private `send(...)`. **`send()` (L166) catches `Exception` broadly and only logs** — email failures are always swallowed, never surfaced to the caller or the end user. This means, e.g., `acceptPrice`/`doApproveTransaction` callers get no signal at all if the seller's approval email silently fails to send.

### `service/upload/CloudinaryService` (plain `@Service`, no interface)
- `uploadProfilePicture`, `uploadProductImage` — wrap `IOException` into unchecked `RuntimeException` (generic, not a domain exception type — inconsistent with the rest of the codebase's use of custom exceptions like `PricingException`/`ResourceNotFoundException`).
- **`deleteImage(String publicId)`** — L48. **Dead code**: grepped the entire `src/main/java` tree, no caller anywhere. Products/profile pictures are never actually deleted from Cloudinary storage when a product is deleted (`ProductServiceImpl.deleteProduct`, `AdminServiceImpl.deleteProduct`) — orphaned media accumulates in Cloudinary indefinitely.

---

## 2. Full pricing pipeline trace (verbatim thresholds, file:line cited)

Entry point: `PricingServiceImpl.getSuggestion` (`PricingServiceImpl.java:31`), called from `ProductServiceImpl.listProduct` (`ProductServiceImpl.java:60`) — itself sandwiched between two separate short transactions (see §4).

**Step 0 — Category stats lookup** (`PricingServiceImpl.java:34-36`)
```java
CategoryStats stats = categoryStatsRepository
        .findByCategory(request.getCategory().toLowerCase())
        .orElse(null);
```

**Step 1 — LLM Call 1 (extraction)** (`PricingServiceImpl.java:39`, prompt at `LLMClient.java:20-49`)
```java
LLMResponse extraction = llmService.extractProductInfo(request.getDescription());
```
Prompt (verbatim, `LLMClient.java:20-49`):
```
You are a product information extractor for an e-commerce platform.
Extract structured facts from this product description.
Return ONLY valid JSON, no markdown, no explanation.

Product description: "%s"

Rules:
- brand: The most prominent brand name. Use "UNKNOWN" if none found. Never null.
- condition: Classify as exactly one of:
    "NEW"         → described as new, sealed, brand new, unopened, never used
    "USED"        → described as used, second hand, secondhand, pre-owned,
                    previously owned, gently used, worn, minor scratches,
                    good condition, fair condition, like new, open box
    "REFURBISHED" → described as refurbished, restored, reconditioned, certified pre-owned
    "UNKNOWN"     → no condition mentioned (assume new retail listing)
- productType: What the product actually is, not the brand.
  Examples: "smartphone", "laptop", "running shoes", "mechanical keyboard",
  "handbag", "smartwatch", "wireless headphones", "gaming mouse"
- modelIdentifier: Specific model if mentioned. Examples: "iPhone 17 Pro Max 256GB",
  "Galaxy S25 Ultra", "WH-1000XM6". Use null if no specific model mentioned.

Return exactly this JSON:
{
  "brand": "Apple",
  "condition": "NEW",
  "productType": "smartphone",
  "modelIdentifier": "iPhone 17 Pro Max 256GB"
}
```
Note: the extracted `condition` field is requested by the prompt but **never read** by `PricingServiceImpl` — condition always comes from the seller's own form field (`request.getCondition()`, L83), not the LLM. The LLM's condition guess is effectively discarded. On any exception, `LLMClient.extractProductInfo` (L57-62) catches everything and returns `LLMResponse.builder().brand("UNKNOWN").build()` — pipeline never sees an LLM Call 1 failure as an exception.

**Step 2 — Early cache check (cache-first optimization)** (`PricingServiceImpl.java:42-69`)
```java
String earlyBrand     = extraction.getBrand() != null ? extraction.getBrand() : "UNKNOWN";
String earlyCondition = request.getCondition();
Optional<double[]> cachedRange = routingService.findCachedRange(earlyBrand, request.getCategory(), earlyCondition);
if (cachedRange.isPresent()) {
    double cachedMin = range[0], cachedMax = range[1];
    double suggested  = round((cachedMin + cachedMax) / 2.0);
    // returns immediately, status="PENDING_SELLER", confidence="HIGH", ML+LLM Call 2 both skipped
}
```
`RoutingServiceImpl.findCachedRange` (`RoutingServiceImpl.java:85-110`) probes Redis keys `pricing:{brand}:{category}:{condition}:{bucket}` across all 4 buckets (`budget, mid, premium, luxury`) — but note the key format includes a price **bucket**, and at this point in the pipeline the actual suggested price isn't known yet, so this only hits cache if a previous approved price happened to land in the same bucket independent of the (unknown) new price. This is a designed shortcut, not a bug, but it means cache hits are approximate (bucket-level), not exact-price keyed.

**Step 3 — Feature building (26 ML features)** (`PricingServiceImpl.java:73`, logic in `FeatureBuilderServiceImpl.java:16-66`)
```java
MLRequest mlRequest = featureBuilderService.buildFeatures(request, extraction, seller, stats);
```
Key derived values:
- `weight = resolveWeight(request.getWeight(), llm.getEstimatedWeight())` → seller weight if `>0`, else LLM-estimated weight if `>0`, else **hardcoded fallback `500.0`** (`FeatureBuilderServiceImpl.java:71`).
- `estimatedVolume = weight * 5.0`; `side = Math.cbrt(estimatedVolume)` used for length/height/width (all three set equal — a cube approximation, `FeatureBuilderServiceImpl.java:24-25`).
- Category/seller/product stats fall back to hardcoded defaults when `stats == null`: `sellerAvgPrice`/`categoryAvgPrice` → `100.0`, `sellerAvgReview`/`productAvgReview` → `4.0`, `sellerSalesCount`/`productSalesCount` → `50`, `maxInstallments` → `12`, `paymentTypeMode` → `"credit_card"` (`FeatureBuilderServiceImpl.java:47-60`).
- Geography hardcoded: `customerState = "SP"`, `sellerState = "SP"` (Brazilian state code — leftover from the underlying Olist/Brazilian e-commerce ML training dataset; not derived from any real seller/buyer location field, `FeatureBuilderServiceImpl.java:63-64`).

**Step 4 — ML baseline call** (`PricingServiceImpl.java:76-77`, `MLClient.java:20-33`)
```java
MLResponse mlResponse = mlService.predict(mlRequest);
double mlBaseline = mlResponse.getPredictedPrice();
```
`MLClient.predict` POSTs to `{ml.service.url}/predict`. **No explicit timeout, no retry configured on the `RestTemplate` in this client** (see `config/RestTemplateConfig.java` — check in external-integrations audit for bean-level timeout config). On any exception or a null/`predictedPrice==null` response, throws `PricingException` (`MLClient.java:24-32`) — this propagates uncaught out of `PricingServiceImpl.getSuggestion` and up to `ProductServiceImpl.listProduct`, meaning **an ML outage causes `listProduct` to fail entirely after the DRAFT product row was already committed** (step 1 of `listProduct`'s 3-step transaction split) — the product is left in `DRAFT` status with no `PricingRequest` ever created, and the seller-facing HTTP call gets whatever `GlobalExceptionHandler` maps `PricingException` to.

**Step 5 — Condition resolution** (`PricingServiceImpl.java:83-90`)
```java
String condition = request.getCondition();          // seller's field wins — LLM's condition guess ignored
String conditionGrade = request.getConditionGrade();
String conditionNotes = request.getConditionNotes() != null ? request.getConditionNotes() : "";
```

**Step 6 — LLM Call 2 (pricing/confidence)** (`PricingServiceImpl.java:93-100`, prompt at `LLMClient.java:70-113`)
```java
LLMResponse pricing = llmService.analyzePricing(
        request.getDescription(), extraction.getBrand(), condition, conditionNotes,
        extraction.getProductType(), extraction.getModelIdentifier(), mlBaseline);
```
Prompt (verbatim, `LLMClient.java:70-113`):
```
You are a product pricing expert for a 2026 e-commerce marketplace.
Return ONLY valid JSON, no markdown, no explanation.

Product to price:
- Description: "%s"
- Brand: %s
- Product type: %s
- Specific model: %s
- Condition: %s
- Condition notes from seller: %s
- ML physical baseline (Brazilian dataset, ignore for branded products): $%.2f

Pricing instructions:
- Use CURRENT 2026 market prices in USD for all known brands.
- The ML baseline is only reliable for UNKNOWN brands and generic unbranded products.
  For any recognized brand, override it completely with real market knowledge.
- Always return the CURRENT NEW RETAIL price for marketPriceMin and marketPriceMax.
- Never apply condition discounts. Price every product as if it is brand new and sealed.
- Condition is provided only so you can assess confidence level correctly.
- The platform applies condition adjustments separately after you respond.
- Be model-specific. iPhone 12 and iPhone 17 have very different prices.
  A 2019 laptop and a 2024 laptop are not the same price.
- marketPriceMin must always be less than marketPriceMax.
- Range width guide: 10-20% of midpoint for well-known products,
  up to 40% for vague or generic products.

Confidence assignment:
HIGH   → Brand is well-known AND specific model is identifiable AND
         condition is NEW or UNKNOWN
MEDIUM → Brand is known BUT condition is USED or REFURBISHED,
         OR brand is known but model is vague/unclear,
         OR product is announced but not yet widely available
LOW    → Brand is UNKNOWN, OR product is handmade/custom/one-of-a-kind,
         OR description is too vague to price reliably

Return exactly this JSON:
{
  "marketPriceMin": number (USD, never null for HIGH/MEDIUM),
  "marketPriceMax": number (USD, never null for HIGH/MEDIUM),
  "confidence": "HIGH" or "MEDIUM" or "LOW",
  "reasoning": "2-3 sentences: what product this is, what drives the price, and why this confidence level"
}
```
On exception, `LLMClient.analyzePricing` (L117-124) returns `LLMResponse.builder().confidence("LOW").multiplier(1.0).reasoning("LLM unavailable").build()` — LLM Call 2 failure is swallowed and downgrades to LOW confidence (→ `mlBaseline` used, routed to `PENDING_ADMIN` per step 7's confidence gate), never surfaced as an HTTP error.

**Step 7 — UNKNOWN-brand guard** (`PricingServiceImpl.java:109-121`)
```java
String brand = extraction.getBrand() != null ? extraction.getBrand() : "UNKNOWN";
if ("UNKNOWN".equalsIgnoreCase(brand)) {
    pricing = LLMResponse.builder()
            .confidence("LOW").marketPriceMin(null).marketPriceMax(null)
            .reasoning("Brand is unknown — ML baseline used for pricing.")
            .build();
}
```
This **overrides whatever LLM Call 2 actually returned** if brand is UNKNOWN, forcing LOW confidence regardless.

**Step 8 — Combine ML + LLM into a suggested price** (`PricingServiceImpl.java:124`, logic at `computeSuggestedPrice`, L176-208, and `getConditionMultiplier`, L210-217)
```java
private double computeSuggestedPrice(LLMResponse llm, double mlBaseline, String condition, String conditionGrade) {
    boolean hasLLMRange = llm.getMarketPriceMin() != null && llm.getMarketPriceMax() != null;
    double multiplier = getConditionMultiplier(condition, conditionGrade);
    return switch (llm.getConfidence().toUpperCase()) {
        case "HIGH" -> {
            if (hasLLMRange) {
                double mid = (llm.getMarketPriceMin() + llm.getMarketPriceMax()) / 2.0;
                yield mid * multiplier;
            }
            yield llm.getMarketPriceMax() != null ? llm.getMarketPriceMax() * multiplier
                    : llm.getMarketPriceMin() != null ? llm.getMarketPriceMin() * multiplier
                    : mlBaseline;
        }
        case "MEDIUM" -> {
            if (hasLLMRange) {
                double min = llm.getMarketPriceMin() * multiplier;
                double max = llm.getMarketPriceMax() * multiplier;
                double mid = (min + max) / 2.0;
                if (mlBaseline >= min && mlBaseline <= max) yield mlBaseline;
                yield mid;
            }
            yield mlBaseline;
        }
        default -> mlBaseline;   // LOW confidence
    };
}
```
Condition multiplier table (`PricingServiceImpl.java:210-217`), applied only to the LLM's new-retail price, never to the ML baseline:
```java
private double getConditionMultiplier(String condition, String conditionGrade) {
    if (condition == null) return 1.0;
    return switch (condition.toUpperCase()) {
        case "USED" -> "HEAVY".equalsIgnoreCase(conditionGrade) ? 0.45 : 0.60;
        case "REFURBISHED" -> 0.65;
        default -> 1.0;
    };
}
```
So: **NEW/UNKNOWN → ×1.0, USED (normal) → ×0.60, USED+HEAVY grade → ×0.45, REFURBISHED → ×0.65.** Confirmed by unit tests `PricingServiceImplTest.usedHeavyCondition_Applies045Multiplier` (900 mid × 0.45 = 405.0) and `refurbishedCondition_Applies065Multiplier` (700 mid × 0.65 = 455.0).

Then price range is derived as **flat ±10% of the suggested price** (`PricingServiceImpl.java:125-126`):
```java
double minRange = round(suggested * 0.90);
double maxRange = round(suggested * 1.10);
```

**Step 9 — Routing decision** (`PricingServiceImpl.java:130`, logic in `RoutingServiceImpl.determineStatus`, L31-74)
```java
String status = routingService.determineStatus(suggested, brand, request.getCategory(), pricing.getConfidence(), condition);
```
Three layers, in order, first match wins:
1. **Redis cache check** (`RoutingServiceImpl.java:36-53`) — key = `pricing:{brand}:{category}:{condition}:{bucket(price)}` where `bucket`: `price<200→budget, <500→mid, <1000→premium, else→luxury` (`RoutingServiceImpl.java:118-121`). If cached `min:max` string exists and `price` falls within `[min,max]` → **`"PENDING_SELLER"`** immediately (skips bounds/confidence layers entirely). Redis errors here are caught and fall through to layer 2.
2. **Category bounds check** (`RoutingServiceImpl.java:56-64`) — `CategoryBoundsRepository.findByCategory(category.toLowerCase())`; if a `CategoryBounds` row exists and `price < minBound || price > maxBound` → **`"PENDING_ADMIN"`**.
3. **Confidence gate** (`RoutingServiceImpl.java:67-73`):
```java
switch (confidence.toUpperCase()) {
    case "HIGH":
    case "MEDIUM":
        return "PENDING_SELLER";
    default:
        return "PENDING_ADMIN";   // LOW confidence
}
```

**Step 10 — ML-based sanity/business-rule validation ("ML validation" layer, additional to routing)** (`PricingServiceImpl.java:132-156`)
```java
double categoryAvgPrice = (stats != null && stats.getAvgPrice() != null)
        ? stats.getAvgPrice().doubleValue() : mlBaseline;
double priceRatio = categoryAvgPrice > 0 ? suggested / categoryAvgPrice : 1.0;
boolean suspiciousPrice = priceRatio > 50.0 || priceRatio < 0.1;
if (suspiciousPrice && !"LOW".equalsIgnoreCase(pricing.getConfidence())) {
    status = "PENDING_ADMIN";   // overrides the routing decision from step 9
}
```
**This is the exact "system validates ML-generated prices against business rules" mechanism** — thresholds are **50× or 0.1× the category average price** (or the ML baseline itself if no category stats exist yet). Only overrides to `PENDING_ADMIN`; a LOW-confidence price is already `PENDING_ADMIN` from step 9, so this check is effectively only consequential for HIGH/MEDIUM-confidence prices that are wildly off from the category average.

**Step 11 — Redis cache write on approval** happens later, not in this method — `RoutingService.cacheApprovedRange` is called from `ProductServiceImpl.acceptPrice` (seller accepts) and `AdminServiceImpl.doApproveTransaction`/`doOverrideTransaction` (admin approves/overrides), always with a **fixed ±10% band around the approved price and a 30-day TTL** (`RoutingServiceImpl.java:76-82`).

**Final response** — `PricingSuggestionResponse` built at `PricingServiceImpl.java:157-172`, includes `suggestedPrice`, `minRange`/`maxRange` (±10%), `confidence`, `status`, `brand`, `mlBaselinePrice`, `marketPriceMin`/`marketPriceMax` (raw new-retail LLM figures, pre-condition-multiplier), `reasoning`.

---

## 3. Product status lifecycle (actual enum + real transitions)

**Actual `ProductStatus` enum** (`enums/ProductStatus.java:3-4`): `PENDING_REVIEW, LIVE, REJECTED, DRAFT, DELETED` — **only 5 states**. There is **no `PENDING_ADMIN` or `PENDING_SELLER` value in this enum.** Those two strings only ever exist as transient `String` values inside `PricingSuggestionResponse.status` / `AcceptPriceResponse.status` / `DisputeResponse.status` — they are UI/API-facing labels, never persisted as the `Product.status` column. This is a real mismatch worth flagging against any documentation that describes `PENDING_ADMIN`/`PENDING_SELLER` as product statuses — in the DB they collapse to `DRAFT` (implicitly, "awaiting seller") or `PENDING_REVIEW` (explicitly, "awaiting admin").

Separately, `PricingRequestStatus` (`enums/PricingRequestStatus.java:3-4`): `PENDING, APPROVED, REJECTED` — tracks the `PricingRequest` row, independent of `Product.status`.

Real transition table:

| From | To | Trigger | Method (file:line) |
|---|---|---|---|
| *(none)* | `DRAFT` | Seller submits a new listing | `ProductPersistenceHelper.saveDraftProduct` — `ProductPersistenceHelper.java:26-38` |
| `DRAFT` | `DRAFT` (unchanged) or `PENDING_REVIEW` | Pricing pipeline finishes: routing status `"PENDING_SELLER"` → stays `DRAFT`; `"PENDING_ADMIN"` → becomes `PENDING_REVIEW` | `ProductPersistenceHelper.finalizePricingRequest` — `ProductPersistenceHelper.java:44-47` (switch statement, no `default`) |
| `DRAFT` | `LIVE` | Seller calls accept-price endpoint, chooses a price in range | `ProductServiceImpl.acceptPrice` — `ProductServiceImpl.java:113` |
| `DRAFT` | `PENDING_REVIEW` | Seller disputes the suggested price | `ProductServiceImpl.disputePrice` — `ProductServiceImpl.java:150` |
| `PENDING_REVIEW` | `LIVE` | Admin approves | `AdminServiceImpl.doApproveTransaction` — `AdminServiceImpl.java:159` (guarded: throws `IllegalStateException` unless `product.status == PENDING_REVIEW`, L149) |
| `PENDING_REVIEW` | `REJECTED` | Admin rejects | `AdminServiceImpl.doRejectTransaction` — `AdminServiceImpl.java:195` (guarded: L186) |
| `LIVE` | `LIVE` (price changes only, no status change) | Admin overrides price | `AdminServiceImpl.doOverrideTransaction` — `AdminServiceImpl.java:212-221` (guarded: only allowed when already `LIVE`, L212) |
| any | `DELETED` | Seller deletes own product | `ProductServiceImpl.deleteProduct` — `ProductServiceImpl.java:276` (ownership-checked, no status precondition) |
| any | `DELETED` | Admin deletes any product | `AdminServiceImpl.deleteProduct` — `AdminServiceImpl.java:316` (**no status precondition at all**, can delete a `LIVE` product with active orders) |

Notably absent: there is **no path back from `REJECTED` or `PENDING_REVIEW` to `DRAFT`** for the seller to revise and resubmit — once rejected, a product appears permanently `REJECTED` (no relist/resubmit method exists in `ProductService` or `AdminService`).

---

## 4. `@Transactional` audit

**Correctly transactional, single-unit DB work:**
`AuthServiceImpl.register` (write), `.login`/`.refresh` (`readOnly=true`); `ProductPersistenceHelper.saveDraftProduct`/`finalizePricingRequest`; `ProductServiceImpl.acceptPrice`, `.disputePrice`, `.getSellerProducts`, `.getProductById`, `.getDashboard`, `.deleteProduct`; `CartServiceImpl` (all 4 methods); `WishlistServiceImpl` (all 4 methods); `BuyerServiceImpl` (all 7 methods); `AdminServiceImpl.getPendingRequests`, `.getRequestById`, `.getAllProducts`, `.getStats`, `.deleteProduct` (this one's `public`, works correctly); `RoutingServiceImpl.determineStatus` (`readOnly=true`); `CacheWarmupService.warmUpCache` (`readOnly=true`).

**Broken / ineffective `@Transactional`:**
- `AdminServiceImpl.doApproveTransaction` (L143), `doRejectTransaction` (L180), `doOverrideTransaction` (L207) — **all three are `private` methods**. Spring AOP transaction proxies cannot apply to private methods (no bytecode weaving is configured — only proxy-based AOP is in play here, standard Spring Boot default). The public wrapper methods (`approveRequest`, `rejectRequest`, `overridePrice`) that call them are **not themselves `@Transactional`**, so nothing establishes a transaction boundary around the multi-repository writes inside. In effect, each `.save()` call runs in its own auto-committed unit (or whatever the default propagation resolves to without an active tx), so a failure partway through (e.g. `approvedDecisionRepository.save` succeeding but `routingService.cacheApprovedRange`'s Redis write throwing) leaves the DB in a partially-updated, non-atomic state with no rollback. **This is the top correctness bug in the service layer** — flagged again in §10.
- `UserServiceImpl.uploadProfilePicture` — no `@Transactional` at all; single `save()` so low risk, but the Cloudinary upload (external I/O) happens before the save with no compensating action if the save fails afterward (orphaned Cloudinary asset).

**Transaction-boundary anti-patterns (annotated correctly, but doing the wrong thing inside the boundary):**
- `ProductServiceImpl.uploadProductImages` (L250) — `@Transactional`, loops calling `CloudinaryService.uploadProductImage` (network I/O) once per file *inside* the open transaction (L255-257). Holds a DB connection for N sequential HTTP round-trips. This is precisely the pattern `listProduct` was restructured to avoid (see the comment at `ProductServiceImpl.java:59`, and the commit history entry `dc80b79 fix: split listProduct transaction to release DB connection during LLM+ML pipeline`), but the same fix wasn't applied here.
- `BuyerServiceImpl.placeOrder` (L95) — `@Transactional`, calls `emailService.sendOrderConfirmationEmail(...)` (SMTP I/O) inside the transaction (L114). Lower risk than the above since `EmailServiceImpl.send` swallows all exceptions internally (§1), so it can't cause a rollback, but it still holds the DB connection open for the duration of an SMTP round-trip.

**By design, correctly *not* transactional:**
`PricingServiceImpl.getSuggestion` — deliberately has no `@Transactional`; it's sandwiched between two short transactions in `ProductServiceImpl.listProduct` specifically so the LLM+ML round-trip (multi-second, network-bound) never holds a DB connection. This is the one part of the codebase that shows explicit, comment-documented awareness of the transaction/external-I/O tension — makes the unguarded cases above (`uploadProductImages`, admin private-method transactions) look like inconsistent application of a lesson otherwise learned.

---

## 5. Dead code, TODO/FIXME, N+1 risk

**Dead/unused code:**
- `FeatureBuilderServiceImpl.sizeCategory(double)` (`FeatureBuilderServiceImpl.java:74-79`) — never called.
- `CloudinaryService.deleteImage(String)` (`CloudinaryService.java:48-54`) — never called anywhere in `src/main/java`; product/profile-picture deletion never actually removes the asset from Cloudinary.

**TODO/FIXME:** none found anywhere under `service/**` (grepped `TODO|FIXME`, zero matches).

**N+1 query risk:**
- `ProductServiceImpl.getSellerProducts` (`ProductServiceImpl.java:164-174`) — one `pricingRequestRepository.findTopByProductOrderByCreatedAtDesc(p)` call per product inside `.map()` (L167-170); N products → N+1 queries. (Note: commit `54ce3d8` already fixed a similar N+1 in the *admin* product list via JOIN FETCH — this seller-facing equivalent appears to still have the issue.)
- `WishlistServiceImpl.getSaved` (`WishlistServiceImpl.java:49-54`) — uses plain `savedProductRepository.findByBuyer(buyer)` (no join-fetch), then accesses `saved.getProduct()` and `p.getSeller()` per item in `toResponse` — likely N+1 on lazy-loaded `product`/`seller` associations, unlike `CartServiceImpl.getCart` which explicitly uses a join-fetching query (`findByBuyerWithProductAndSeller`) for the equivalent case.

---

## Requirement cross-check evidence (service-layer portion only)

- **"Price prediction requests routed to ML microservice via REST"** — PASS. `MLClient.predict` (`MLClient.java:20-33`) does `restTemplate.postForObject(mlServiceUrl + "/predict", request, MLResponse.class)`.
- **"System validates/adjusts/rejects ML-generated prices against business rules before use"** — PASS, concretely: category-bounds check (`RoutingServiceImpl.java:56-64`), confidence gate (`RoutingServiceImpl.java:67-73`), and the 50×/0.1× category-average sanity check (`PricingServiceImpl.java:138-150`).
- **"Every prediction event logged (inputs, predicted value, rules applied) for audit"** — PARTIAL. Every `PricingRequest` row persists `suggestedPrice`, `brand`, `llmConfidence`, `mlBaselinePrice`, `marketPriceMin/Max`, `condition`, `reasoning` (`ProductPersistenceHelper.java:52-68`) — this is solid structured audit data. However, the *routing reason* (which of the 3 routing layers fired, or whether the ML-sanity-check override fired) is **not persisted** — it only exists transiently as `String status` inside `PricingServiceImpl` and is reconstructed heuristically later, differently, in `AdminServiceImpl.toAdminResponse` (`AdminServiceImpl.java:272-281`, only checks bounds vs. "LOW_CONFIDENCE", doesn't know about the ML-sanity-check path at all). So the "rules applied" part of this requirement is not fully auditable after the fact.
- **"Admin can view products, trigger predictions, manually override/approve prices"** — PASS for view/override/approve (`AdminServiceImpl.getAllProducts`, `.overridePrice`, `.approveRequest`). Admin does not "trigger predictions" directly — predictions only happen automatically at seller listing time (`ProductServiceImpl.listProduct`); there's no admin-initiated re-prediction endpoint/method found in this layer.
- **"Predictions generated within a few seconds (near real-time)"** — PARTIAL/unverifiable from code alone: 2 sequential LLM calls + 1 ML call, no timeouts visible on the LLM `ChatClient` side within this scope (external-integrations fork should confirm `SpringAIConfig`/`RestTemplateConfig` timeout values); cache-first check exists specifically to skip ML+LLM Call 2 on repeat brand/category/condition combos.
- **"Graceful handling of microservice failures, data integrity maintained"** — PARTIAL. LLM failures are fully graceful (caught, fallback DTOs, §2). **ML failures are not graceful for data integrity**: `MLClient.predict` throws `PricingException` uncaught through `PricingServiceImpl`/`ProductServiceImpl.listProduct`, and since the `DRAFT` product row was already committed in a prior transaction (`persistenceHelper.saveDraftProduct`), an ML outage leaves an orphaned `DRAFT` product with no `PricingRequest` and no way for the seller to retry pricing for that same draft (no such retry method exists in `ProductService`).
- **"Auth/authorization restricts admin and backend operations to authorized users"** — not verifiable from this layer alone (that's `@PreAuthorize`/`SecurityConfig`, outside this fork's scope); role-check evidence found here is only `AuthServiceImpl.register`'s ADMIN-registration guard (`AuthServiceImpl.java:36`).
- **"Architecture supports independent evolution/redeployment of ML service and backend"** — PASS at the code-shape level: `MLClient`/`LLMClient` are the only two integration points, both behind interfaces (`MLService`/`LLMService`), URL externalized via `${ml.service.url}` (`MLClient.java:15`).

---

*This report covers the service layer only. Controllers, security config, DTO validation, and deep external-integration details (timeouts, retries, Redis TTL/warmup infra, Cloudinary/Brevo failure modes) are covered by other audit passes.*
