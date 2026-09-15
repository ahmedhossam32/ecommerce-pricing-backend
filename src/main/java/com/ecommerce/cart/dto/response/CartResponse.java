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
public class CartResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String brand;
    private String category;
    private Double price;
    private String sellerName;
    private LocalDateTime addedAt;
    private List<String> imageUrls;
}
