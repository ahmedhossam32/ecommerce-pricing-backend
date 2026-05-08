package com.ecommerce.controller;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.BuyerProductResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PriceHistoryResponse;
import com.ecommerce.entity.User;
import com.ecommerce.service.buyer.BuyerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BuyerController {

    private final BuyerService buyerService;

    @GetMapping("/api/buyer/products")
    public ResponseEntity<List<BuyerProductResponse>> getAllProducts() {
        return ResponseEntity.ok(buyerService.getAllLiveProducts());
    }

    @GetMapping("/api/buyer/products/{id}")
    public ResponseEntity<BuyerProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(buyerService.getProductById(id));
    }

    @GetMapping("/api/buyer/products/{id}/history")
    public ResponseEntity<List<PriceHistoryResponse>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(buyerService.getProductHistory(id));
    }

    @PostMapping("/api/orders")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(buyerService.placeOrder(request, buyer));
    }

    @GetMapping("/api/orders/my")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(buyerService.getMyOrders(buyer));
    }
}