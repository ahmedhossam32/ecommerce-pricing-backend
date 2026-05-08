package com.ecommerce.controller;

import com.ecommerce.dto.response.SavedProductResponse;
import com.ecommerce.entity.User;
import com.ecommerce.service.wishlist.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buyer/wishlist")
@PreAuthorize("hasRole('BUYER')")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{productId}")
    public ResponseEntity<SavedProductResponse> saveProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(wishlistService.saveProduct(productId, buyer));
    }

    @GetMapping
    public ResponseEntity<List<SavedProductResponse>> getSaved(
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(wishlistService.getSaved(buyer));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> unsaveProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal User buyer) {
        wishlistService.unsaveProduct(productId, buyer);
        return ResponseEntity.noContent().build();
    }
}
