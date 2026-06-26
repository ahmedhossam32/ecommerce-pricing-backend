package com.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitingFilter.
 *
 * All tests instantiate the filter directly and drive it with Mockito mocks — no Spring
 * context needed.  The window-reset test backdates a window via reflection instead of
 * sleeping 60 s; production code is intentionally left unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // some @BeforeEach stubs are unused in non-429 tests
@DisplayName("RateLimitingFilter — Unit Tests")
class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain         chain;

    private StringWriter responseBody;
    private PrintWriter  printWriter;

    @BeforeEach
    void setUp() throws Exception {
        filter       = new RateLimitingFilter();
        responseBody = new StringWriter();
        printWriter  = new PrintWriter(responseBody);
        when(response.getWriter()).thenReturn(printWriter);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Stubs a request mock for direct IP (no X-Forwarded-For). */
    private void stubRequest(HttpServletRequest req, String method, String uri, String remoteAddr) {
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
    }

    /** Stubs a request mock with an X-Forwarded-For header value. */
    private void stubRequestXff(HttpServletRequest req, String method, String uri, String xff) {
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
    }

    /** Drives the filter {@code times} times using the shared request/response/chain mocks. */
    private void hit(HttpServletRequest req, HttpServletResponse resp, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            filter.doFilterInternal(req, resp, chain);
        }
    }

    /**
     * Backdates the existing Window for {@code key} by 61 s so the filter's next call
     * treats it as expired.  Uses reflection to avoid adding a Clock seam to production
     * code — see task note (b).
     */
    @SuppressWarnings("unchecked")
    private void expireWindow(String key) throws Exception {
        Field windowsField = RateLimitingFilter.class.getDeclaredField("windows");
        windowsField.setAccessible(true);
        ConcurrentHashMap<String, Object> windows =
                (ConcurrentHashMap<String, Object>) windowsField.get(filter);

        Object window = windows.get(key);
        assertThat(window).as("window for key '%s' must exist before expiry", key).isNotNull();

        Field wsField = window.getClass().getDeclaredField("windowStart");
        wsField.setAccessible(true);
        wsField.setLong(window, System.currentTimeMillis() - 61_000L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Requests under the limit pass through
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5 requests to POST /api/auth/login — all pass through, none are 429")
    void requestsUnderLimit_allPassThrough() throws Exception {
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.1");
        hit(request, response, 5);

        verify(chain, times(5)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. 6th request is blocked with correct 429 payload
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6th request to POST /api/auth/login returns 429; first 5 pass")
    void sixthRequest_isBlocked() throws Exception {
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.2");
        hit(request, response, 6);

        verify(chain, times(5)).doFilter(request, response);  // only 5 reached the chain
        verify(response, times(1)).setStatus(429);             // exactly one 429
    }

    @Test
    @DisplayName("429 response body contains 'Too many requests'")
    void rateLimitedResponse_bodyContainsMessage() throws Exception {
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.3");
        hit(request, response, 6);

        printWriter.flush();
        assertThat(responseBody.toString()).contains("Too many requests");
    }

    @Test
    @DisplayName("429 response has Retry-After header set to a positive integer (seconds)")
    void rateLimitedResponse_hasPositiveRetryAfterHeader() throws Exception {
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.4");
        hit(request, response, 6);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Retry-After"), captor.capture());
        assertThat(Integer.parseInt(captor.getValue()))
                .as("Retry-After must be a positive integer")
                .isPositive();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Window resets after expiry (reflection-based, no real sleep)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Request after window expiry succeeds — counter has been reset (no real sleep)")
    void windowExpiry_resetsCounterAndAllowsNextRequest() throws Exception {
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.5");

        // Fill the window to its limit
        hit(request, response, 5);
        verify(chain, times(5)).doFilter(request, response);

        // Simulate 61 s passing by backdating the stored window via reflection
        expireWindow("POST:/api/auth/login:10.0.0.5");

        // First request in the new window must pass through, not 429
        filter.doFilterInternal(request, response, chain);

        verify(chain, times(6)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Per-IP isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Exhausting IP-A's limit does not consume any of IP-B's allowance")
    void perIpIsolation_differentIPsAreTrackedIndependently() throws Exception {
        HttpServletRequest  reqA  = mock(HttpServletRequest.class);
        HttpServletRequest  reqB  = mock(HttpServletRequest.class);
        HttpServletResponse respA = mock(HttpServletResponse.class);
        HttpServletResponse respB = mock(HttpServletResponse.class);

        when(respA.getWriter()).thenReturn(printWriter);

        stubRequestXff(reqA, "POST", "/api/auth/login", "192.168.1.1");
        stubRequestXff(reqB, "POST", "/api/auth/login", "192.168.1.2");

        // IP-A: 5 pass, 6th is blocked
        hit(reqA, respA, 6);
        verify(respA, times(1)).setStatus(429);
        verify(chain, times(5)).doFilter(reqA, respA);

        // IP-B: first request should pass through without any block
        filter.doFilterInternal(reqB, respB, chain);
        verify(respB, never()).setStatus(429);
        verify(chain, times(1)).doFilter(reqB, respB);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Per-endpoint isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Exhausting POST /api/auth/login does not block POST /api/auth/register")
    void perEndpointIsolation_loginAndRegisterAreSeparate() throws Exception {
        HttpServletRequest  loginReq    = mock(HttpServletRequest.class);
        HttpServletRequest  registerReq = mock(HttpServletRequest.class);
        HttpServletResponse loginResp    = mock(HttpServletResponse.class);
        HttpServletResponse registerResp = mock(HttpServletResponse.class);

        when(loginResp.getWriter()).thenReturn(printWriter);

        stubRequest(loginReq,    "POST", "/api/auth/login",    "10.0.0.6");
        stubRequest(registerReq, "POST", "/api/auth/register", "10.0.0.6");

        // Exhaust login (5 pass, 6th blocked)
        hit(loginReq, loginResp, 6);
        verify(loginResp, times(1)).setStatus(429);

        // Same IP on register — must still be allowed
        filter.doFilterInternal(registerReq, registerResp, chain);
        verify(registerResp, never()).setStatus(429);
        verify(chain, times(1)).doFilter(registerReq, registerResp);
    }

    @Test
    @DisplayName("POST /api/products has its own limit (10) independent of login's limit (5)")
    void perEndpointIsolation_productsLimitIsSeparate() throws Exception {
        // Exhaust login for a given IP using the shared mocks
        stubRequest(request, "POST", "/api/auth/login", "10.0.0.7");
        hit(request, response, 6);
        verify(response, times(1)).setStatus(429);

        // Same IP, products endpoint — use fresh mocks so counters are provably isolated
        HttpServletRequest  prodReq  = mock(HttpServletRequest.class);
        HttpServletResponse prodResp = mock(HttpServletResponse.class);
        stubRequest(prodReq, "POST", "/api/products", "10.0.0.7");

        // All 10 allowed requests must pass through
        hit(prodReq, prodResp, 10);
        verify(prodResp, never()).setStatus(429);
        verify(chain, times(10)).doFilter(prodReq, prodResp);
    }

    @Test
    @DisplayName("POST /api/products limit is 10 — 11th request is blocked, not the 6th")
    void productsEndpoint_limitIs10NotFive() throws Exception {
        stubRequest(request, "POST", "/api/products", "10.0.0.8");

        hit(request, response, 10);
        verify(chain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);

        // 11th must be blocked
        filter.doFilterInternal(request, response, chain);
        verify(response, times(1)).setStatus(429);
        verify(chain, times(10)).doFilter(request, response);   // still exactly 10
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Unlisted endpoints are never rate-limited
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("20 GET /api/buyer/products requests are never blocked (no rule for this endpoint)")
    void unlistedEndpoint_neverRateLimited() throws Exception {
        stubRequest(request, "GET", "/api/buyer/products", "10.0.0.9");
        hit(request, response, 20);

        verify(chain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("GET /api/products is not rate-limited (only POST /api/products has a rule)")
    void getProducts_isNotLimited_onlyPostIs() throws Exception {
        stubRequest(request, "GET", "/api/products", "10.0.0.10");
        hit(request, response, 20);

        verify(chain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bonus: X-Forwarded-For parsing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("X-Forwarded-For with multiple IPs: only the first one is used for the key")
    void xForwardedFor_multipleIPs_usesFirstOnly() throws Exception {
        // First IP is "203.0.113.1"; the others are intermediary proxies
        stubRequestXff(request, "POST", "/api/auth/login", "203.0.113.1, 10.0.0.1, 172.16.0.1");

        hit(request, response, 6);

        // 5 pass, 6th is blocked — proves the counter tracked against "203.0.113.1"
        verify(chain, times(5)).doFilter(request, response);
        verify(response, times(1)).setStatus(429);
    }
}
