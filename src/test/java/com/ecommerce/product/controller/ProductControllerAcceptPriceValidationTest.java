package com.ecommerce.controller;

import com.ecommerce.dto.response.AcceptPriceResponse;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.exception.GlobalExceptionHandler;
import com.ecommerce.service.product.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that POST /api/products/{id}/accept enforces the @Positive constraint on
 * AcceptPriceRequest.chosenPrice at the HTTP layer (i.e. @Valid is actually wired up),
 * without ever reaching ProductServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController.acceptPrice — request validation")
class ProductControllerAcceptPriceValidationTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User seller = User.builder()
                .id(1L)
                .email("seller@example.com")
                .role(Role.SELLER)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(seller, null, "ROLE_SELLER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("chosenPrice = 0 returns 400 and never calls the service")
    void zeroChosenPrice_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("chosenPrice", 0));

        mockMvc.perform(post("/api/products/{id}/accept", 1L)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("chosenPrice = -5 returns 400 and never calls the service")
    void negativeChosenPrice_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("chosenPrice", -5));

        mockMvc.perform(post("/api/products/{id}/accept", 1L)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("chosenPrice = 100 (positive) returns 200 — not a regression")
    void positiveChosenPrice_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("chosenPrice", 100));

        when(productService.acceptPrice(eq(1L), any(), any()))
                .thenReturn(AcceptPriceResponse.builder()
                        .productId(1L)
                        .finalPrice(100.0)
                        .status("ACCEPTED")
                        .message("Price accepted")
                        .build());

        mockMvc.perform(post("/api/products/{id}/accept", 1L)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        verify(productService, times(1)).acceptPrice(eq(1L), any(), any());
    }
}
