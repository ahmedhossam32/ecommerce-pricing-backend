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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingServiceImpl.class);

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

        log.info("=== ML BASELINE: {} ===", mlBaseline);
        log.debug("=== CATEGORY: {} ===", request.getCategory());

        // 5. Resolve condition: seller ground truth takes priority over LLM guess
        String condition = (request.getCondition() != null && !request.getCondition().isBlank())
                ? request.getCondition()
                : (extraction.getCondition() != null ? extraction.getCondition() : "UNKNOWN");

        String conditionNotes = request.getConditionNotes() != null
                ? request.getConditionNotes() : "";

        log.debug("=== CONDITION SOURCE: {} ===",
                (request.getCondition() != null && !request.getCondition().isBlank()) ? "SELLER" : "LLM");
        log.debug("=== CONDITION: {} ===", condition);

        // 5. LLM Call 2: market price + confidence (runs after ML)
        LLMResponse pricing = llmService.analyzePricing(
                request.getDescription(),
                extraction.getBrand(),
                condition,
                conditionNotes,
                extraction.getProductType(),
                extraction.getModelIdentifier(),
                mlBaseline);

        log.info("=== LLM CONFIDENCE: {} ===", pricing.getConfidence());
        log.info("=== LLM RAW RANGE (new retail): {} - {} ===", pricing.getMarketPriceMin(), pricing.getMarketPriceMax());
        log.debug("=== LLM BRAND: {} ===", extraction.getBrand());
        log.debug("=== LLM REASONING: {} ===", pricing.getReasoning());
        log.info("=== CONDITION MULTIPLIER: {} ===", getConditionMultiplier(condition, conditionNotes));
        log.info("=== EXPECTED FINAL: {} ===", round(((pricing.getMarketPriceMin() != null && pricing.getMarketPriceMax() != null) ? (pricing.getMarketPriceMin() + pricing.getMarketPriceMax()) / 2.0 * getConditionMultiplier(condition, conditionNotes) : 0.0)));

        // Resolve brand early — needed for UNKNOWN guard below
        String brand = extraction.getBrand() != null ? extraction.getBrand() : "UNKNOWN";

        // Force LOW confidence when brand is UNKNOWN — LLM inconsistency guard
        if ("UNKNOWN".equalsIgnoreCase(brand)) {
            log.info("=== FORCING LOW CONFIDENCE: brand is UNKNOWN ===");
            pricing = LLMResponse.builder()
                    .confidence("LOW")
                    .marketPriceMin(null)
                    .marketPriceMax(null)
                    .reasoning("Brand is unknown — ML baseline used for pricing.")
                    .build();
        }

        // 6. Combine ML + LLM into suggested price
        double suggested = computeSuggestedPrice(pricing, mlBaseline, condition, conditionNotes);
        double minRange  = round(suggested * 0.90);
        double maxRange  = round(suggested * 1.10);
        suggested        = round(suggested);

        // 7. Route: cache → bounds → confidence
        String status = routingService.determineStatus(suggested, brand, request.getCategory(), pricing.getConfidence(), condition);

        // 8. ML validation — sanity check LLM price against ML category knowledge
        // ML knows the category average price — use it to catch suspicious LLM prices
        double categoryAvgPrice = (stats != null && stats.getAvgPrice() != null)
                ? stats.getAvgPrice().doubleValue()
                : mlBaseline;

        double priceRatio = categoryAvgPrice > 0
                ? suggested / categoryAvgPrice : 1.0;

        boolean suspiciousPrice = priceRatio > 50.0 || priceRatio < 0.1;

        if (suspiciousPrice && !"LOW".equalsIgnoreCase(pricing.getConfidence())) {
            log.warn("=== ML VALIDATION: suspicious price ratio {}x category avg ${} — routing to admin ===",
                    Math.round(priceRatio), Math.round(categoryAvgPrice));
            status = "PENDING_ADMIN";
        } else {
            log.info("=== ML VALIDATION: price ratio {}x category avg ${} — OK ===",
                    Math.round(priceRatio), Math.round(categoryAvgPrice));
        }

        log.info("=== ML ROLE: category avg ${} | price ratio {}x | suspicious: {} ===",
                Math.round(categoryAvgPrice),
                Math.round(priceRatio),
                suspiciousPrice);

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
                .condition(condition)
                .conditionNotes(conditionNotes)
                .reasoning(pricing.getReasoning())
                .build();
    }


    private double computeSuggestedPrice(LLMResponse llm, double mlBaseline,
                                          String condition, String conditionNotes) {
        boolean hasLLMRange = llm.getMarketPriceMin() != null
                              && llm.getMarketPriceMax() != null;

        double multiplier = getConditionMultiplier(condition, conditionNotes);

        return switch (llm.getConfidence().toUpperCase()) {

            case "HIGH" -> {
                if (hasLLMRange) {
                    double mid = (llm.getMarketPriceMin() + llm.getMarketPriceMax()) / 2.0;
                    yield mid * multiplier;
                }
                yield llm.getMarketPriceMax() != null ? llm.getMarketPriceMax() * multiplier
                        : llm.getMarketPriceMin() != null ? llm.getMarketPriceMin() * multiplier
                        : mlBaseline;
            }

            case "MEDIUM" -> {
                if (hasLLMRange) {
                    double min = llm.getMarketPriceMin() * multiplier;
                    double max = llm.getMarketPriceMax() * multiplier;
                    double mid = (min + max) / 2.0;
                    if (mlBaseline >= min && mlBaseline <= max) yield mlBaseline;
                    yield mid;
                }
                yield mlBaseline;
            }

            default -> mlBaseline;
        };
    }

    private double getConditionMultiplier(String condition, String conditionNotes) {
        if (condition == null) return 1.0;
        return switch (condition.toUpperCase()) {
            case "USED" -> {
                if (conditionNotes != null) {
                    String notes = conditionNotes.toLowerCase();
                    boolean heavyDamage = notes.contains("crack")
                            || notes.contains("broken")
                            || notes.contains("damage")
                            || notes.contains("heavy wear")
                            || notes.contains("not working")
                            || notes.contains("faulty");
                    if (heavyDamage) yield 0.45;
                }
                yield 0.60;
            }
            case "REFURBISHED" -> 0.65;
            default -> 1.0;
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