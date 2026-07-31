# Security & Error Handling Audit

Scope: `SecurityConfig`, `JwtAuthFilter`, `RateLimitingFilter`, `RestTemplateConfig`, `JwtUtil`, `AuthService`/`AuthServiceImpl`/`UserDetailsServiceImpl`, `GlobalExceptionHandler` + custom exceptions, `Role` enum, `User` entity (for `UserDetails` implementation), `application.properties`.

---

## 1. JWT implementation

**File:** `src/main/java/com/ecommerce/util/JwtUtil.java`

- Signing algorithm: HMAC-SHA (`Jwts.builder()...signWith(signingKey())`), key built via `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))` (JwtUtil.java:64-66) — algorithm is inferred from key length by jjwt (HS256/384/512).
- Secret source: `@Value("${app.jwt.secret}")` (JwtUtil.java:17-18), bound to `application.properties:29`:
  ```
  app.jwt.secret=${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
  ```
  **This is an env-var-overridable property, but the fallback default is a real-looking 64-char hex secret checked into source control.** If `JWT_SECRET` is not set in any environment (e.g., a fresh clone, CI, or a misconfigured deploy), the app silently signs tokens with this hardcoded value, which is now public in git history. This is the single most important finding — see §5.
- Claims: only `subject` (email) and `issuedAt`/`expiration` are set (JwtUtil.java:56-62). **No role/authority claim, no token type ("access" vs "refresh") claim, no `jti`.** Access and refresh tokens are structurally identical — same claim shape, same signing key — the only difference is TTL. Nothing in the token itself lets a server distinguish a refresh token from an access token; a refresh token can be used directly as a Bearer access token until it expires (7 days) since `JwtAuthFilter` only checks email + expiry (JwtAuthFilter.java:38-49), not any "type" claim (there isn't one).
- Expiry: `application.properties:30-31`:
  ```
  app.jwt.expiration=86400000        # 24h access token
  app.jwt.refresh-expiration=604800000   # 7 days refresh token
  ```
- Validation: `JwtUtil.isValid(token, email)` (JwtUtil.java:38-40) checks `extractEmail(token).equals(email) && !isExpired(token)`. Signature verification happens implicitly inside `extractClaim` via `Jwts.parser().verifyWith(signingKey())...parseSignedClaims(token)` (JwtUtil.java:46-53) — an invalid signature throws `JwtException` before `isValid` is ever reached.
- No token revocation/blacklist mechanism exists anywhere (grepped only within scope files) — logout is client-side only; a leaked access token is valid for up to 24h and a leaked refresh token for 7 days with no way to invalidate it server-side.

**Refresh flow:** `AuthServiceImpl.refresh(String refreshToken)` (AuthServiceImpl.java:62-78) extracts email from the refresh token, loads the user, re-validates the refresh token, and issues a **new access token** while **echoing back the same refresh token** (`refreshToken(refreshToken)` at line 72) rather than rotating it. No refresh-token rotation, no reuse detection.

---

## 2. Password hashing

`SecurityConfig.passwordEncoder()` (SecurityConfig.java:71-74):
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
Default `BCryptPasswordEncoder()` constructor → strength factor **10** (library default), no custom cost factor configured. Used in `AuthServiceImpl.register()` via `passwordEncoder.encode(request.getPassword())` (AuthServiceImpl.java:43) and implicitly by `DaoAuthenticationProvider` during `login()` (AuthServiceImpl.java:54-56, provider wired in SecurityConfig.java:59-64).

---

## 3. Filter chain & authorization rules

**File:** `SecurityConfig.java:33-56`

Filter order (both added `before UsernamePasswordAuthenticationFilter`, registration order in code = execution order for `addFilterBefore` at the same anchor):
1. `rateLimitingFilter` (SecurityConfig.java:52)
2. `jwtAuthFilter` (SecurityConfig.java:53)
3. `UsernamePasswordAuthenticationFilter` (Spring default, unused in practice since auth is stateless/JWT — login goes through `AuthenticationManager.authenticate(...)` called directly in `AuthServiceImpl.login`, not this filter)

CSRF disabled, session policy `STATELESS` (SecurityConfig.java:35-36) — consistent with pure-JWT stateless auth.

**`authorizeHttpRequests` rules, verbatim order (SecurityConfig.java:37-49):**
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/auth/**",
        "/api/buyer/products",
        "/api/buyer/products/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**"
    ).permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/user/profile-picture").authenticated()
    .requestMatchers(HttpMethod.POST, "/api/products/*/images").hasRole("SELLER")
    .anyRequest().authenticated()
)
```
- `/api/auth/**` open (register/login/refresh — expected).
- `/api/buyer/products` and `/api/buyer/products/**` open for **all HTTP methods**, not just GET. There is no `HttpMethod.GET` restriction on this matcher — if `BuyerController` ever exposes a POST/PUT/DELETE under `/api/buyer/products/**`, it would be publicly accessible with no auth. (Cross-check against the actual `BuyerController` mappings in the controller-layer report — this rule as written permits any verb.)
- Swagger/OpenAPI docs fully public in every environment (no profile gating) — acceptable for a GP demo, a real exposure surface in production (reveals full API schema).
- `/actuator/health` public — fine, standard practice, and matches actuator exposure limited to `health` only (`management.endpoints.web.exposure.include=health`, application.properties:51).
- Two narrow method-specific rules for profile picture upload (`authenticated()`, i.e. any logged-in role) and product image upload (`hasRole("SELLER")`).
- **Everything else falls through to `anyRequest().authenticated()`** — this includes all of `/api/admin/**`, `/api/products/**` (non-image), `/api/cart/**`, `/api/pricing/**`, `/api/wishlist/**`, `/api/user/**`. Authorization at that point is **authenticated-only, not role-restricted** — role enforcement for admin-only or seller-only endpoints must come from `@PreAuthorize` at the controller/method level (not in scope for this fork; verify in the controller-layer report that every admin endpoint actually carries `@PreAuthorize("hasRole('ADMIN')")` or equivalent, because `SecurityConfig` itself does **not** enforce role separation beyond the one `hasRole("SELLER")` rule above).

---

## 4. Role enum

`src/main/java/com/ecommerce/enums/Role.java:3-5`:
```java
public enum Role {
    BUYER, SELLER, ADMIN
}
```
Exactly three roles, no sub-roles or scopes. `User.getAuthorities()` (User.java:47-50) maps this to a single `SimpleGrantedAuthority("ROLE_" + role.name())` — a user has exactly one role/authority, ever.

Note: `AuthServiceImpl.register()` (AuthServiceImpl.java:35-38) explicitly downgrades any registration request for `Role.ADMIN` to `Role.BUYER`:
```java
Role role = request.getRole();
if (role == null || role == Role.ADMIN) {
    role = Role.BUYER;
}
```
So self-registration as ADMIN is blocked — admins must be provisioned some other way (DB seed/manual promotion; not found in this scope).

---

## 5. Notable/risky patterns in SecurityConfig

- **Hardcoded fallback secrets are the top concern**, not just JWT — see application.properties:29 (JWT secret), :8 (`DB_PASSWORD:postgres123`), :44-45 (Brevo SMTP username/password), :63-64 (Cloudinary API key/secret). All follow `${ENV_VAR:hardcoded-real-looking-default}`. The externalization pattern is correct (env var takes precedence), but leaving production-shaped secrets as the *default* in a committed properties file means anyone who clones the repo (or finds it via git history, since commit `c9b22c7` claims to have "externalized secrets to env vars" but the values are still literally present) has working credentials unless every deployment target explicitly overrides all of them. This is a partial fix, not a real externalization.
- `anyRequest().authenticated()` as the catch-all means **role separation for ADMIN vs SELLER vs BUYER on most endpoints depends entirely on method-level `@PreAuthorize` annotations in controllers**, not on this central config. A missing `@PreAuthorize` on any admin controller method would silently downgrade that endpoint to "any authenticated user" — need to verify each admin/seller endpoint in the controller report has an explicit annotation.
- `/api/buyer/products/**` is `permitAll()` for **all methods**, not scoped to GET — worth confirming no mutating endpoint lives under that path prefix in `BuyerController`.
- JWT filter (`JwtAuthFilter.java:51-53`) silently swallows `JwtException` (`catch (JwtException ignored) {}`) and falls through to `chain.doFilter` unauthenticated rather than rejecting the request outright — this is actually correct behavior for a filter that must let `permitAll()` routes through even with a garbage/expired Bearer header, but it means a malformed token on a protected route doesn't get its own explicit 401 from the filter — it instead reaches `anyRequest().authenticated()` and gets rejected by Spring Security's entry point with a generic 401 (no custom message), rather than the `JwtException` handler in `GlobalExceptionHandler` (GlobalExceptionHandler.java:44-47) — that handler is effectively **dead code for this path**, since the filter never lets the exception propagate to Spring MVC/the `@RestControllerAdvice`. It would only fire if a `JwtException` were thrown somewhere else, e.g. inside a controller/service calling `JwtUtil` directly (e.g. `AuthServiceImpl.refresh`, which calls `jwtUtil.extractEmail(refreshToken)` at AuthServiceImpl.java:64 outside a try/catch — a malformed refresh token there *would* throw `JwtException` up through the controller and hit this handler).
- No CSRF protection is intentional/correct for a stateless JWT API (no cookies used for auth), so disabling it is not a flaw here.

---

## 6. CORS configuration

**No CORS configuration exists anywhere in the codebase within this scope** — no `CorsConfigurationSource` bean, no `.cors(...)` call in `SecurityConfig`, no `@CrossOrigin` annotations found (`grep -rn "[Cc]ors" src/main` returned zero matches). 

Implication: Spring Security's default behavior applies — CORS is **not enabled**, meaning cross-origin browser requests (e.g., from a separately-hosted frontend on a different origin) will be blocked by the browser unless something in front of this service (a reverse proxy, API gateway, or the frontend being served from the same origin) handles CORS headers. If the GP frontend is a separate SPA on a different origin/port, this would manifest as CORS errors in the browser console in any deployment where frontend and backend aren't same-origin — this needs to be flagged as either an intentional gap (proxy handles it) or a MISSING item, not a "misconfigured" one, since there's genuinely nothing here.

---

## 7. GlobalExceptionHandler — exception → status → body mapping

**File:** `src/main/java/com/ecommerce/exception/GlobalExceptionHandler.java`, annotated `@RestControllerAdvice` (line 16).

All non-validation responses share the shape (built by the private `error()` helper, lines 90-96):
```json
{ "timestamp": "<LocalDateTime.now().toString()>", "status": <int>, "message": "<string>" }
```

| Exception | Status | Message | Line |
|---|---|---|---|
| `EmailAlreadyExistsException` | 409 CONFLICT | `ex.getMessage()` | 19-22 |
| `TokenRefreshException` | 401 UNAUTHORIZED | `ex.getMessage()` | 24-27 |
| `BadCredentialsException` (Spring Security) | 401 UNAUTHORIZED | hardcoded `"Invalid email or password"` | 29-32 |
| `ResourceNotFoundException` | 404 NOT_FOUND | `ex.getMessage()` | 34-37 |
| `PricingException` | 503 SERVICE_UNAVAILABLE | `ex.getMessage()` | 39-42 |
| `JwtException` (jjwt) | 401 UNAUTHORIZED | hardcoded `"Invalid or expired token"` | 44-47 |
| `IllegalArgumentException` | 400 BAD_REQUEST | `ex.getMessage()` | 49-52 |
| `IllegalStateException` | 400 BAD_REQUEST | `ex.getMessage()` | 54-57 |
| `MethodArgumentNotValidException` (`@Valid` failures) | 400 | different shape: `{timestamp, status:400, errors: {field: message, ...}}` | 59-71 |
| `AccessDeniedException` (Spring Security) | 403 FORBIDDEN | hardcoded `"Access denied"` | 73-77 |
| `HttpRequestMethodNotSupportedException` | 405 METHOD_NOT_ALLOWED | `"HTTP method not supported: " + ex.getMethod()` | 79-83 |
| `Exception` (catch-all) | 500 INTERNAL_SERVER_ERROR | hardcoded `"An unexpected error occurred"` | 85-88 |

Custom exception classes (all simple `RuntimeException` subclasses with a single message constructor, no extra fields):
- `EmailAlreadyExistsException` (thrown in `AuthServiceImpl.register`, AuthServiceImpl.java:32)
- `TokenRefreshException` (thrown twice in `AuthServiceImpl.refresh`, AuthServiceImpl.java:66, 69)
- `ResourceNotFoundException` (not thrown within this fork's scope — used elsewhere in service layer, see data/service-layer report)
- `PricingException` (not thrown within this fork's scope — used in pricing pipeline, see pricing-pipeline report)

Notes:
- The generic `Exception` catch-all (line 85-88) means **any unanticipated exception (NPE, DB constraint violation, etc.) returns a bare 500 with no detail** — safe for not leaking stack traces, but makes client-side debugging/production troubleshooting harder without server-side log correlation (no error ID/trace ID included in the body).
- `IllegalArgumentException`/`IllegalStateException` → 400 is a broad catch; if any framework or library code throws these for reasons unrelated to bad client input (e.g., a misconfiguration), the client would incorrectly see "400 Bad Request" for what's actually a server-side bug.
- No `@ExceptionHandler` for `HttpMessageNotReadableException` (malformed JSON body) — falls through to the generic `Exception` handler → 500 instead of a more accurate 400.
- No `@ExceptionHandler` for `DataIntegrityViolationException` (e.g., unique constraint violation like duplicate email if the `existsByEmail` check race-conditions) — would also fall through to 500.

---

## 8. RateLimitingFilter

**File:** `src/main/java/com/ecommerce/config/RateLimitingFilter.java`

- Strategy: **in-memory, per-(method+path+client-IP) fixed-window counter**, using `ConcurrentHashMap<String, Window>` (line 45) where `Window` holds an `AtomicInteger` count and a `volatile long windowStart`, mutated inside a `synchronized(window)` block (lines 65-89).
- Hardcoded rule table (lines 38-43), exact-match on `METHOD:URI` (no wildcard/pattern matching, so this **only** applies to the literal 4 paths listed — a different path with the same abuse profile, e.g. `/api/auth/refresh`, is not covered):
  ```java
  private static final Map<String, Limit> RULES = Map.of(
          "POST:/api/auth/login",       new Limit(5,  60_000),
          "POST:/api/auth/register",    new Limit(5,  60_000),
          "POST:/api/products",         new Limit(10, 60_000),
          "POST:/api/pricing/suggest",  new Limit(5,  60_000)
  );
  ```
- Client IP resolution (lines 94-100): trusts `X-Forwarded-For` header verbatim if present, else falls back to `getRemoteAddr()`. **This trusts client-supplied `X-Forwarded-For` with no validation that the request actually came through a trusted proxy** — any client can spoof this header to reset their own rate-limit bucket to an arbitrary key, trivially bypassing the limiter. Only safe if a trusted reverse proxy strips/overwrites this header before it reaches the app; nothing in this codebase enforces that.
- On limit exceeded: HTTP 429, header `Retry-After: <seconds>`, body `{"status":429,"message":"Too many requests. Please try again in N seconds."}` (lines 80-87) — written directly, bypassing `GlobalExceptionHandler` (this filter runs before the DispatcherServlet, so it must handle its own response).
- **Multi-instance deployment**: the class's own doc comment (lines 17-24) already self-flags this — "single-instance, in-memory implementation... if the backend is ever horizontally scaled across multiple instances the per-IP limits will not be enforced globally." Confirmed accurate: `windows` is a local `ConcurrentHashMap` with no external backing store (no Redis use here despite Redis being available elsewhere in the stack for caching — see pricing/integrations report). In a load-balanced multi-instance deployment, an attacker distributed across N instances effectively gets `N ×` the intended limit, and counters reset whenever an instance restarts/redeploys.
- `@Order(1)` (line 27) plus `addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)` before `addFilterBefore(jwtAuthFilter, ...)` in `SecurityConfig` (lines 52-53) — rate limiting runs before JWT auth, so unauthenticated abusive traffic is throttled before any auth work happens, which is correct ordering.

---

## Summary of most important findings (this scope only)

1. **Hardcoded fallback secrets in `application.properties`** (JWT signing key, DB password, Brevo SMTP credentials, Cloudinary API secret) undermine the "externalize to env vars" fix from commit `c9b22c7` — the defaults are real-looking production secrets, not placeholders, and are committed to git.
2. **Refresh tokens are structurally identical to access tokens** (same claims, no `type` claim) and are not rotated on refresh — a stolen refresh token works as a bearer token for up to 7 days with no server-side revocation.
3. **RateLimitingFilter trusts unvalidated `X-Forwarded-For`** and is in-memory-only (won't scale horizontally; self-documented in the class Javadoc).
4. **No CORS configuration exists at all** — likely fine if a proxy/gateway handles it, but nothing in this codebase does.
5. Role-based access control beyond one `hasRole("SELLER")` rule is **entirely delegated to `@PreAuthorize` annotations in controllers** — `SecurityConfig` provides no defense-in-depth for ADMIN-only routes; verify controller-layer report confirms every admin/seller-restricted method is actually annotated.
