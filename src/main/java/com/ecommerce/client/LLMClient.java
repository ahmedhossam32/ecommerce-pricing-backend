package com.ecommerce.client;

import com.ecommerce.dto.response.LLMResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LLMClient.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LLMResponse extractProductInfo(String description) {
        try {
            String prompt = """
                    You are a product information extractor for an e-commerce platform.
                    Extract structured facts from this product description.
                    Return ONLY valid JSON, no markdown, no explanation.

                    Product description: "%s"

                    Rules:
                    - brand: The most prominent brand name. Use "UNKNOWN" if none found. Never null.
                    - condition: Classify as exactly one of:
                        "NEW"         → described as new, sealed, brand new, unopened, never used
                        "USED"        → described as used, second hand, secondhand, pre-owned,
                                        previously owned, gently used, worn, minor scratches,
                                        good condition, fair condition, like new, open box
                        "REFURBISHED" → described as refurbished, restored, reconditioned, certified pre-owned
                        "UNKNOWN"     → no condition mentioned (assume new retail listing)
                    - productType: What the product actually is, not the brand.
                      Examples: "smartphone", "laptop", "running shoes", "mechanical keyboard",
                      "handbag", "smartwatch", "wireless headphones", "gaming mouse"
                    - modelIdentifier: Specific model if mentioned. Examples: "iPhone 17 Pro Max 256GB",
                      "Galaxy S25 Ultra", "WH-1000XM6". Use null if no specific model mentioned.

                    Return exactly this JSON:
                    {
                      "brand": "Apple",
                      "condition": "NEW",
                      "productType": "smartphone",
                      "modelIdentifier": "iPhone 17 Pro Max 256GB"
                    }
                    """.formatted(description);

            String raw = chatClient.prompt().user(prompt).call().content();
            LLMResponse response = objectMapper.readValue(clean(raw), LLMResponse.class);
            if (response.getBrand() == null || response.getBrand().isBlank()) {
                response.setBrand("UNKNOWN");
            }
            return response;
        } catch (Exception e) {
            log.error("=== LLM CALL 1 FAILED: {} ===", e.getMessage(), e);
            return LLMResponse.builder()
                    .brand("UNKNOWN")
                    .build();
        }
    }

    public LLMResponse analyzePricing(String description, String brand, String condition, String conditionNotes, String productType, String modelIdentifier, double mlBaseline) {
        try {
            String modelInfo = (modelIdentifier != null && !modelIdentifier.isBlank())
                    ? modelIdentifier : productType;

            String prompt = """
                    You are a product pricing expert for a 2026 e-commerce marketplace.
                    Return ONLY valid JSON, no markdown, no explanation.

                    Product to price:
                    - Description: "%s"
                    - Brand: %s
                    - Product type: %s
                    - Specific model: %s
                    - Condition: %s
                    - Condition notes from seller: %s
                    - ML physical baseline (Brazilian dataset, ignore for branded products): $%.2f

                    Pricing instructions:
                    - Use CURRENT 2026 market prices in USD for all known brands.
                    - The ML baseline is only reliable for UNKNOWN brands and generic unbranded products.
                      For any recognized brand, override it completely with real market knowledge.
                    - Always return the CURRENT NEW RETAIL price for marketPriceMin and marketPriceMax.
                    - Never apply condition discounts. Price every product as if it is brand new and sealed.
                    - Condition is provided only so you can assess confidence level correctly.
                    - The platform applies condition adjustments separately after you respond.
                    - Be model-specific. iPhone 12 and iPhone 17 have very different prices.
                      A 2019 laptop and a 2024 laptop are not the same price.
                    - marketPriceMin must always be less than marketPriceMax.
                    - Range width guide: 10-20%% of midpoint for well-known products,
                      up to 40%% for vague or generic products.

                    Confidence assignment:
                    HIGH   → Brand is well-known AND specific model is identifiable AND
                             condition is NEW or UNKNOWN
                    MEDIUM → Brand is known BUT condition is USED or REFURBISHED,
                             OR brand is known but model is vague/unclear,
                             OR product is announced but not yet widely available
                    LOW    → Brand is UNKNOWN, OR product is handmade/custom/one-of-a-kind,
                             OR description is too vague to price reliably

                    Return exactly this JSON:
                    {
                      "marketPriceMin": number (USD, never null for HIGH/MEDIUM),
                      "marketPriceMax": number (USD, never null for HIGH/MEDIUM),
                      "confidence": "HIGH" or "MEDIUM" or "LOW",
                      "reasoning": "2-3 sentences: what product this is, what drives the price, and why this confidence level"
                    }
                    """.formatted(description, brand, productType, modelInfo, condition, conditionNotes, mlBaseline);

            String raw = chatClient.prompt().user(prompt).call().content();
            return objectMapper.readValue(clean(raw), LLMResponse.class);
        } catch (Exception e) {
            log.error("=== LLM CALL 2 FAILED: {} ===", e.getMessage(), e);
            return LLMResponse.builder()
                    .confidence("LOW")
                    .multiplier(1.0)
                    .reasoning("LLM unavailable")
                    .build();
        }
    }

    private String clean(String raw) {
        return raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
    }
}