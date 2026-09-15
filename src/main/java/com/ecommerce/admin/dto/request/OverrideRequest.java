package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OverrideRequest {

    @NotNull
    @Positive
    private Double newPrice;

    @Size(max = 500)
    private String adminNote;
}