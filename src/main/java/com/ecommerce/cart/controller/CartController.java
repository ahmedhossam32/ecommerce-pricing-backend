package com.ecommerce.controller;

import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.User;
import com.ecommerce.service.cart.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buyer/cart")
@PreAuthorize("hasRole('BUYER')")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/{productId}")
    public ResponseEntity<CartResponse> addToCart(
            @PathVariable Long productId,
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(cartService.addToCart(productId, buyer));
    }

    @GetMapping
    public ResponseEntity<List<CartResponse>> getCart(
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(cartService.getCart(buyer));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable Long productId,
            @AuthenticationPrincipal User buyer) {
        cartService.removeFromCart(productId, buyer);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @AuthenticationPrincipal User buyer) {
        cartService.clearCart(buyer);
        return ResponseEntity.noContent().build();
    }
}
