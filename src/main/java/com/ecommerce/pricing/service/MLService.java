package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.response.MLResponse;

public interface MLService {
    MLResponse predict(MLRequest request);
}