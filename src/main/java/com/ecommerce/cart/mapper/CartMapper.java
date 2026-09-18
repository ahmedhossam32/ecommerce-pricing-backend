package com.ecommerce.cart.mapper;

import com.ecommerce.cart.dto.response.CartResponse;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {

    public CartResponse toResponse(CartItem item) {
        Product p = item.getProduct();
        return CartResponse.builder()
                .cartItemId(item.getId())
                .productId(p.getId())
                .productName(p.getName())
                .brand(p.getBrand())
                .category(p.getCategory())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .addedAt(item.getAddedAt())
                .imageUrls(p.getImageUrls())
                .build();
    }
}
