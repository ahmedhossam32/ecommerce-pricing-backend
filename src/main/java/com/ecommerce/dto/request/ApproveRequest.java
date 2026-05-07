package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ApproveRequest {

    @NotNull
    @Positive
    private Double approvedPrice;

    private String adminNote;
}