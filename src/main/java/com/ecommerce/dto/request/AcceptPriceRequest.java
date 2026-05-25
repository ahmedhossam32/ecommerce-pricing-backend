package com.ecommerce.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AcceptPriceRequest {
    @Positive
    private Double chosenPrice;
}