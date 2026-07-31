# Data Layer Audit — DynaMart ecommerce-backend

Scope: `src/main/java/com/ecommerce/entity`, `.../repository`, `.../enums`. All findings below are read directly from source, cited as `file:line`.

## 1. Entities

### `User` — `entity/User.java`
- `id: Long` (`@Id @GeneratedValue(IDENTITY)`, L21-23)
- `name: String` `@Column(nullable=false)` (L25-26)
- `email: String` `@Column(unique=true, nullable=false)` (L28-29)
- `password: String` — plain column, no `nullable=false`, no length constraint (L31). **Nullable in DB** — an OAuth/Google user can have `password = null` (consistent with `AuthProvider.GOOGLE`, see below), but nothing stops a LOCAL user from having a null password either; enforcement is only in service-layer logic if any.
- `role: Role` `@Enumerated(STRING) @Column(nullable=false)` (L33-35)
- `provider: AuthProvider` `@Enumerated(STRING)`, `@Builder.Default = LOCAL` (L37-39) — **no `nullable=false`**, but default is always applied via builder so effectively never null in practice, not DB-enforced.
- `profilePictureUrl: String` → column `profile_picture_url` (L41-42)
- `createdAt: LocalDateTime` `@CreationTimestamp` (L44-45)
- Implements Spring Security `UserDetails` directly (L19); `getAuthorities()` returns a single `ROLE_<role>` authority (L47-50); `getUsername()` returns email (L52-55). No `isEnabled`/`isAccountNonLocked` overrides — all default to `true` (interface defaults), so there is no account-disable mechanism at the entity level.
- No relationships declared on `User` itself — all reverse relationships (products, orders, cart items, etc.) are unidirectional from the child side (no `@OneToMany` back-refs anywhere on `User`).

### `Product` — `entity/Product.java`
- `id: Long` (IDENTITY)
- `seller: User` `@ManyToOne(LAZY) @JoinColumn(seller_id, nullable=false)` (L22-24)
- `name: String` `nullable=false` (L26-27)
- `description: String` `columnDefinition="TEXT"` (L29-30)
- `brand: String` — nullable, no constraint (L32)
- `category: String` `nullable=false` (L34-35) — **plain string, not an enum/FK** to `CategoryBounds`/`CategoryStats`; matching between `products.category` and `category_bounds.category`/`category_stats.category` is done by string equality at the service layer (no DB-level referential integrity).
- `weight: Double`, `freightValue: Double`, `photosQty: Integer` — all nullable, no constraints (L37-41)
- `imageUrls: List<String>` via `@ElementCollection` + `@CollectionTable(name="product_images", joinColumns=@JoinColumn(product_id))`, column `image_url` (L43-47) — this creates a **separate table** `product_images(product_id, image_url)`, not a JPA entity/repository (Hibernate manages it internally; no dedicated repository exists for it — see dead-model note below, this is expected, not dead).
- `price: BigDecimal` `precision=10, scale=2` (L49-50) — **nullable**, no `nullable=false`; a product can exist with `price = null` (consistent with DRAFT/PENDING_REVIEW products awaiting a pricing decision).
- `status: ProductStatus` `@Enumerated(STRING)`, `@Builder.Default = DRAFT` (L52-54) — **no `nullable=false`** at DB level (relies on builder default only).
- `createdAt: LocalDateTime` `@CreationTimestamp` (L56-57)
- No `updatedAt`/version column — **no optimistic locking (`@Version`)** anywhere on `Product`, despite it being mutated by concurrent actors (seller edits, admin approval, pricing pipeline). Concurrent price updates could silently overwrite each other.
- No `@OneToMany` back-references to `Order`, `CartItem`, `PricingRequest`, etc. — all navigation is one-directional from those child entities via `product_id` FK.

### `CartItem` — `entity/CartItem.java`
- `id: Long` (IDENTITY)
- `buyer: User` `@ManyToOne(LAZY) @JoinColumn(buyer_id, nullable=false)` (L20-22)
- `product: Product` `@ManyToOne(LAZY) @JoinColumn(product_id, nullable=false)` (L24-26)
- `addedAt: LocalDateTime` `@CreationTimestamp` (L28-29)
- Table-level: `@UniqueConstraint(columnNames={"buyer_id","product_id"})` (L10-12) — DB-enforced: one cart row per (buyer, product) pair. No quantity field — cart is effectively a "distinct product set," not quantity-tracked (add-again is presumably a no-op/upsert at service level, not visible from the entity).

