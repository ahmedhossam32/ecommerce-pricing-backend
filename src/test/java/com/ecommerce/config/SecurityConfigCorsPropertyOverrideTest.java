package com.ecommerce.config;

import com.ecommerce.controller.BuyerController;
import com.ecommerce.service.buyer.BuyerService;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test 5, isolated in its own class: a different app.cors.allowed-origins value means a
 * different Spring context (Spring Boot Test context caching keys on the exact property
 * set), so this is kept separate from SecurityConfigCorsIT rather than sharing a context.
 */
@WebMvcTest(controllers = BuyerController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, RateLimitingFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1jb3JzLXRlc3RzLW9ubHktMzItYnl0ZXM=",
        "app.jwt.expiration=3600000",
        "app.jwt.refresh-expiration=7200000",
        "app.cors.allowed-origins=https://myapp.vercel.app"
})
@DisplayName("SecurityConfig — CORS allowed-origins property override (behavioral verification)")
class SecurityConfigCorsPropertyOverrideTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserDetailsService userDetailsService;
    @MockBean private BuyerService buyerService;

    // ── Test 5 — env var / property override genuinely changes the allowed origin ──
    @Test
    @DisplayName("Test 5: app.cors.allowed-origins actually controls which origin is allowed, not hardcoded")
    void allowedOriginsProperty_actuallyControlsWhichOriginIsAllowed() throws Exception {
        // The origin this context was configured with must be allowed.
        mockMvc.perform(options("/api/buyer/products")
                        .header("Origin", "https://myapp.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://myapp.vercel.app"));

        // The compiled-in default dev origin must NOT be allowed here -- proving the
        // property actually drives the allowlist rather than a hardcoded value winning
        // regardless of configuration.
        MvcResult result = mockMvc.perform(options("/api/buyer/products")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
