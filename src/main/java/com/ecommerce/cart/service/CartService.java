package com.ecommerce.service.cart;

import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.User;

import java.util.List;

public interface CartService {
    CartResponse addToCart(Long productId, User buyer);
    List<CartResponse> getCart(User buyer);
    void removeFromCart(Long productId, User buyer);
    void clearCart(User buyer);
}