### `SavedProduct` (wishlist) — `entity/SavedProduct.java`
- Same shape as `CartItem`: `buyer`, `product` (both `@ManyToOne(LAZY)`, both `nullable=false`), `savedAt` (`@CreationTimestamp`).
- Same `@UniqueConstraint(columnNames={"buyer_id","product_id"})` (L10-12) on table `saved_products`.

### `Order` — `entity/Order.java`
- `buyer: User` `@ManyToOne(LAZY) nullable=false` (L19-21)
- `product: Product` `@ManyToOne(LAZY) nullable=false` (L23-25)
- `priceAtPurchase: BigDecimal` `precision=10,scale=2` (L27-28) — **nullable**, no `nullable=false`; an order can be persisted with a null purchase price.
- `createdAt: LocalDateTime` `@CreationTimestamp` (L30-31)
- No `status` field (no PENDING/SHIPPED/CANCELLED lifecycle) — this is a minimal "purchase record," not a full order-management entity. No quantity field either — implies one row per unit purchased, unconfirmed from entity alone.

### `PricingRequest` — `entity/PricingRequest.java`
- `product: Product` `@ManyToOne(LAZY) @JoinColumn(product_id)` — **no `nullable=false`** (L20-22), unlike every other FK in the codebase; a `PricingRequest` can in principle be orphaned from any product at the DB level.
- `suggestedPrice`, `sellerPrice`, `mlBaselinePrice`, `marketPriceMin`, `marketPriceMax`: all `BigDecimal precision=10,scale=2`, all nullable (L24-28, 53-60)
- `sellerReasoning: String` `TEXT` (L30-31)
- `status: PricingRequestStatus` `@Enumerated(STRING)`, `@Builder.Default = PENDING` (L33-35) — no `nullable=false`
- LLM-derived free-text fields: `brand`, `llmConfidence` (plain `String`, not enum — despite reading like a confidence band) (L38-39), `condition` (`TEXT`, despite a `Condition` enum existing in `enums/Condition.java` — **the enum is never referenced by this entity**, meaning `Condition.from(...)` conversion, if used, is only ever applied transiently in the service layer and the DB stores the raw/normalized string, not the enum type), `conditionNotes` (`TEXT`), `conditionGrade` → column `condition_grade`, `reasoning` (`TEXT`)
- `createdAt: LocalDateTime` `@CreationTimestamp` (L62-63)
- This is the single row-per-request "pricing decision" record. No explicit link back to `PricingHistory` or `ApprovedDecision`.

### `PricingHistory` — `entity/PricingHistory.java`
- `product: Product` `@ManyToOne(LAZY) nullable=false` (L19-21)
- `oldPrice`, `newPrice`: `BigDecimal precision=10,scale=2`, nullable (L23-27)
- `changedAt: LocalDateTime` `@CreationTimestamp` (L29-30)
- **Dead / never written** — see §5. Only method on its repository is `deleteByProduct` (cascade cleanup), and that repository method is never called either (see below) — grep across the whole `src/main/java` tree found zero calls to any `pricingHistoryRepository.<method>(...)`. The field is injected (`@RequiredArgsConstructor`-style `private final PricingHistoryRepository pricingHistoryRepository;`) in both `ProductServiceImpl` (L51) and `AdminServiceImpl` (L58) but unused in both — likely dead code / an unfinished audit-log feature.

### `ApprovedDecision` — `entity/ApprovedDecision.java`
- `brand: String nullable=false`, `category: String nullable=false` (L19-23)
- `approvedMin`, `approvedMax`: `BigDecimal precision=10,scale=2` (L25-29)
- `createdAt: LocalDateTime @CreationTimestamp` (L31-32)
- No FK to `Product`/`PricingRequest` — this is a standalone "we've approved this brand+category price band before" audit/precedent record, written by `AdminServiceImpl` (L165, on approval) and read in bulk by `CacheWarmupService` (L26, `findAll()`) and counted in `AdminServiceImpl` (L137, `count()` for stats). Confirmed live (not dead).

