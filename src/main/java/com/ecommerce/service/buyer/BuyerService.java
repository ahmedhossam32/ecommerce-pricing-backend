package com.ecommerce.service.buyer;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.BuyerProductResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PriceHistoryResponse;
import com.ecommerce.entity.User;

import java.util.List;

public interface BuyerService {
    List<BuyerProductResponse> getAllLiveProducts();
    BuyerProductResponse getProductById(Long productId);
    List<PriceHistoryResponse> getProductHistory(Long productId);
    OrderResponse placeOrder(OrderRequest request, User buyer);
    List<OrderResponse> getMyOrders(User buyer);
}