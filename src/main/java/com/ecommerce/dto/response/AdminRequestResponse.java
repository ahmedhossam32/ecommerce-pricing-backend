package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRequestResponse {
    private Long requestId;
    private Long productId;
    private String productName;
    private String category;
    private String brand;
    private String sellerName;
    private String sellerEmail;
    private Double suggestedPrice;
    private Double sellerPrice;
    private String sellerReasoning;
    private Double marketPriceMin;
    private Double marketPriceMax;
    private String llmConfidence;
    private Double mlBaselinePrice;
    private LocalDateTime createdAt;
    private String requestType;
    private String routingReason;
    private List<String> imageUrls;
    private String sellerProfilePictureUrl;
}