### `CategoryBounds` — `entity/CategoryBounds.java`
- `category: String` `@Column(unique=true, nullable=false)` (L17-18)
- `minPrice`, `maxPrice`: `BigDecimal precision=10,scale=2` (L20-24)
- No `createdAt`/`id` beyond surrogate key. One row per category — used by `RoutingServiceImpl.java:56` (`categoryBoundsRepository.findByCategory(...)`, confirmed live) to bound/clamp final prices, and referenced in `AdminServiceImpl` (presumably CRUD management of bounds).

### `CategoryStats` — `entity/CategoryStats.java`
- `category: String @Column(unique=true, nullable=false)` (L15-16)
- `avgPrice: Double`, `avgReview: Double`, `medianSalesCount: Integer`, `mostCommonPaymentType: String`, `defaultMaxInstallments: Integer` (L18-22)
- No relationships. Confirmed live — used in `PricingServiceImpl` and `FeatureBuilderServiceImpl`/`FeatureBuilderService` (feature-building for the ML/LLM pricing pipeline).

## 2. Enums

| Enum | File | Values |
|---|---|---|
| `ProductStatus` | `enums/ProductStatus.java:4` | `PENDING_REVIEW, LIVE, REJECTED, DRAFT, DELETED` |
| `Role` | `enums/Role.java:4` | `BUYER, SELLER, ADMIN` |
| `AuthProvider` | `enums/AuthProvider.java:4` | `LOCAL, GOOGLE` |
| `Condition` | `enums/Condition.java:4` | `NEW, USED, REFURBISHED, UNKNOWN` — plus a static `from(String)` factory (L6-13) that upper-cases/trims and falls back to `UNKNOWN` on any parse failure or null/blank input (never throws) |
| `PricingRequestStatus` | `enums/PricingRequestStatus.java:4` | `PENDING, APPROVED, REJECTED` |

**Discrepancy vs. the GP proposal's stated product lifecycle** (`DRAFT → PENDING_REVIEW/PENDING_ADMIN/PENDING_SELLER → LIVE/REJECTED/DELETED`): the actual `ProductStatus` enum has only **one** pending state, `PENDING_REVIEW` — there is no `PENDING_ADMIN` or `PENDING_SELLER` value anywhere in the enum or codebase (confirmed by reading the full enum body). Whatever branching between admin-review and seller-negotiation states exists must be modeled by `PricingRequestStatus` on `PricingRequest` instead, not by additional `ProductStatus` values. This should be flagged clearly in the full report as a docs/code mismatch.

## 3. Repositories

### `UserRepository` (`repository/UserRepository.java`)
- `findByEmail(String)` → `Optional<User>` — derived, exact match on `email` column. Used for login/auth lookup.
- `existsByEmail(String)` → `boolean` — derived, existence check (registration duplicate-check).
- `countByRole(Role)` → `long` — derived, count of users per role (admin stats).

### `ProductRepository` (`repository/ProductRepository.java`)
- `findBySeller(User)` → `List<Product>` — all products owned by a seller, no status filter.
- `findBySellerAndStatusNot(User, ProductStatus)` → `List<Product>` — seller's products excluding one status (e.g., excluding `DELETED`).
- `findByIdAndSeller(Long, User)` → `Optional<Product>` — ownership-checked single lookup (used to enforce a seller can only fetch/mutate their own product).
- `countByStatus(ProductStatus)` → `long` — count for admin stats (e.g., how many `PENDING_REVIEW`).
- `findByStatus(ProductStatus)` → `List<Product>` — unpaged list by status.
- `findAllByOrderByCreatedAtDesc()` → `List<Product>` — unpaged, newest-first, no filter.
- `findByStatusOrderByCreatedAtDesc(ProductStatus)` → `List<Product>` — unpaged, filtered + sorted.
- `findByStatus(ProductStatus, Pageable)` → `Page<Product>` — paged variant.
- `findAllByOrderByCreatedAtDesc(Pageable)` → `Page<Product>` — paged, unfiltered.
- `findByStatusOrderByCreatedAtDesc(ProductStatus, Pageable)` → `Page<Product>` — paged + filtered + sorted.
- `findAllByOrderByCreatedAtDescWithSeller(Pageable)` (L27-29) — `@Query("SELECT p FROM Product p JOIN FETCH p.seller ORDER BY p.createdAt DESC")`, explicit `countQuery="SELECT COUNT(p) FROM Product p"` — **JOIN FETCH to avoid N+1** when listing all products with seller info; paged.
- `findByStatusOrderByCreatedAtDescWithSeller(ProductStatus, Pageable)` (L31-35) — same JOIN FETCH pattern, filtered by status, with matching explicit `countQuery`.
- `calculateRevenueForSeller(User)` → `Double` (L37-38) — `@Query("SELECT COALESCE(SUM(o.priceAtPurchase), 0) FROM Order o WHERE o.product.seller = :seller")` — note this query lives on `ProductRepository` but actually queries the `Order` entity; returns `0` (not `null`) via `COALESCE` when a seller has no orders.

