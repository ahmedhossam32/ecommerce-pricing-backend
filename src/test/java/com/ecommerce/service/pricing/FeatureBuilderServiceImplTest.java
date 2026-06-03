package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.LLMResponse;
import com.ecommerce.entity.CategoryStats;
import com.ecommerce.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeatureBuilderService — Unit Tests")
class FeatureBuilderServiceImplTest {

    private FeatureBuilderServiceImpl featureBuilder;

    @BeforeEach
    void setUp() {
        featureBuilder = new FeatureBuilderServiceImpl();
    }

    // ── Test 1 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should use seller weight when provided")
    void shouldUseSellerWeightWhenProvided() {
        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description("Samsung Galaxy S25 smartphone 128GB")
                .freightValue(15.0)
                .photosQty(3)
                .weight(300.0)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("Samsung")
                .estimatedWeight(500.0)
                .build();

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), null);

        assertThat(result.getProductWeightG()).isEqualTo(300.0);
    }

    // ── Test 2 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should fall back to LLM weight when seller weight is null")
    void shouldFallBackToLLMWeightWhenSellerWeightIsNull() {
        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description("Some product")
                .freightValue(10.0)
                .photosQty(1)
                .weight(null)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("Sony")
                .estimatedWeight(400.0)
                .build();

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), null);

        assertThat(result.getProductWeightG()).isEqualTo(400.0);
    }

    // ── Test 3 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should fall back to 500g default when both weights are null")
    void shouldFallBackTo500gWhenBothWeightsNull() {
        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description("Mystery product")
                .freightValue(5.0)
                .photosQty(1)
                .weight(null)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("UNKNOWN")
                .estimatedWeight(null)
                .build();

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), null);

        assertThat(result.getProductWeightG()).isEqualTo(500.0);
    }

    // ── Test 4 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should use category stats values when stats are provided")
    void shouldUseCategoryStatsWhenProvided() {
        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description("Test product")
                .freightValue(10.0)
                .photosQty(2)
                .weight(200.0)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("Apple")
                .estimatedWeight(200.0)
                .build();

        CategoryStats stats = new CategoryStats();
        stats.setAvgPrice(850.0);
        stats.setAvgReview(4.5);
        stats.setMedianSalesCount(120);
        stats.setMostCommonPaymentType("credit_card");
        stats.setDefaultMaxInstallments(10);

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), stats);

        assertThat(result.getCategoryAvgPrice()).isEqualTo(850.0);
        assertThat(result.getSellerAvgReview()).isEqualTo(4.5);
        assertThat(result.getSellerSalesCount()).isEqualTo(120);
        assertThat(result.getMaxInstallments()).isEqualTo(10);
        assertThat(result.getPaymentTypeMode()).isEqualTo("credit_card");
    }

    // ── Test 5 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should use fallback defaults when stats are null")
    void shouldUseFallbackDefaultsWhenStatsAreNull() {
        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description("Test product")
                .freightValue(10.0)
                .photosQty(2)
                .weight(200.0)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("Apple")
                .estimatedWeight(200.0)
                .build();

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), null);

        assertThat(result.getCategoryAvgPrice()).isEqualTo(100.0);
        assertThat(result.getSellerAvgReview()).isEqualTo(4.0);
        assertThat(result.getSellerSalesCount()).isEqualTo(50);
        assertThat(result.getMaxInstallments()).isEqualTo(12);
        assertThat(result.getPaymentTypeMode()).isEqualTo("credit_card");
    }

    // ── Test 6 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Should set description length as productDescriptionLenght")
    void shouldSetDescriptionLength() {
        String description = "Samsung Galaxy S25 Ultra 256GB Black";

        ProductListingRequest request = ProductListingRequest.builder()
                .category("electronics")
                .description(description)
                .freightValue(10.0)
                .photosQty(2)
                .weight(200.0)
                .condition("NEW")
                .conditionGrade(null)
                .conditionNotes(null)
                .build();

        LLMResponse llm = LLMResponse.builder()
                .brand("Samsung")
                .estimatedWeight(200.0)
                .build();

        MLRequest result = featureBuilder.buildFeatures(request, llm, new User(), null);

        assertThat(result.getProductDescriptionLenght()).isEqualTo(description.length());
    }
}
