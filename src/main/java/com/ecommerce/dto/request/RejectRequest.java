package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRequest {

    @NotBlank
    @Size(min = 5, max = 500)
    private String rejectionReason;
}