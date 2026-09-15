package com.ecommerce.cart.service;

import com.ecommerce.user.entity.User;
import com.ecommerce.cart.dto.response.CartResponse;

import java.util.List;

public interface CartService {
    CartResponse addToCart(Long productId, User buyer);
    List<CartResponse> getCart(User buyer);
    void removeFromCart(Long productId, User buyer);
    void clearCart(User buyer);
}