Both paged+unpaged, both fetch-joined+non-fetch-joined variants of "list by status/all" coexist (5 overlapping method families for essentially the same list). This is duplication — worth flagging in the code-quality section as unnecessary API surface, since the non-JOIN-FETCH variants are the N+1-prone ones and it's unclear from the repository alone whether they're still called anywhere (needs cross-check against the service-layer fork's findings).

### `CartItemRepository` (`repository/CartItemRepository.java`)
- `findByBuyer(User)` → `List<CartItem>` — plain, will N+1 on `product`/`seller` lazy access if iterated.
- `findByBuyerWithProductAndSeller(User)` (L16-20) — `@Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product p JOIN FETCH p.seller WHERE ci.buyer = :buyer")` — the N+1-safe variant, two-level JOIN FETCH (cart→product→seller).
- `findByBuyerAndProduct(User, Product)` → `Optional<CartItem>` — existence/lookup for add-to-cart upsert logic.
- `deleteByBuyerAndProduct(User, Product)` → derived delete, single cart line removal.
- `deleteAllByBuyer(User)` → derived bulk delete, e.g. clear-cart or checkout.
- `deleteByProduct(Product)` → derived bulk delete — cascade cleanup when a product is deleted, invoked from product-deletion flow.

### `SavedProductRepository` (`repository/SavedProductRepository.java`)
- `findByBuyer(User)` → `List<SavedProduct>` — **no JOIN FETCH variant exists here** (unlike cart) — wishlist listing is a plausible N+1 site if the service iterates and touches `.getProduct()`/`.getProduct().getSeller()`. Flag for the service-layer fork to confirm.
- `findByBuyerAndProduct`, `deleteByBuyerAndProduct`, `deleteByBuyer`, `deleteByProduct` — same shape as cart's non-fetch methods.

### `OrderRepository` (`repository/OrderRepository.java`)
- `findByBuyer(User)` → `List<Order>` — plain, no fetch join (N+1 risk on `product`/`buyer` if accessed).
- `findByBuyerIdOrderByCreatedAtDesc(Long)` → `List<Order>` — buyer's order history, newest first, unpaged (no `Page` variant exists for order history — flag as missing pagination).
- `calculateRevenueForSeller(User)` (L16-17) — identical JPQL to the one duplicated on `ProductRepository` (`SELECT COALESCE(SUM(o.priceAtPurchase),0) FROM Order o WHERE o.product.seller = :seller`) — **this exact query is defined twice, verbatim, on two different repositories** (`OrderRepository.java:16-17` and `ProductRepository.java:37-38`). Worth flagging as duplication in code-quality section.
- `countByProductSeller(User)` → `long` — derived, traverses `product.seller` path — count of orders for a seller's products (distinct from user's own order count).
- `deleteByProduct(Product)` → cascade cleanup on product deletion.

### `PricingRequestRepository` (`repository/PricingRequestRepository.java`)
- `findByStatus(PricingRequestStatus)` → `List<PricingRequest>` — unpaged, e.g. all `PENDING` requests for admin review queue. **No pagination** on what could become an unbounded admin queue list.
- `findByStatusAndProduct_Status(PricingRequestStatus, ProductStatus)` (L14) — derived query traversing the `product` association's `status` field — e.g. pending pricing requests whose product is still in a specific product-status.
- `findTopByProductOrderByCreatedAtDesc(Product)` → `Optional<PricingRequest>` — most recent pricing request for a given product (derived `Top`/`First` + ordering).
- `findByProduct(Product)` → `List<PricingRequest>` — full history of requests for one product.
- `findByProductIn(List<Product>)` → `List<PricingRequest>` — batch lookup across many products (likely used to avoid N+1 when listing a seller's/admin's products with their latest pricing request — needs service-layer confirmation of actual usage pattern).
- `deleteByProduct(Product)` → cascade cleanup.

