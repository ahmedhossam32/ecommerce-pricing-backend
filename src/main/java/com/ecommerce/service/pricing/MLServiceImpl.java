package com.ecommerce.service.pricing;

import com.ecommerce.client.MLClient;
import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.response.MLResponse;
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