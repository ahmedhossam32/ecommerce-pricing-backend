package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.entity.User;

public interface PricingService {
    PricingSuggestionResponse getSuggestion(ProductListingRequest request, User seller);
}