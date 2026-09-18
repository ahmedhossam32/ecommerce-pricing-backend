package com.ecommerce.buyer.service;

import com.ecommerce.buyer.dto.response.BuyerProductResponse;
import com.ecommerce.buyer.dto.request.OrderRequest;
import com.ecommerce.buyer.dto.response.OrderResponse;
import com.ecommerce.buyer.dto.response.PriceHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BuyerService {
    Page<BuyerProductResponse> getAllLiveProducts(Pageable pageable);
    BuyerProductResponse getProductById(Long productId);
    List<PriceHistoryResponse> getProductHistory(Long productId);
    OrderResponse placeOrder(OrderRequest request, Long buyerId);
    List<OrderResponse> getMyOrders(Long buyerId);
    OrderResponse getOrderById(Long orderId, Long buyerId);
}