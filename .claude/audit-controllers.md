# Controller / REST API Layer Audit

## 1. Complete Endpoint Table

### AuthController — `@RequestMapping("/api/auth")` (no class-level auth)
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/auth/register | none visible (public) | RegisterRequest (@Valid) | AuthResponse | Calls `authService.register(request)` — delegates all logic to service | AuthController.java:24 |
| POST | /api/auth/login | none visible (public) | LoginRequest (@Valid) | AuthResponse | Calls `authService.login(request)` | AuthController.java:29 |
| POST | /api/auth/refresh | none visible (public) | RefreshRequest (@Valid) | AuthResponse | Calls `authService.refresh(request.getRefreshToken())` | AuthController.java:34 |

### PricingController — `@RequestMapping("/api/pricing")`
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/pricing/suggest | `@PreAuthorize("hasRole('SELLER')")` | ProductListingRequest (@Valid) | PricingSuggestionResponse | Injects authenticated seller, calls `pricingService.getSuggestion(request, seller)` — a pricing preview/dry-run separate from actual product listing | PricingController.java:24-31 |

### CartController — `@RequestMapping("/api/buyer/cart")`, class-level `@PreAuthorize("hasRole('BUYER')")`
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/buyer/cart/{productId} | hasRole('BUYER') (class-level) | none (path var only) | CartResponse | `cartService.addToCart(productId, buyer)` | CartController.java:22-27 |
| GET | /api/buyer/cart | hasRole('BUYER') | none | List\<CartResponse\> | `cartService.getCart(buyer)` — **no pagination**, but bounded to one buyer's cart so low risk | CartController.java:29-33 |
| DELETE | /api/buyer/cart/{productId} | hasRole('BUYER') | none | 204 No Content | `cartService.removeFromCart(productId, buyer)` | CartController.java:35-41 |
| DELETE | /api/buyer/cart | hasRole('BUYER') | none | 204 No Content | `cartService.clearCart(buyer)` | CartController.java:43-48 |

### UserController — `@RequestMapping("/api/user")`
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/user/profile-picture | `@PreAuthorize("isAuthenticated()")` | multipart file (no @Valid — file, not a bean) | String (URL) | `userService.uploadProfilePicture(file, user)` — no server-side check here on file type/size before calling service | UserController.java:20-27 |

### WishlistController — `@RequestMapping("/api/buyer/wishlist")`, class-level `@PreAuthorize("hasRole('BUYER')")`
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/buyer/wishlist/{productId} | hasRole('BUYER') | none | SavedProductResponse | `wishlistService.saveProduct(productId, buyer)` | WishlistController.java:22-27 |
| GET | /api/buyer/wishlist | hasRole('BUYER') | none | List\<SavedProductResponse\> | `wishlistService.getSaved(buyer)` — no pagination, bounded per-user | WishlistController.java:29-33 |
| DELETE | /api/buyer/wishlist/{productId} | hasRole('BUYER') | none | 204 | `wishlistService.unsaveProduct(productId, buyer)` | WishlistController.java:35-41 |
| DELETE | /api/buyer/wishlist | hasRole('BUYER') | none | 204 | `wishlistService.clearWishlist(buyer)` | WishlistController.java:43-48 |

