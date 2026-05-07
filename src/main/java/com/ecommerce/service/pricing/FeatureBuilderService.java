package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.LLMResponse;
import com.ecommerce.entity.CategoryStats;
import com.ecommerce.entity.User;

public interface FeatureBuilderService {
    MLRequest buildFeatures(ProductListingRequest request, LLMResponse llm, User seller, CategoryStats stats);
}