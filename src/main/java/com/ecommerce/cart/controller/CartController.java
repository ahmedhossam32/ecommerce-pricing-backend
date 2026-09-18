package com.ecommerce.cart.controller;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.cart.dto.response.CartResponse;
import com.ecommerce.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buyer/cart")
@PreAuthorize("hasRole('BUYER')")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserRepository userRepository;

    @PostMapping("/{productId}")
    public ResponseEntity<CartResponse> addToCart(@PathVariable Long productId) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(cartService.addToCart(productId, buyer));
    }

    @GetMapping
    public ResponseEntity<List<CartResponse>> getCart() {
        Long buyerId = SecurityUtils.getCurrentUserId();
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(cartService.getCart(buyer));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long productId) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        cartService.removeFromCart(productId, buyer);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        Long buyerId = SecurityUtils.getCurrentUserId();
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        cartService.clearCart(buyer);
        return ResponseEntity.noContent().build();
    }
}
