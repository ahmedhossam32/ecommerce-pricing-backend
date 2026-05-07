package com.ecommerce.service.pricing;

import com.ecommerce.dto.response.LLMResponse;

public interface LLMService {
    LLMResponse extractProductInfo(String description);
    LLMResponse analyzePricing(String description, double mlBaseline);
}