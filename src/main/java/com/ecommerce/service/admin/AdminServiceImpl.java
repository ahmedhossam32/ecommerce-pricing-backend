package com.ecommerce.service.admin;

import com.ecommerce.dto.request.ApproveRequest;
import com.ecommerce.dto.request.OverrideRequest;
import com.ecommerce.dto.request.RejectRequest;
import com.ecommerce.dto.response.AdminRequestResponse;
import com.ecommerce.dto.response.AdminStatsResponse;
import com.ecommerce.entity.ApprovedDecision;
import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.PricingRequestStatus;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.enums.Role;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ApprovedDecisionRepository;
import com.ecommerce.repository.PricingRequestRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.pricing.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PricingRequestRepository pricingRequestRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApprovedDecisionRepository approvedDecisionRepository;
    private final RoutingService routingService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestResponse> getPendingRequests() {
        return pricingRequestRepository
                .findByStatusAndProduct_Status(PricingRequestStatus.PENDING, ProductStatus.PENDING_REVIEW)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    @Transactional
    public Map<String, String> approveRequest(Long requestId, ApproveRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Product is not pending admin review");
        }
        User seller = product.getSeller();
        double approvedPrice = request.getApprovedPrice();
        double approvedMin = round(approvedPrice * 0.85);
        double approvedMax = round(approvedPrice * 1.15);
        String brand = pr.getBrand() != null ? pr.getBrand() : "UNKNOWN";

        // Update product
        product.setPrice(BigDecimal.valueOf(approvedPrice));
        product.setStatus(ProductStatus.LIVE);
        productRepository.save(product);

        // Update pricing request
        pr.setStatus(PricingRequestStatus.APPROVED);
        pricingRequestRepository.save(pr);

        // Persist approved decision to PostgreSQL
        approvedDecisionRepository.save(ApprovedDecision.builder()
                .brand(brand)
                .category(product.getCategory())
                .approvedMin(BigDecimal.valueOf(approvedMin))
                .approvedMax(BigDecimal.valueOf(approvedMax))
                .build());

        // Cache in Redis
        routingService.cacheApprovedRange(brand, product.getCategory(), approvedPrice);

        // Email seller
        emailService.sendApprovalEmail(
                seller.getEmail(), seller.getName(),
                product.getName(), approvedPrice, request.getAdminNote());

        return Map.of("message", "Product approved and seller notified.", "status", "APPROVED");
    }

    @Override
    @Transactional
    public Map<String, String> rejectRequest(Long requestId, RejectRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        User seller = product.getSeller();

        double suggested = pr.getSuggestedPrice().doubleValue();
        double minRange = round(suggested * 0.85);
        double maxRange = round(suggested * 1.15);

        // Update product and pricing request
        product.setStatus(ProductStatus.REJECTED);
        productRepository.save(product);

        pr.setStatus(PricingRequestStatus.REJECTED);
        pricingRequestRepository.save(pr);

        // Email seller
        emailService.sendRejectionEmail(
                seller.getEmail(), seller.getName(),
                product.getName(), request.getRejectionReason(), minRange, maxRange);

        return Map.of("message", "Product rejected and seller notified.", "status", "REJECTED");
    }

    @Override
    @Transactional
    public Map<String, String> overridePrice(Long productId, OverrideRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        double oldPrice = product.getPrice() != null
                ? product.getPrice().doubleValue() : 0.0;
        double newPrice = request.getNewPrice();
        String brand = product.getBrand() != null ? product.getBrand() : "UNKNOWN";
        User seller = product.getSeller();

        product.setPrice(BigDecimal.valueOf(newPrice));
        productRepository.save(product);

        routingService.cacheApprovedRange(brand, product.getCategory(), newPrice);

        emailService.sendOverrideEmail(
                seller.getEmail(),
                seller.getName(),
                product.getName(),
                oldPrice,
                newPrice,
                request.getAdminNote()
        );

        return Map.of(
                "message", "Price overridden, cache updated and seller notified.",
                "oldPrice", String.valueOf(oldPrice),
                "newPrice", String.valueOf(newPrice)
        );
    }

    @Override
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .totalProducts(productRepository.count())
                .liveProducts(productRepository.countByStatus(ProductStatus.LIVE))
                .pendingReview(productRepository.countByStatus(ProductStatus.PENDING_REVIEW))
                .rejectedProducts(productRepository.countByStatus(ProductStatus.REJECTED))
                .totalSellers(userRepository.countByRole(Role.SELLER))
                .totalApprovedDecisions(approvedDecisionRepository.count())
                .build();
    }

    private AdminRequestResponse toAdminResponse(PricingRequest pr) {
        Product product = pr.getProduct();
        User seller = product.getSeller();
        return AdminRequestResponse.builder()
                .requestId(pr.getId())
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .brand(pr.getBrand())
                .sellerName(seller.getName())
                .sellerEmail(seller.getEmail())
                .suggestedPrice(pr.getSuggestedPrice() != null ? pr.getSuggestedPrice().doubleValue() : null)
                .sellerPrice(pr.getSellerPrice() != null ? pr.getSellerPrice().doubleValue() : null)
                .sellerReasoning(pr.getSellerReasoning())
                .marketPriceMin(pr.getMarketPriceMin() != null ? pr.getMarketPriceMin().doubleValue() : null)
                .marketPriceMax(pr.getMarketPriceMax() != null ? pr.getMarketPriceMax().doubleValue() : null)
                .llmConfidence(pr.getLlmConfidence())
                .mlBaselinePrice(pr.getMlBaselinePrice() != null ? pr.getMlBaselinePrice().doubleValue() : null)
                .createdAt(pr.getCreatedAt())
                .build();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}