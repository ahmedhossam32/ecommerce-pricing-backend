package com.ecommerce.pricing.service;
import com.ecommerce.pricing.dto.response.LLMResponse;


public interface LLMService {
    LLMResponse extractProductInfo(String description);
    LLMResponse analyzePricing(String description, String brand, String condition, String conditionNotes, String productType, String modelIdentifier, double mlBaseline);
}