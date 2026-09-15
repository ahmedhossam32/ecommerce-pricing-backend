package com.ecommerce.pricing.service.impl;
import com.ecommerce.pricing.client.MLClient;
import com.ecommerce.pricing.dto.request.MLRequest;
import com.ecommerce.pricing.dto.response.MLResponse;
import com.ecommerce.pricing.service.MLService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MLServiceImpl implements MLService {

    private final MLClient mlClient;

    @Override
    public MLResponse predict(MLRequest request) {
        return mlClient.predict(request);
    }
}