package com.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductListingRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String category;

    @NotBlank
    @Size(min = 10, message = "Description must be at least 10 characters")
    private String description;

    private Double weight;

    @NotNull
    @PositiveOrZero
    private Double freightValue;

    @NotNull
    @Min(1)
    private Integer photosQty;
}