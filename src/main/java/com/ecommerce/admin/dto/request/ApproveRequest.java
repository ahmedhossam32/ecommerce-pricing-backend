package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApproveRequest {

    @NotNull
    @Positive
    private Double approvedPrice;

    @Size(max = 500)
    private String adminNote;
}