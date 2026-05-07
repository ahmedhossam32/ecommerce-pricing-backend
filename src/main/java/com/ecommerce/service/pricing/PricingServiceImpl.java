package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.LLMResponse;
import com.ecommerce.dto.response.MLResponse;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.entity.CategoryStats;
import com.ecommerce.entity.User;
import com.ecommerce.repository.CategoryStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final LLMService llmService;
    private final MLService mlService;
    private final FeatureBuilderService featureBuilderService;
    private final RoutingService routingService;
    private final CategoryStatsRepository categoryStatsRepository;

    @Override
    public PricingSuggestionResponse getSuggestion(ProductListingRequest request, User seller) {

        // 1. Category stats — new sellers never get zeros
        CategoryStats stats = categoryStatsRepository
                .findByCategory(request.getCategory().toLowerCase())
                .orElse(null);

        // 2. LLM Call 1: extract brand + weight (runs before ML)
        LLMResponse extraction = llmService.extractProductInfo(request.getDescription());

        // 3. Build all 26 ML features using brand + weight from LLM Call 1
        MLRequest mlRequest = featureBuilderService.buildFeatures(request, extraction, seller, stats);

        // 4. ML baseline price
        MLResponse mlResponse = mlService.predict(mlRequest);
        double mlBaseline = mlResponse.getPredictedPrice();

        System.out.println("=== ML BASELINE: " + mlBaseline + " ===");
        System.out.println("=== CATEGORY: " + request.getCategory() + " ===");

        // 5. LLM Call 2: market price + confidence + multiplier (runs after ML)
        LLMResponse pricing = llmService.analyzePricing(request.getDescription(), mlBaseline);

        System.out.println("=== LLM CONFIDENCE: " + pricing.getConfidence() + " ===");
        System.out.println("=== LLM PRICE RANGE: " + pricing.getMarketPriceMin() + " - " + pricing.getMarketPriceMax() + " ===");
        System.out.println("=== LLM BRAND: " + extraction.getBrand() + " ===");
        System.out.println("=== LLM REASONING: " + pricing.getReasoning() + " ===");

        // 6. Combine ML + LLM into suggested price
        double suggested = computeSuggestedPrice(pricing, mlBaseline);
        double minRange  = round(suggested * 0.85);
        double maxRange  = round(suggested * 1.15);
        suggested        = round(suggested);

        // 7. Route: cache → bounds → confidence
        String brand = extraction.getBrand() != null ? extraction.getBrand() : "UNKNOWN";
        String status = routingService.determineStatus(suggested, brand, request.getCategory(), pricing.getConfidence());

        return PricingSuggestionResponse.builder()
                .suggestedPrice(suggested)
                .minRange(minRange)
                .maxRange(maxRange)
                .confidence(pricing.getConfidence())
                .status(status)
                .message(message(status))
                .brand(brand)
                .mlBaselinePrice(mlBaseline)
                .marketPriceMin(pricing.getMarketPriceMin())
                .marketPriceMax(pricing.getMarketPriceMax())
                .build();
    }


    private double computeSuggestedPrice(LLMResponse llm, double mlBaseline) {
        boolean hasLLMRange = llm.getMarketPriceMin() != null && llm.getMarketPriceMax() != null;

        return switch (llm.getConfidence().toUpperCase()) {

            case "HIGH" -> {
                if (hasLLMRange) yield (llm.getMarketPriceMin() + llm.getMarketPriceMax()) / 2.0;
                yield llm.getMarketPriceMax() != null ? llm.getMarketPriceMax()
                        : llm.getMarketPriceMin() != null ? llm.getMarketPriceMin()
                        : mlBaseline;
            }

            case "MEDIUM" -> {
                if (hasLLMRange) {
                    double min = llm.getMarketPriceMin();
                    double max = llm.getMarketPriceMax();
                    if (mlBaseline >= min && mlBaseline <= max) yield mlBaseline;
                    yield (min + max) / 2.0;
                }
                yield mlBaseline;
            }

            default -> mlBaseline;
        };
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String message(String status) {
        return switch (status) {
            case "PENDING_SELLER" -> "Please review the suggested price and accept or dispute it.";
            default               -> "Your pricing request has been submitted for admin review.";
        };
    }
}