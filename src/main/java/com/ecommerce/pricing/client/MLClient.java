package com.ecommerce.client;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.response.MLResponse;
import com.ecommerce.exception.PricingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class MLClient {

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    public MLResponse predict(MLRequest request) {
        try {
            MLResponse response = restTemplate.postForObject(
                    mlServiceUrl + "/predict", request, MLResponse.class);
            if (response == null || response.getPredictedPrice() == null) {
                throw new PricingException("ML service returned an empty response");
            }
            return response;
        } catch (PricingException e) {
            throw e;
        } catch (Exception e) {
            throw new PricingException("ML service unavailable: " + e.getMessage());
        }
    }
}