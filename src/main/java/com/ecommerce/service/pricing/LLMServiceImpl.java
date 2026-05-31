package com.ecommerce.service.pricing;

import com.ecommerce.client.LLMClient;
import com.ecommerce.dto.response.LLMResponse;
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
