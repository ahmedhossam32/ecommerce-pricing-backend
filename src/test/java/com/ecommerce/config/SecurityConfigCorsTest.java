package com.ecommerce.config;

import com.ecommerce.controller.BuyerController;
import com.ecommerce.controller.PricingController;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.service.buyer.BuyerService;
import com.ecommerce.service.pricing.PricingService;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioral proof for the CORS fix applied to SecurityConfig.
 *
 * @WebMvcTest scoped to just the two controllers these tests touch (BuyerController,
 * PricingController) rather than a full @SpringBootTest -- this project's application
 * context can't fully start in this sandbox (no reachable Postgres/Redis, no OPENAI_API_KEY;
 * see EcommerceBackendApplicationTests, which fails for that reason independent of anything
 * here). @WebMvcTest sidesteps all of that: it loads only the web MVC layer, so none of
 * JPA/Redis/Spring AI/Mail/Cloudinary auto-configuration is even attempted.
 *
 * SecurityConfig/JwtAuthFilter/RateLimitingFilter/JwtUtil are @Component/@Configuration
 * beans that @WebMvcTest does not auto-scan, so they're explicitly @Import-ed -- this
 * exercises the REAL security filter chain (including the real CorsFilter wiring under
 * test), not a re-implementation of it. UserDetailsService/BuyerService/PricingService
 * are @MockBean'd since their real implementations need JPA/other infra this slice
 * deliberately excludes.
 */
@WebMvcTest(controllers = {BuyerController.class, PricingController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, RateLimitingFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1jb3JzLXRlc3RzLW9ubHktMzItYnl0ZXM=",
        "app.jwt.expiration=3600000",
        "app.jwt.refresh-expiration=7200000",
        "app.cors.allowed-origins=http://localhost:5173"
})
@DisplayName("SecurityConfig — CORS fix (behavioral verification)")
class SecurityConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockBean private UserDetailsService userDetailsService;
    @MockBean private BuyerService buyerService;
    @MockBean private PricingService pricingService;

    // ── Test 1 — preflight succeeds without auth ────────────────────────────
    @Test
    @DisplayName("Test 1: preflight OPTIONS on an authenticated endpoint succeeds without auth")
    void preflightOptions_succeedsWithoutAuth() throws Exception {
        mockMvc.perform(options("/api/orders/my")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
    }

    // ── Test 2 — disallowed origin: report the ACTUAL observed behavior ─────
    @Test
    @DisplayName("Test 2: preflight from a disallowed origin -- report actual observed behavior")
    void preflightOptions_disallowedOrigin_reportsActualBehavior() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/orders/my")
                        .header("Origin", DISALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();

        int status = result.getResponse().getStatus();
        String allowOriginHeader = result.getResponse().getHeader("Access-Control-Allow-Origin");
        String body = result.getResponse().getContentAsString();

        System.out.println("[Test 2] status=" + status
                + " Access-Control-Allow-Origin=" + allowOriginHeader
                + " body=\"" + body + "\"");

        assertThat(allowOriginHeader)
                .as("a disallowed origin must never get its own value echoed back")
                .isNull();
        // Spring's DefaultCorsProcessor rejects an unrecognized preflight origin outright
        // (403 "Invalid CORS request") rather than silently permitting the request minus
        // the header -- asserting the actual observed status here, not an assumption.
        assertThat(status).isEqualTo(403);
    }

    // ── Test 3 — real cross-origin GET on a public endpoint ─────────────────
    @Test
    @DisplayName("Test 3: actual cross-origin GET on a public endpoint carries the CORS header")
    void actualGet_publicEndpoint_carriesCorsHeader() throws Exception {
        when(buyerService.getAllLiveProducts(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/buyer/products").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    // ── Test 4 — authenticated cross-origin POST, full preflight+real cycle ─
    @Test
    @DisplayName("Test 4: authenticated cross-origin POST works end-to-end (preflight then real request)")
    void authenticatedPost_endToEnd() throws Exception {
        User seller = User.builder()
                .id(1L)
                .name("Test Seller")
                .email("seller@test.com")
                .password("hashed")
                .role(Role.SELLER)
                .build();
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(seller);
        when(pricingService.getSuggestion(any(), any())).thenReturn(PricingSuggestionResponse.builder()
                .suggestedPrice(100.0)
                .status("PENDING_SELLER")
                .confidence("HIGH")
                .build());

        // 1) preflight
        mockMvc.perform(options("/api/pricing/suggest")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));

        // 2) the real, authenticated request the preflight was clearing the way for
        String token = jwtUtil.generateAccessToken("seller@test.com");
        String body = """
                {
                  "name": "Test Product",
                  "category": "electronics",
                  "description": "A perfectly valid product description",
                  "weight": 200.0,
                  "freightValue": 15.0,
                  "photosQty": 1,
                  "condition": "NEW"
                }
                """;

        mockMvc.perform(post("/api/pricing/suggest")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }
}
