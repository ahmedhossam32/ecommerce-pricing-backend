package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.LLMResponse;
import com.ecommerce.dto.response.MLResponse;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.entity.CategoryStats;
import com.ecommerce.entity.User;
import com.ecommerce.repository.CategoryStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingService — Unit Tests")
class PricingServiceImplTest {

    @Mock private LLMService llmService;
    @Mock private MLService mlService;
    @Mock private FeatureBuilderService featureBuilderService;
    @Mock private RoutingService routingService;
    @Mock private CategoryStatsRepository categoryStatsRepository;

    @InjectMocks
    private PricingServiceImpl pricingService;

    private User seller;
    private ProductListingRequest baseRequest;

    @BeforeEach
    void setUp() {
        seller = new User();

        baseRequest = ProductListingRequest.builder()
                .category("electronics")
                .description("Samsung Galaxy S25 Ultra 256GB")
                .freightValue(15.0)
                .photosQty(3)
                .weight(200.0)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        // Default: no category stats
        when(categoryStatsRepository.findByCategory(anyString()))
                .thenReturn(Optional.empty());

        // Default: feature builder returns a minimal MLRequest
        when(featureBuilderService.buildFeatures(any(), any(), any(), any()))
                .thenReturn(com.ecommerce.dto.request.MLRequest.builder()
                        .productCategoryNameEnglish("electronics")
                        .build());
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("HIGH confidence — suggested price is midpoint of LLM range")
    void highConfidence_SuggestedPriceIsMidpointOfLLMRange() {
        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Samsung")
                        .estimatedWeight(200.0)
                        .productType("smartphone")
                        .modelIdentifier("S25 Ultra")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(900.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("HIGH")
                        .marketPriceMin(1200.0)
                        .marketPriceMax(1400.0)
                        .reasoning("Strong market demand")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_SELLER");

        PricingSuggestionResponse response = pricingService.getSuggestion(baseRequest, seller);

        assertThat(response.getSuggestedPrice()).isEqualTo(1300.0);
        assertThat(response.getConfidence()).isEqualTo("HIGH");
        assertThat(response.getStatus()).isEqualTo("PENDING_SELLER");
        assertThat(response.getBrand()).isEqualTo("Samsung");
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("LOW confidence — suggested price falls back to ML baseline")
    void lowConfidence_SuggestedPriceFallsBackToMLBaseline() {
        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Sony")
                        .estimatedWeight(300.0)
                        .productType("headphones")
                        .modelIdentifier("WH-1000XM6")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(250.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("LOW")
                        .marketPriceMin(null)
                        .marketPriceMax(null)
                        .reasoning("Insufficient market data")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_ADMIN");

        PricingSuggestionResponse response = pricingService.getSuggestion(baseRequest, seller);

        assertThat(response.getSuggestedPrice()).isEqualTo(250.0);
        assertThat(response.getConfidence()).isEqualTo("LOW");
        assertThat(response.getStatus()).isEqualTo("PENDING_ADMIN");
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("UNKNOWN brand — forces LOW confidence regardless of LLM response")
    void unknownBrand_ForcesLowConfidence() {
        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("UNKNOWN")
                        .estimatedWeight(null)
                        .productType(null)
                        .modelIdentifier(null)
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(180.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("HIGH")
                        .marketPriceMin(200.0)
                        .marketPriceMax(300.0)
                        .reasoning("Detected market range")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_ADMIN");

        PricingSuggestionResponse response = pricingService.getSuggestion(baseRequest, seller);

        assertThat(response.getConfidence()).isEqualTo("LOW");
        assertThat(response.getBrand()).isEqualTo("UNKNOWN");
        assertThat(response.getSuggestedPrice()).isEqualTo(180.0);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("USED HEAVY condition — applies 0.45x multiplier to suggested price")
    void usedHeavyCondition_Applies045Multiplier() {
        ProductListingRequest usedRequest = ProductListingRequest.builder()
                .category("electronics")
                .description("Used laptop heavy wear")
                .freightValue(20.0)
                .photosQty(2)
                .weight(2000.0)
                .condition("USED")
                .conditionGrade("HEAVY")
                .conditionNotes("Screen scratches, dented corners")
                .build();

        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Dell")
                        .estimatedWeight(2000.0)
                        .productType("laptop")
                        .modelIdentifier("Inspiron 15")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(800.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("HIGH")
                        .marketPriceMin(800.0)
                        .marketPriceMax(1000.0)
                        .reasoning("Market range for new Dell Inspiron")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_SELLER");

        PricingSuggestionResponse response = pricingService.getSuggestion(usedRequest, seller);

        // midpoint = (800+1000)/2 = 900 * 0.45 = 405.0
        assertThat(response.getSuggestedPrice()).isEqualTo(405.0);
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("REFURBISHED condition — applies 0.65x multiplier")
    void refurbishedCondition_Applies065Multiplier() {
        ProductListingRequest refurbishedRequest = ProductListingRequest.builder()
                .category("electronics")
                .description("Refurbished iPhone 14")
                .freightValue(10.0)
                .photosQty(2)
                .weight(172.0)
                .condition("REFURBISHED")
                .conditionGrade(null)
                .conditionNotes("Factory reset, new battery")
                .build();

        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Apple")
                        .estimatedWeight(172.0)
                        .productType("smartphone")
                        .modelIdentifier("iPhone 14")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(600.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("HIGH")
                        .marketPriceMin(600.0)
                        .marketPriceMax(800.0)
                        .reasoning("Refurbished iPhone market range")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_SELLER");

        PricingSuggestionResponse response = pricingService.getSuggestion(refurbishedRequest, seller);

        // midpoint = (600+800)/2 = 700 * 0.65 = 455.0
        assertThat(response.getSuggestedPrice()).isEqualTo(455.0);
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("minRange and maxRange are ±10% of suggested price")
    void minRangeAndMaxRange_AreWithin10PercentOfSuggested() {
        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Samsung")
                        .estimatedWeight(200.0)
                        .productType("smartphone")
                        .modelIdentifier("S25")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(1000.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("HIGH")
                        .marketPriceMin(1000.0)
                        .marketPriceMax(1200.0)
                        .reasoning("Market analysis")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_SELLER");

        PricingSuggestionResponse response = pricingService.getSuggestion(baseRequest, seller);

        double suggested = response.getSuggestedPrice();
        assertThat(response.getMinRange()).isEqualTo(Math.round(suggested * 0.90 * 100.0) / 100.0);
        assertThat(response.getMaxRange()).isEqualTo(Math.round(suggested * 1.10 * 100.0) / 100.0);
    }

    // ── Test 7 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("MEDIUM confidence — ML baseline used when within LLM range")
    void mediumConfidence_MLBaselineUsedWhenWithinRange() {
        when(llmService.extractProductInfo(anyString()))
                .thenReturn(LLMResponse.builder()
                        .brand("Sony")
                        .estimatedWeight(250.0)
                        .productType("headphones")
                        .modelIdentifier("WH-1000XM5")
                        .build());

        when(mlService.predict(any()))
                .thenReturn(new MLResponse(320.0));

        when(llmService.analyzePricing(any(), any(), any(), any(), any(), any(), anyDouble()))
                .thenReturn(LLMResponse.builder()
                        .confidence("MEDIUM")
                        .marketPriceMin(300.0)
                        .marketPriceMax(400.0)
                        .reasoning("Medium confidence estimate")
                        .build());

        when(routingService.determineStatus(anyDouble(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("PENDING_SELLER");

        PricingSuggestionResponse response = pricingService.getSuggestion(baseRequest, seller);

        // ML baseline 320 is within [300, 400] → use ML baseline
        assertThat(response.getSuggestedPrice()).isEqualTo(320.0);
    }
}