### ProductController — `@RequestMapping("/api")` (seller-facing product management)
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| POST | /api/products | hasRole('SELLER') | ProductListingRequest (@Valid) | PricingSuggestionResponse | `productService.listProduct(request, seller)` — creates the actual product + kicks off pricing pipeline | ProductController.java:33-39 |
| POST | /api/products/{id}/accept | hasRole('SELLER') | AcceptPriceRequest, **`@RequestBody(required = false)`, no `@Valid`** | AcceptPriceResponse | `productService.acceptPrice(id, request, seller)` — seller accepts suggested/ML price | ProductController.java:41-48 |
| POST | /api/products/{id}/dispute | hasRole('SELLER') | DisputePriceRequest (@Valid) | DisputeResponse | `productService.disputePrice(id, request, seller)` | ProductController.java:50-57 |
| GET | /api/seller/products | hasRole('SELLER') | none | List\<ProductResponse\> | `productService.getSellerProducts(seller)` — **no pagination**; a seller with many SKUs gets an unbounded list | ProductController.java:59-64 |
| GET | /api/products/{id} | hasRole('SELLER') | none | ProductResponse | `productService.getProductById(id, seller)` | ProductController.java:66-72 |
| GET | /api/seller/dashboard | hasRole('SELLER') | none | SellerDashboardResponse | `productService.getDashboard(seller)` | ProductController.java:74-79 |
| POST | /api/products/{id}/images | hasRole('SELLER') | multipart files, manual check `files.size() > 5` → 400 (no @Valid, hand-rolled) | List\<String\> | `productService.uploadProductImages(id, files, seller)` | ProductController.java:81-93 |
| DELETE | /api/seller/products/{id} | hasRole('SELLER') | none | Map\<String,String\> | `productService.deleteProduct(id, seller)` — seller deletes own product | ProductController.java:95-102 |

### BuyerController — no class-level `@RequestMapping` (paths declared per-method with full `/api/...` prefix)
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| GET | /api/buyer/products | **none visible (public)** | query params `page`,`size` (primitives, no @Valid applicable) | Page\<BuyerProductResponse\> | `buyerService.getAllLiveProducts(pageable)` — paginated (`page` default 0, `size` default 12), sorted by createdAt desc | BuyerController.java:29-35 |
| GET | /api/buyer/products/{id} | none visible (public) | none | BuyerProductResponse | `buyerService.getProductById(id)` | BuyerController.java:37-40 |
| GET | /api/buyer/products/{id}/history | none visible (public) | none | List\<PriceHistoryResponse\> | `buyerService.getProductHistory(id)` — **no pagination**; price-history rows for a product grow unbounded over the product's lifetime | BuyerController.java:42-45 |
| POST | /api/orders | hasRole('BUYER') | OrderRequest (@Valid) | OrderResponse | `buyerService.placeOrder(request, buyer)` | BuyerController.java:47-53 |
| GET | /api/orders/my | hasRole('BUYER') | none | List\<OrderResponse\> | `buyerService.getMyOrders(buyer)` — **no pagination**; a long-lived buyer account's order history is unbounded | BuyerController.java:55-60 |
| GET | /api/orders/{orderId} | hasRole('BUYER') | none | OrderResponse | `buyerService.getOrderById(orderId, buyer)` | BuyerController.java:62-68 |

### AdminController — `@RequestMapping("/api/admin")`
| Method | Path | Auth | Request DTO | Response DTO | Behavior | Location |
|---|---|---|---|---|---|---|
| GET | /api/admin/requests | hasRole('ADMIN') | none | List\<AdminRequestResponse\> | `adminService.getPendingRequests()` — **no pagination at all** (not even query params); returns *all* pending pricing requests platform-wide, unbounded and will degrade as volume grows | AdminController.java:33-37 |
| GET | /api/admin/requests/{requestId} | hasRole('ADMIN') | none | AdminRequestResponse | `adminService.getRequestById(requestId)` | AdminController.java:39-43 |
| POST | /api/admin/approve/{requestId} | hasRole('ADMIN') | ApproveRequest (@Valid) | Map\<String,String\> | `adminService.approveRequest(requestId, request)` | AdminController.java:45-51 |
| POST | /api/admin/reject/{requestId} | hasRole('ADMIN') | RejectRequest (@Valid) | Map\<String,String\> | `adminService.rejectRequest(requestId, request)` | AdminController.java:53-59 |
| POST | /api/admin/override/{productId} | hasRole('ADMIN') | OverrideRequest (@Valid) | Map\<String,String\> | `adminService.overridePrice(productId, request)` | AdminController.java:61-67 |
| GET | /api/admin/products | hasRole('ADMIN') | query `status`,`page`,`size` | Page\<AdminProductResponse\> | `adminService.getAllProducts(status, pageable)` — paginated (default size 10), sorted createdAt desc | AdminController.java:69-77 |
| GET | /api/admin/stats | hasRole('ADMIN') | none | AdminStatsResponse | `adminService.getStats()` | AdminController.java:79-83 |
| DELETE | /api/admin/products/{id} | hasRole('ADMIN') | DeleteProductRequest, **`@RequestBody(required = false)`, no `@Valid`** | Map\<String,String\> | `adminService.deleteProduct(id, request)` — admin hard/soft-deletes any product | AdminController.java:85-92 |

