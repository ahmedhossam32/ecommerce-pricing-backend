package com.ecommerce.buyer.service.impl;

import com.ecommerce.buyer.mapper.BuyerMapper;
import com.ecommerce.order.entity.Order;
import com.ecommerce.pricing.entity.PricingRequest;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.common.service.EmailService;
import com.ecommerce.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import com.ecommerce.pricing.repository.PricingRequestRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.buyer.dto.response.BuyerProductResponse;
import com.ecommerce.buyer.service.BuyerService;
import com.ecommerce.buyer.dto.request.OrderRequest;
import com.ecommerce.buyer.dto.response.OrderResponse;
import com.ecommerce.buyer.dto.response.PriceHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuyerServiceImpl implements BuyerService {

    private final ProductRepository productRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final BuyerMapper buyerMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<BuyerProductResponse> getAllLiveProducts(Pageable pageable) {
        return productRepository.findByStatusWithSeller(ProductStatus.LIVE, pageable)
                .map(buyerMapper::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BuyerProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStatus() != ProductStatus.LIVE) {
            throw new ResourceNotFoundException("Product not found");
        }
        return buyerMapper.toDetailResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getProductHistory(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStatus() != ProductStatus.LIVE) {
            throw new ResourceNotFoundException("Product not found");
        }

        List<PricingRequest> requests = pricingRequestRepository.findByProduct(product);

        if (requests.isEmpty()) {
            return List.of(PriceHistoryResponse.builder()
                    .price(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0)
                    .event("Current price")
                    .date(product.getCreatedAt())
                    .build());
        }

        return requests.stream()
                .map(pr -> PriceHistoryResponse.builder()
                        .price(resolvePrice(pr))
                        .event(resolveEvent(pr))
                        .date(pr.getCreatedAt())
                        .build())
                .sorted(Comparator.comparing(PriceHistoryResponse::getDate))
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(OrderRequest request, Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.LIVE) {
            throw new IllegalStateException("Product is not available for purchase");
        }

        if (product.getSeller().getId().equals(buyer.getId())) {
            throw new IllegalStateException("You cannot purchase your own product");
        }

        Order order = Order.builder()
                .buyer(buyer)
                .product(product)
                .priceAtPurchase(product.getPrice())
                .build();
        orderRepository.save(order);

        emailService.sendOrderConfirmationEmail(
                buyer.getEmail(),
                buyer.getName(),
                product.getName(),
                order.getPriceAtPurchase().doubleValue()
        );

        return buyerMapper.toOrderResponse(order, "Order placed successfully!");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(Long buyerId) {
        return orderRepository.findByBuyerIdWithProductAndSellerOrderByCreatedAtDesc(buyerId)
                .stream()
                .map(o -> buyerMapper.toOrderResponse(o, "Order placed successfully!"))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long buyerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new AccessDeniedException("You are not authorized to view this order");
        }

        return buyerMapper.toOrderResponse(order, "Order placed successfully!");
    }

    private double resolvePrice(PricingRequest pr) {
        if (pr.getSellerPrice() != null) return pr.getSellerPrice().doubleValue();
        if (pr.getSuggestedPrice() != null) return pr.getSuggestedPrice().doubleValue();
        return 0.0;
    }

    private String resolveEvent(PricingRequest pr) {
        return switch (pr.getStatus()) {
            case APPROVED -> pr.getSellerReasoning() != null ? "Admin approved" : "Seller accepted";
            case REJECTED -> "Rejected";
            default -> "Pending review";
        };
    }
}