package com.ecommerce.service.buyer;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.BuyerProductResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PriceHistoryResponse;
import com.ecommerce.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BuyerService {
    List<BuyerProductResponse> getAllLiveProducts();
    Page<BuyerProductResponse> getAllLiveProducts(Pageable pageable);
    BuyerProductResponse getProductById(Long productId);
    List<PriceHistoryResponse> getProductHistory(Long productId);
    OrderResponse placeOrder(OrderRequest request, User buyer);
    List<OrderResponse> getMyOrders(User buyer);
    OrderResponse getOrderById(Long orderId, User buyer);
}