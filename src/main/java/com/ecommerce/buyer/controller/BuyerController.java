package com.ecommerce.buyer.controller;

import com.ecommerce.user.entity.User;
import com.ecommerce.buyer.dto.response.BuyerProductResponse;
import com.ecommerce.buyer.service.BuyerService;
import com.ecommerce.buyer.dto.request.OrderRequest;
import com.ecommerce.buyer.dto.response.OrderResponse;
import com.ecommerce.buyer.dto.response.PriceHistoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BuyerController {

    private final BuyerService buyerService;

    @GetMapping("/api/buyer/products")
    public ResponseEntity<Page<BuyerProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(buyerService.getAllLiveProducts(pageable));
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

    @GetMapping("/api/orders/{orderId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User buyer) {
        return ResponseEntity.ok(buyerService.getOrderById(orderId, buyer));
    }
}