package com.ecommerce.pricing.service;

import com.ecommerce.product.dto.request.ProductListingRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.pricing.entity.CategoryStats;
import com.ecommerce.pricing.dto.response.LLMResponse;
import com.ecommerce.pricing.dto.request.MLRequest;

public interface FeatureBuilderService {
    MLRequest buildFeatures(ProductListingRequest request, LLMResponse llm, User seller, CategoryStats stats);
}