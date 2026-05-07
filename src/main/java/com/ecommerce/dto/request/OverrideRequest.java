package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OverrideRequest {

    @NotNull
    @Positive
    private Double newPrice;

    private String adminNote;
}