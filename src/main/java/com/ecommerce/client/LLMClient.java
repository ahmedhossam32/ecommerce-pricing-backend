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
                    - Price based on CURRENT 2026 market value
                    - If product is described as new/sealed/brand new → use NEW retail price
                    - If product is described as used/refurbished/condition → use SECONDHAND resale price
                    - Do not underprice well-known branded products

                    Return exactly this JSON:
                    {
                      "marketPriceMin": minimum USD market price as number or null if unknown,
                      "marketPriceMax": maximum USD market price as number or null if unknown,
                      "multiplier": marketPriceMidpoint divided by ML baseline rounded to 1 decimal or 1.0 if unknown,
                      "confidence": "HIGH" or "MEDIUM" or "LOW",
                      "reasoning": "one sentence explaining your confidence level"
                    }

                    Confidence rules:
                    HIGH = well known brand, clear product name, established market price
                    MEDIUM = known brand but unclear specs, used condition, or announced but unreleased product
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