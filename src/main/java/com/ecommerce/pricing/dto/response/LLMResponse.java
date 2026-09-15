package com.ecommerce.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {

    private String brand;
    private Double estimatedWeight;
    private Double marketPriceMin;
    private Double marketPriceMax;
    private Double multiplier;
    private String confidence;
    private String reasoning;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("productType")
    private String productType;

    @JsonProperty("modelIdentifier")
    private String modelIdentifier;
}