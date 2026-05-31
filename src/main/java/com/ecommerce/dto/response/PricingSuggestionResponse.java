package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSuggestionResponse {

    private Long productId;
    private Double suggestedPrice;
    private Double minRange;
    private Double maxRange;
    private String confidence;
    private String status;
    private String message;
    private String brand;
    private Double mlBaselinePrice;
    private Double marketPriceMin;
    private Double marketPriceMax;
    private String condition;
    private String conditionNotes;
    private String reasoning;
}