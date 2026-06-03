package com.ecommerce.service.pricing;

import com.ecommerce.entity.CategoryBounds;
import com.ecommerce.repository.CategoryBoundsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingService — Unit Tests")
class RoutingServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CategoryBoundsRepository categoryBoundsRepository;

    @InjectMocks
    private RoutingServiceImpl routingService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // no cache by default
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("HIGH confidence with no cache and within bounds → PENDING_SELLER")
    void highConfidenceNoCacheWithinBounds_ReturnsPendingSeller() {
        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.empty());

        String result = routingService.determineStatus(
                500.0, "Samsung", "electronics", "HIGH", "NEW");

        assertThat(result).isEqualTo("PENDING_SELLER");
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("MEDIUM confidence with no cache and within bounds → PENDING_SELLER")
    void mediumConfidenceNoCacheWithinBounds_ReturnsPendingSeller() {
        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.empty());

        String result = routingService.determineStatus(
                300.0, "Sony", "electronics", "MEDIUM", "USED");

        assertThat(result).isEqualTo("PENDING_SELLER");
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("LOW confidence with no cache → PENDING_ADMIN")
    void lowConfidenceNoCache_ReturnsPendingAdmin() {
        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.empty());

        String result = routingService.determineStatus(
                200.0, "UNKNOWN", "electronics", "LOW", "NEW");

        assertThat(result).isEqualTo("PENDING_ADMIN");
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Price above category max bound → PENDING_ADMIN")
    void priceAboveCategoryMaxBound_ReturnsPendingAdmin() {
        CategoryBounds bounds = new CategoryBounds();
        bounds.setMinPrice(BigDecimal.valueOf(50.0));
        bounds.setMaxPrice(BigDecimal.valueOf(500.0));

        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.of(bounds));

        String result = routingService.determineStatus(
                999.0, "Samsung", "electronics", "HIGH", "NEW");

        assertThat(result).isEqualTo("PENDING_ADMIN");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Price below category min bound → PENDING_ADMIN")
    void priceBelowCategoryMinBound_ReturnsPendingAdmin() {
        CategoryBounds bounds = new CategoryBounds();
        bounds.setMinPrice(BigDecimal.valueOf(100.0));
        bounds.setMaxPrice(BigDecimal.valueOf(1000.0));

        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.of(bounds));

        String result = routingService.determineStatus(
                10.0, "Xiaomi", "electronics", "HIGH", "NEW");

        assertThat(result).isEqualTo("PENDING_ADMIN");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Cache hit with price in approved range → PENDING_SELLER")
    void cacheHitWithPriceInRange_ReturnsPendingSeller() {
        when(valueOperations.get(anyString())).thenReturn("450.0:550.0");

        String result = routingService.determineStatus(
                500.0, "Samsung", "electronics", "LOW", "NEW");

        assertThat(result).isEqualTo("PENDING_SELLER");
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("priceBucket — under 200 returns budget")
    void priceBucketBudget() {
        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.empty());

        String result = routingService.determineStatus(
                150.0, "Generic", "electronics", "HIGH", "NEW");

        assertThat(result).isEqualTo("PENDING_SELLER");
    }

    // ── Test 8 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("priceBucket — over 1000 returns luxury bucket (no crash)")
    void priceBucketLuxury() {
        when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(Optional.empty());

        String result = routingService.determineStatus(
                1500.0, "Apple", "electronics", "HIGH", "NEW");

        assertThat(result).isEqualTo("PENDING_SELLER");
    }
}