### `PricingHistoryRepository` (`repository/PricingHistoryRepository.java`)
- Only method: `deleteByProduct(Product)` → derived delete. **No `save`, no `findBy*` at all** — this repository can delete rows for a product but the codebase has no code path that ever inserts a row in the first place (see §5), so in practice `deleteByProduct` always deletes zero rows.

### `CategoryBoundsRepository` (`repository/CategoryBoundsRepository.java`)
- `findByCategory(String)` → `Optional<CategoryBounds>` — single lookup by category name (string match against `Product.category`, no FK).

### `CategoryStatsRepository` (`repository/CategoryStatsRepository.java`)
- `findByCategory(String)` → `Optional<CategoryStats>` — same pattern.

### `ApprovedDecisionRepository` (`repository/ApprovedDecisionRepository.java`)
- No custom methods — only inherits `JpaRepository<ApprovedDecision, Long>` (i.e., `save`, `findAll`, `count`, etc.). All usages confirmed are via these inherited methods (`.count()`, `.save(...)`, `.findAll()` — see §1).

## 4. Reconstructed DB Schema

> Types inferred from Java field type + `@Column`/`precision/scale` annotations under default Hibernate PostgreSQL dialect mapping. `IDENTITY` generation ⇒ Postgres `BIGSERIAL`/`GENERATED BY DEFAULT AS IDENTITY` depending on Hibernate version defaults.

**users**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| name | varchar | NOT NULL |
| email | varchar | NOT NULL, UNIQUE |
| password | varchar | nullable |
| role | varchar | NOT NULL (enum string) |
| provider | varchar | nullable (enum string, app-level default `LOCAL`) |
| profile_picture_url | varchar | nullable |
| created_at | timestamp | set on insert (Hibernate-managed, not DB default) |

**products**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| seller_id | bigint | NOT NULL, FK → users.id |
| name | varchar | NOT NULL |
| description | text | nullable |
| brand | varchar | nullable |
| category | varchar | NOT NULL (no FK) |
| weight | double precision | nullable |
| freight_value | double precision | nullable |
| photos_qty | integer | nullable |
| price | numeric(10,2) | nullable |
| status | varchar | nullable at DB level (app default DRAFT) |
| created_at | timestamp | set on insert |

**product_images** (implicit `@ElementCollection` table, not an entity)
| column | type | constraints |
|---|---|---|
| product_id | bigint | FK → products.id |
| image_url | varchar | — |

**cart_items**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| buyer_id | bigint | NOT NULL, FK → users.id |
| product_id | bigint | NOT NULL, FK → products.id |
| added_at | timestamp | set on insert |
| — | — | UNIQUE(buyer_id, product_id) |

**saved_products**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| buyer_id | bigint | NOT NULL, FK → users.id |
| product_id | bigint | NOT NULL, FK → products.id |
| saved_at | timestamp | set on insert |
| — | — | UNIQUE(buyer_id, product_id) |

**orders**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| buyer_id | bigint | NOT NULL, FK → users.id |
| product_id | bigint | NOT NULL, FK → products.id |
| price_at_purchase | numeric(10,2) | nullable |
| created_at | timestamp | set on insert |

**pricing_requests**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| product_id | bigint | **nullable**, FK → products.id (only FK in the schema without `nullable=false`) |
| suggested_price | numeric(10,2) | nullable |
| seller_price | numeric(10,2) | nullable |
| seller_reasoning | text | nullable |
| status | varchar | nullable at DB level (app default PENDING) |
| brand | varchar | nullable |
| llm_confidence | varchar | nullable |
| condition | text | nullable |
| condition_notes | text | nullable |
| condition_grade | varchar | nullable |
| reasoning | text | nullable |
| ml_baseline_price | numeric(10,2) | nullable |
| market_price_min | numeric(10,2) | nullable |
| market_price_max | numeric(10,2) | nullable |
| created_at | timestamp | set on insert |

**pricing_history**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| product_id | bigint | NOT NULL, FK → products.id |
| old_price | numeric(10,2) | nullable |
| new_price | numeric(10,2) | nullable |
| changed_at | timestamp | set on insert |

*(Table exists and would be created by Hibernate DDL/migration, but nothing ever inserts into it — see §5.)*

**approved_decisions**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| brand | varchar | NOT NULL |
| category | varchar | NOT NULL |
| approved_min | numeric(10,2) | nullable |
| approved_max | numeric(10,2) | nullable |
| created_at | timestamp | set on insert |

