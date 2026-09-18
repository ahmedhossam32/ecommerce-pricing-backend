package com.ecommerce.buyer.mapper;

import com.ecommerce.buyer.dto.response.BuyerProductResponse;
import com.ecommerce.buyer.dto.response.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class BuyerMapper {

    private static final String DEFAULT_AVATAR_URL =
            "https://res.cloudinary.com/demo/image/upload/avatar.png";

    public BuyerProductResponse toSummaryResponse(Product p) {
        return BuyerProductResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .brand(p.getBrand())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .weight(p.getWeight())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImageUrls())
                .sellerProfilePictureUrl(resolveProfilePictureUrl(p.getSeller().getProfilePictureUrl()))
                .build();
    }

    public BuyerProductResponse toDetailResponse(Product p) {
        return BuyerProductResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .brand(p.getBrand())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .weight(p.getWeight())
                .photosQty(p.getPhotosQty())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImageUrls())
                .sellerProfilePictureUrl(resolveProfilePictureUrl(p.getSeller().getProfilePictureUrl()))
                .build();
    }

    public OrderResponse toOrderResponse(Order order, String message) {
        Product product = order.getProduct();
        User seller = product.getSeller();

        return OrderResponse.builder()
                .orderId(order.getId())
                .productId(product.getId())
                .productName(product.getName())
                .price(order.getPriceAtPurchase().doubleValue())
                .buyerName(order.getBuyer().getName())
                .sellerName(seller.getName())
                .createdAt(order.getCreatedAt())
                .message(message)
                .imageUrls(product.getImageUrls())
                .category(product.getCategory())
                .brand(product.getBrand())
                .sellerProfilePictureUrl(resolveProfilePictureUrl(seller.getProfilePictureUrl()))
                .build();
    }

    private String resolveProfilePictureUrl(String url) {
        return url != null ? url : DEFAULT_AVATAR_URL;
    }
}
