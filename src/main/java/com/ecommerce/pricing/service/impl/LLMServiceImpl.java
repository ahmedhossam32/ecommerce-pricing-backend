package com.ecommerce.pricing.service.impl;
import com.ecommerce.pricing.client.LLMClient;
import com.ecommerce.pricing.dto.response.LLMResponse;
import com.ecommerce.pricing.service.LLMService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final LLMClient llmClient;

    @Override
    public LLMResponse extractProductInfo(String description) {
        return llmClient.extractProductInfo(description);
    }

    @Override
    public LLMResponse analyzePricing(String description, String brand, String condition, String conditionNotes, String productType, String modelIdentifier, double mlBaseline) {
        return llmClient.analyzePricing(description, brand, condition, conditionNotes, productType, modelIdentifier, mlBaseline);
    }
}
