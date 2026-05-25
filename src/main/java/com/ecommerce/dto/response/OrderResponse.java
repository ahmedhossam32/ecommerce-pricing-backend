package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private Long productId;
    private String productName;
    private Double price;
    private String buyerName;
    private String sellerName;
    private LocalDateTime createdAt;
    private String message;
}