**Total endpoints: 34** across 8 controllers.

---

## 2. DTO Validation Audit

| DTO | Fields & annotations | Gaps |
|---|---|---|
| **LoginRequest** | email `@Email @NotBlank`; password `@NotBlank` | No `@Size` cap on password/email — a client could POST a multi-MB string as "password"; low risk (auth service will just fail the compare) but worth a max `@Size` guard. |
| **RefreshRequest** | refreshToken `@NotBlank` | No size cap; JWTs have a natural bounded size so low risk. |
| **RegisterRequest** | name `@NotBlank @Size(2,100)`; email `@Email @NotBlank`; password `@NotBlank @Size(6,100)`; role `@NotNull` | role is bound directly to the `Role` enum — **no restriction preventing a client from registering as `ADMIN`** if that value exists in the enum (see Role enum — cross-check with security fork). This is a request-DTO-level gap regardless of what AuthServiceImpl does with it. |
| **OrderRequest** | productId `@NotNull` | No `@Positive`/`@Min(1)` — a negative or zero ID passes bean validation and only fails downstream at the repository lookup. Minor. |
| **DisputePriceRequest** | sellerPrice `@NotNull @Positive`; sellerReasoning `@NotBlank @Size(10,500)` | Well-constrained. |
| **ApproveRequest** | approvedPrice `@NotNull @Positive`; adminNote `@Size(max=500)` (optional, no `@NotBlank` — fine, it's optional) | No upper bound on `approvedPrice` (e.g. no `@Max`) — an admin could submit an absurd price; business-rule bound (if any) must be enforced in the service layer, not here. |
| **RejectRequest** | rejectionReason `@NotBlank @Size(5,500)` | Fine. |
| **MLRequest** | 21 fields, no Bean Validation annotations at all (only Jackson `@JsonProperty`) | **Intentional** — this is an internal outbound DTO built by `FeatureBuilderService` and sent *to* the ML microservice, never bound from an incoming `@RequestBody`, so `@Valid` doesn't apply here. Not a real gap for the controller layer. |
| **AcceptPriceRequest** | chosenPrice `@Positive` only (no `@NotNull`) | Field is optional (nullable) by design — presumably "accept ML/LLM-suggested price as-is" when null vs. "accept this specific price" when present. But combined with the controller's `@RequestBody(required = false)` and missing `@Valid` (see §3), **validation on this field never runs at all** even when a body is sent, since `@Valid` is absent from the controller parameter. |
| **OverrideRequest** | newPrice `@NotNull @Positive`; adminNote `@Size(max=500)` | Fine, and `@Valid` is present on this one. |
| **ProductListingRequest** | name `@NotBlank`; category `@NotBlank`; description `@NotBlank @Size(min=10)`; weight `@NotNull @Positive`; freightValue `@NotNull @PositiveOrZero`; photosQty `@NotNull @Min(1)`; condition `@NotBlank`; conditionNotes (**no validation**); conditionGrade (**no validation**) | Flags: (1) `name` has no `@Size` upper bound — a client can submit an arbitrarily large string as a product name. (2) `description` has a min length but **no max** — same unbounded-payload risk. (3) `category` is a free-text `@NotBlank` string with no enum/whitelist check at the DTO level — nothing stops a typo'd or nonsense category from reaching the pricing pipeline (category-bounds lookups downstream may silently miss). (4) `conditionNotes` / `conditionGrade` have zero constraints — completely free-form, unbounded strings. |
| **DeleteProductRequest** | reason (**no validation**, no `@NotBlank`) | Only field is unconstrained; combined with controller-level `@RequestBody(required = false)` and no `@Valid` (see §3), the whole object is effectively unvalidated by design — acceptable for an optional "reason" field, but worth noting it's just a bare string with no size cap either. |

---

## 3. Endpoints Missing `@Valid` on `@RequestBody`

Three request bodies are received without `@Valid`, all consistently paired with `required = false` (i.e., they're optional bodies, not typos), but this means **any Bean Validation annotations present on those DTOs are dead code / never enforced**:

1. `ProductController.acceptPrice` — `AcceptPriceRequest request` — **no `@Valid`** (ProductController.java:45). The `@Positive` on `AcceptPriceRequest.chosenPrice` never fires; a negative `chosenPrice` will pass the controller and reach `productService.acceptPrice` unchecked.
2. `AdminController.deleteProduct` — `DeleteProductRequest request` — no `@Valid` (AdminController.java:90). Low impact since the DTO has no constraints anyway.
3. `AdminController.approveRequest`, `rejectRequest`, `overridePrice`, and `ProductController.disputePrice`, `listProduct`, and `AuthController`'s three endpoints, `BuyerController.placeOrder`, `PricingController.suggest` **do** correctly use `@Valid` — only the two `required = false` bodies above lack it.

**Actionable gap:** add `@Valid` to `acceptPrice`'s `AcceptPriceRequest` parameter so the existing `@Positive` constraint actually runs when a body is supplied.

---

## 4. Pagination Audit

Paginated (uses `Page<T>` + `Pageable`):
- `BuyerController.getAllProducts` (`/api/buyer/products`) — page/size query params, default size 12.
- `AdminController.getAllProducts` (`/api/admin/products`) — page/size query params, default size 10.

**Not paginated, returns raw `List<T>`:**
- `CartController.getCart` — bounded by nature (one buyer's cart), low risk.
- `WishlistController.getSaved` — bounded by nature (one buyer's saved list), low risk.
- `ProductController.getSellerProducts` (`/api/seller/products`) — **unbounded**, grows with a seller's total catalog size, no page/size params.
- `BuyerController.getProductHistory` (`/api/buyer/products/{id}/history`) — **unbounded**, price-history rows accumulate over a product's whole lifetime and this is a public, unauthenticated endpoint.
- `BuyerController.getMyOrders` (`/api/orders/my`) — **unbounded**, grows with a buyer's full order history.
- `AdminController.getPendingRequests` (`/api/admin/requests`) — **unbounded and platform-wide** (not scoped to one user) — this is the highest-risk missing-pagination case: as pricing-request volume grows this single endpoint returns every pending request in the system in one response, with no query params at all.

---

## 5. Verb/Path/Behavior Mismatches

- No GET endpoints were found to mutate state — all state changes are behind POST/DELETE, which is correct.
- `ProductController.acceptPrice` and `disputePrice` are both `POST /products/{id}/accept` and `POST /products/{id}/dispute` — action-style URLs rather than resource-style REST, but this is a consistent, intentional convention used throughout (`/approve/{id}`, `/reject/{id}`, `/override/{id}` in AdminController use the same style), not an inconsistency.
- `BuyerController` has no class-level `@RequestMapping`, unlike every other controller — each method repeats the full `/api/...` prefix. Not a bug, but an inconsistency in style versus e.g. `AdminController`/`CartController`/`WishlistController`, which factor the prefix into `@RequestMapping` at the class level.
- `ProductController` mixes seller-product-management paths under two different prefixes in one class: `/api/products/...` and `/api/seller/...` — no functional bug, but inconsistent with the one-prefix-per-controller pattern elsewhere.

---

## Notable cross-cutting observation (outside strict scope, flagged briefly)

`BuyerController`'s three product-browsing GETs (`/api/buyer/products`, `/api/buyer/products/{id}`, `/api/buyer/products/{id}/history`) have **no `@PreAuthorize` and no class-level security annotation** — they appear intentionally public (browsing a storefront without login is normal), but this should be cross-checked against `SecurityConfig`'s permit-all list by the security-focused fork to confirm it's an intentional public route and not a gap.
