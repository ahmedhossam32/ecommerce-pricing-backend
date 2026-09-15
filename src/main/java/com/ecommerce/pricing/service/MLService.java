package com.ecommerce.pricing.service;
import com.ecommerce.pricing.dto.request.MLRequest;
import com.ecommerce.pricing.dto.response.MLResponse;


public interface MLService {
    MLResponse predict(MLRequest request);
}