package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DisputePriceRequest {

    @NotNull
    @Positive
    private Double sellerPrice;

    @NotBlank
    private String sellerReasoning;
}