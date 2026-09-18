package com.ecommerce.buyer.controller;

import com.ecommerce.buyer.dto.response.BuyerProductResponse;
import com.ecommerce.buyer.service.BuyerService;
import com.ecommerce.buyer.dto.request.OrderRequest;
import com.ecommerce.buyer.dto.response.OrderResponse;
import com.ecommerce.buyer.dto.response.PriceHistoryResponse;
import com.ecommerce.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<Page<BuyerProductResponse>> getAllProducts(Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(buyerService.getAllLiveProducts(sortedPageable));
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
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(buyerService.placeOrder(request, buyerId));
    }

    @GetMapping("/api/orders/my")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<List<OrderResponse>> getMyOrders() {
        Long buyerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(buyerService.getMyOrders(buyerId));
    }

    @GetMapping("/api/orders/{orderId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(buyerService.getOrderById(orderId, buyerId));
    }
}