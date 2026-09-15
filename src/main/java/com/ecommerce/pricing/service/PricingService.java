package com.ecommerce.pricing.service;

import com.ecommerce.product.dto.request.ProductListingRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.pricing.dto.response.PricingSuggestionResponse;

public interface PricingService {
    PricingSuggestionResponse getSuggestion(ProductListingRequest request, User seller);
}