package com.ecommerce.client;

import com.ecommerce.dto.response.LLMResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMClient {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LLMResponse extractProductInfo(String description) {
        try {
            String prompt = """
                    You are a product information extractor.
                    Extract the brand name from this product description and return ONLY valid JSON. No explanation, no markdown, no code blocks.

                    Product description: "%s"

                    Rules:
                    - brand: Extract the most prominent brand name (e.g. "Apple", "Samsung", "Nike"). If multiple brands appear, pick the first/primary one. NEVER return null — use "UNKNOWN" if no brand found.
                    - estimatedWeight: weight in grams as a number. Estimate based on product type if not mentioned. Return null only if truly impossible to estimate.

                    Return exactly this JSON format (use real values, not placeholders):
                    {
                      "brand": "Apple",
                      "estimatedWeight": 174
                    }
                    """.formatted(description);

            String raw = chatClient.prompt().user(prompt).call().content();
            LLMResponse response = objectMapper.readValue(clean(raw), LLMResponse.class);
            if (response.getBrand() == null || response.getBrand().isBlank()) {
                response.setBrand("UNKNOWN");
            }
            return response;
        } catch (Exception e) {
            return LLMResponse.builder()
                    .brand("UNKNOWN")
                    .build();
        }
    }

    public LLMResponse analyzePricing(String description, double mlBaseline) {
        try {
            String prompt = """
                    You are a product pricing assistant for a 2025-2026 e-commerce platform.
                    Analyze this product and return ONLY raw JSON, no markdown, no explanation.

                    Product description: "%s"
                    ML baseline price for this category: $%.2f
                    Current year: 2026

                    Important pricing rules:
                    - Price based on CURRENT 2026 market value in USD
                    - The ML baseline is trained on old Brazilian e-commerce data — it severely underestimates branded electronics, smartphones, laptops, and fashion. ALWAYS override it with real 2026 market prices for known brands.
                    - If product is new/sealed/brand new → use current NEW retail price from 2026 market
                    - If product contains ANY of these words: "used", "second hand", "secondhand", "refurbished", "pre-owned", "minor scratches", "good condition", "like new", "open box" → you MUST set confidence to MEDIUM and price based on CURRENT secondhand resale market value (typically 40-60%% of new retail price)
                    - For used branded smartphones, laptops, tablets, headphones: secondhand resale value is significantly higher than the ML baseline — use your knowledge of real 2026 resale market prices
                    - Do not underprice well-known branded products in any condition
                    - Only use ML baseline as a signal for truly unknown brands or generic unbranded products

                    Return exactly this JSON:
                    {
                      "marketPriceMin": minimum USD market price as number or null if unknown,
                      "marketPriceMax": maximum USD market price as number or null if unknown,
                      "multiplier": marketPriceMidpoint divided by ML baseline rounded to 1 decimal or 1.0 if unknown,
                      "confidence": "HIGH" or "MEDIUM" or "LOW",
                      "reasoning": "one sentence explaining your confidence level"
                    }

                    Confidence rules:
                    HIGH = well known brand, clear product name, established market price, AND product is new/sealed
                    MEDIUM = known brand but unclear specs, OR used/secondhand/refurbished condition, OR announced but unreleased product
                    LOW = unknown brand, vague description, handmade item, unique or one-of-a-kind product
                    """.formatted(description, mlBaseline);

            String raw = chatClient.prompt().user(prompt).call().content();
            return objectMapper.readValue(clean(raw), LLMResponse.class);
        } catch (Exception e) {
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