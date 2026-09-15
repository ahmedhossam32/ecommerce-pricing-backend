package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DisputePriceRequest {

    @NotNull
    @Positive
    private Double sellerPrice;

    @NotBlank
    @Size(min = 10, max = 500)
    private String sellerReasoning;
}