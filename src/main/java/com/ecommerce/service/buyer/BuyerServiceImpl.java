package com.ecommerce.service.buyer;

import com.ecommerce.dto.request.OrderRequest;
import com.ecommerce.dto.response.BuyerProductResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PriceHistoryResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.admin.EmailService;
import org.springframework.security.access.AccessDeniedException;
import com.ecommerce.repository.PricingRequestRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuyerServiceImpl implements BuyerService {

    private final ProductRepository productRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<BuyerProductResponse> getAllLiveProducts() {
        return productRepository.findByStatus(ProductStatus.LIVE)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BuyerProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStatus() != ProductStatus.LIVE) {
            throw new ResourceNotFoundException("Product not found");
        }
        return toDetailResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getProductHistory(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

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
    public OrderResponse placeOrder(OrderRequest request, User buyer) {
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

        return OrderResponse.builder()
                .orderId(order.getId())
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice().doubleValue())
                .buyerName(buyer.getName())
                .sellerName(product.getSeller().getName())
                .createdAt(order.getCreatedAt())
                .message("Order placed successfully!")
                .imageUrls(product.getImageUrls())
                .category(product.getCategory())
                .brand(product.getBrand())
                .sellerProfilePictureUrl(
                    product.getSeller().getProfilePictureUrl() != null
                        ? product.getSeller().getProfilePictureUrl()
                        : "https://res.cloudinary.com/demo/image/upload/avatar.png"
                )
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(User buyer) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId())
                .stream()
                .map(o -> OrderResponse.builder()
                        .orderId(o.getId())
                        .productId(o.getProduct().getId())
                        .productName(o.getProduct().getName())
                        .price(o.getPriceAtPurchase().doubleValue())
                        .buyerName(o.getBuyer().getName())
                        .sellerName(o.getProduct().getSeller().getName())
                        .createdAt(o.getCreatedAt())
                        .message("Order placed successfully!")
                        .imageUrls(o.getProduct().getImageUrls())
                        .category(o.getProduct().getCategory())
                        .brand(o.getProduct().getBrand())
                        .sellerProfilePictureUrl(
                            o.getProduct().getSeller().getProfilePictureUrl() != null
                                ? o.getProduct().getSeller().getProfilePictureUrl()
                                : "https://res.cloudinary.com/demo/image/upload/avatar.png"
                        )
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, User buyer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new AccessDeniedException("You are not authorized to view this order");
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .productId(order.getProduct().getId())
                .productName(order.getProduct().getName())
                .price(order.getPriceAtPurchase().doubleValue())
                .buyerName(order.getBuyer().getName())
                .sellerName(order.getProduct().getSeller().getName())
                .createdAt(order.getCreatedAt())
                .message("Order placed successfully!")
                .imageUrls(order.getProduct().getImageUrls())
                .category(order.getProduct().getCategory())
                .brand(order.getProduct().getBrand())
                .sellerProfilePictureUrl(
                    order.getProduct().getSeller().getProfilePictureUrl() != null
                        ? order.getProduct().getSeller().getProfilePictureUrl()
                        : "https://res.cloudinary.com/demo/image/upload/avatar.png"
                )
                .build();
    }

    private BuyerProductResponse toSummaryResponse(Product p) {
        return BuyerProductResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .brand(p.getBrand())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .weight(p.getWeight())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImageUrls())
                .sellerProfilePictureUrl(
                    p.getSeller().getProfilePictureUrl() != null
                        ? p.getSeller().getProfilePictureUrl()
                        : "https://res.cloudinary.com/demo/image/upload/avatar.png"
                )
                .build();
    }

    private BuyerProductResponse toDetailResponse(Product p) {
        return BuyerProductResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .brand(p.getBrand())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .weight(p.getWeight())
                .photosQty(p.getPhotosQty())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImageUrls())
                .sellerProfilePictureUrl(
                    p.getSeller().getProfilePictureUrl() != null
                        ? p.getSeller().getProfilePictureUrl()
                        : "https://res.cloudinary.com/demo/image/upload/avatar.png"
                )
                .build();
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