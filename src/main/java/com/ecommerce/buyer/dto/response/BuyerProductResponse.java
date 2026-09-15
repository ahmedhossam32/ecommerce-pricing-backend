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
public class BuyerProductResponse {
    private Long productId;
    private String name;
    private String description;
    private String category;
    private String brand;
    private Double price;
    private String sellerName;
    private Double weight;
    private Integer photosQty;
    private LocalDateTime createdAt;
    private List<String> imageUrls;
    private String sellerProfilePictureUrl;
}