**category_bounds**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| category | varchar | NOT NULL, UNIQUE |
| min_price | numeric(10,2) | nullable |
| max_price | numeric(10,2) | nullable |

**category_stats**
| column | type | constraints |
|---|---|---|
| id | bigint | PK, identity |
| category | varchar | NOT NULL, UNIQUE |
| avg_price | double precision | nullable |
| avg_review | double precision | nullable |
| median_sales_count | integer | nullable |
| most_common_payment_type | varchar | nullable |
| default_max_installments | integer | nullable |

## 5. Dead Data Model Check

Cross-referenced every entity/repository against `src/main/java/com/ecommerce/service/**` and `src/main/java/com/ecommerce/controller/**`.

| Entity | Repository injected? | Actually called (beyond field decl)? | Verdict |
|---|---|---|---|
| User | yes (everywhere) | yes | live |
| Product | yes (everywhere) | yes | live |
| CartItem | yes (`CartServiceImpl`, `ProductServiceImpl`, `ProductPersistenceHelper`) | yes | live |
| SavedProduct | yes (`WishlistServiceImpl`) | yes | live |
| Order | yes (`BuyerServiceImpl`, `AdminServiceImpl`, `ProductServiceImpl`) | yes | live |
| PricingRequest | yes (`PricingServiceImpl`, `AdminServiceImpl`, `RoutingServiceImpl`) | yes | live |
| ApprovedDecision | yes (`AdminServiceImpl`, `CacheWarmupService`) | yes — `.save()` at `AdminServiceImpl.java:165`, `.count()` at `:137`, `.findAll()` at `CacheWarmupService.java:26` | live |
| CategoryBounds | yes (`RoutingServiceImpl`, `AdminServiceImpl`) | yes — `.findByCategory()` at `RoutingServiceImpl.java:56` | live |
| CategoryStats | yes (`PricingServiceImpl`, `FeatureBuilderServiceImpl`) | yes | live |
| **PricingHistory** | yes (`ProductServiceImpl.java:51`, `AdminServiceImpl.java:58`) | **no** — grep for `pricingHistoryRepository\.\w+\(` across the entire `src/main/java` tree returns **zero matches**. The field is declared `private final` in both services (so it compiles and is wired by Spring DI) but never invoked. | **effectively dead** |

**Key finding: `PricingHistory` / `pricing_history` table is dead weight.** The entity, its table, and its repository all exist and are wired into two services via constructor injection, but no code path ever calls `save`, `deleteByProduct`, or any other method on `PricingHistoryRepository`. This directly undercuts the GP proposal's non-functional requirement that "every prediction event [be] logged... for audit" (see the master report's §8 cross-check) — the entity that looks purpose-built for that audit trail (`old_price`/`new_price`/`changed_at`) is never populated. The actual audit trail that *is* populated is `PricingRequest` (one row per pricing decision, with LLM/ML fields), which is a different and less price-history-shaped record — it doesn't track a running old→new price timeline the way `PricingHistory` implies it should.

## Notes for other forks / final report
- `PricingRequest.product` is the only FK in the entire schema that is nullable — worth checking in the service-layer audit whether a `PricingRequest` can legitimately be created/persisted without a product, or whether this is just a missed `nullable=false`.
- Duplicate JPQL: `calculateRevenueForSeller` is defined identically on both `ProductRepository:37-38` and `OrderRepository:16-17`. Check which one (if either, or both) is actually called by `AdminServiceImpl`/service layer.
- `ProductStatus` enum has only `PENDING_REVIEW` as a pending state — no `PENDING_ADMIN`/`PENDING_SELLER` values exist anywhere in code, contradicting the product-lifecycle description in the task prompt/docs. The service-layer fork should confirm how admin-review vs. seller-negotiation branching is actually modeled (likely via `PricingRequestStatus` on `PricingRequest`, not via additional `ProductStatus` values).
- No entity has `@Version` (optimistic locking) — flag under code-quality/production-readiness in the final report, especially for `Product.price`/`status` which are mutated by multiple actors (seller, admin, pricing pipeline).
- Several `ProductRepository` list methods (paged/unpaged, fetch-joined/not) look like overlapping/redundant API surface — confirm actual call sites in the service-layer audit before flagging as dead code.
