package com.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window rate limiter for abuse-prone endpoints.
 * <p>
 * IMPORTANT: This is a single-instance, in-memory implementation. Each JVM instance
 * maintains its own independent counters, so if the backend is ever horizontally scaled
 * across multiple instances the per-IP limits will not be enforced globally. In that case
 * this limiter must be replaced with (or backed by) a shared store such as Redis.
 */
@Slf4j
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private record Limit(int maxRequests, long windowMillis) {}

    private static class Window {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    /** Rules keyed by "METHOD:URI". Only exact path + method combinations are matched. */
    private static final Map<String, Limit> RULES = Map.of(
            "POST:/api/auth/login",       new Limit(5,  60_000),
            "POST:/api/auth/register",    new Limit(5,  60_000),
            "POST:/api/products",         new Limit(10, 60_000),
            "POST:/api/pricing/suggest",  new Limit(5,  60_000)
    );

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ruleKey = request.getMethod() + ":" + request.getRequestURI();
        Limit limit = RULES.get(ruleKey);

        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String windowKey = ruleKey + ":" + clientIp;

        Window window = windows.computeIfAbsent(windowKey, k -> new Window());

        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStart >= limit.windowMillis()) {
                window.windowStart = now;
                window.count.set(0);
            }

            int current = window.count.incrementAndGet();

            if (current > limit.maxRequests()) {
                long retryAfterMs = limit.windowMillis() - (now - window.windowStart);
                long retryAfterSec = (retryAfterMs + 999) / 1000;

                log.warn("Rate limit exceeded — ip={} uri={} count={}", clientIp, request.getRequestURI(), current);

                response.setStatus(429);
                response.setContentType("application/json");
                response.setHeader("Retry-After", String.valueOf(retryAfterSec));
                response.getWriter().write(
                        "{\"status\":429,\"message\":\"Too many requests. Please try again in "
                                + retryAfterSec + " seconds.\"}"
                );
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
