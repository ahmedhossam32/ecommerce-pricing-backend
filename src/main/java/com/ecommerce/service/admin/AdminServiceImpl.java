package com.ecommerce.service.admin;

import com.ecommerce.dto.request.ApproveRequest;
import com.ecommerce.dto.request.OverrideRequest;
import com.ecommerce.dto.request.RejectRequest;
import com.ecommerce.dto.response.AdminProductResponse;
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
import com.ecommerce.repository.OrderRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PricingRequestRepository pricingRequestRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApprovedDecisionRepository approvedDecisionRepository;
    private final OrderRepository orderRepository;
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
    public Map<String, String> approveRequest(Long requestId, ApproveRequest request) {
        ApprovalData data = doApproveTransaction(requestId, request);
        emailService.sendApprovalEmail(
                data.sellerEmail(), data.sellerName(),
                data.productName(), data.approvedPrice(), data.adminNote());
        return data.response();
    }

    @Override
    public Map<String, String> rejectRequest(Long requestId, RejectRequest request) {
        RejectionData data = doRejectTransaction(requestId, request);
        emailService.sendRejectionEmail(
                data.sellerEmail(), data.sellerName(),
                data.productName(), data.rejectionReason(), data.minRange(), data.maxRange());
        return data.response();
    }

    @Override
    public Map<String, String> overridePrice(Long productId, OverrideRequest request) {
        OverrideData data = doOverrideTransaction(productId, request);
        emailService.sendOverrideEmail(
                data.sellerEmail(), data.sellerName(),
                data.productName(), data.oldPrice(), data.newPrice(), data.adminNote());
        return data.response();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRequestResponse getRequestById(Long requestId) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));
        return toAdminResponse(pr);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminProductResponse> getAllProducts(String status) {
        List<Product> products = (status != null && !status.isBlank())
                ? productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.valueOf(status.toUpperCase()))
                : productRepository.findAllByOrderByCreatedAtDesc();

        return products.stream()
                .map(this::toAdminProductResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .totalProducts(productRepository.count())
                .liveProducts(productRepository.countByStatus(ProductStatus.LIVE))
                .pendingReview(productRepository.countByStatus(ProductStatus.PENDING_REVIEW))
                .rejectedProducts(productRepository.countByStatus(ProductStatus.REJECTED))
                .totalSellers(userRepository.countByRole(Role.SELLER))
                .totalApprovedDecisions(approvedDecisionRepository.count())
                .totalBuyers(userRepository.countByRole(Role.BUYER))
                .totalOrders(orderRepository.count())
                .build();
    }

    @Transactional
    private ApprovalData doApproveTransaction(Long requestId, ApproveRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Product is not pending admin review");
        }
        User seller = product.getSeller();
        double approvedPrice = request.getApprovedPrice();
        double approvedMin = round(approvedPrice * 0.90);
        double approvedMax = round(approvedPrice * 1.10);
        String brand = pr.getBrand() != null ? pr.getBrand() : "UNKNOWN";

        product.setPrice(BigDecimal.valueOf(approvedPrice));
        product.setStatus(ProductStatus.LIVE);
        productRepository.save(product);

        pr.setStatus(PricingRequestStatus.APPROVED);
        pricingRequestRepository.save(pr);

        approvedDecisionRepository.save(ApprovedDecision.builder()
                .brand(brand)
                .category(product.getCategory())
                .approvedMin(BigDecimal.valueOf(approvedMin))
                .approvedMax(BigDecimal.valueOf(approvedMax))
                .build());

        routingService.cacheApprovedRange(brand, product.getCategory(), approvedPrice);

        return new ApprovalData(
                seller.getEmail(), seller.getName(), product.getName(),
                approvedPrice, request.getAdminNote(),
                Map.of("message", "Product approved and seller notified.", "status", "APPROVED"));
    }

    @Transactional
    private RejectionData doRejectTransaction(Long requestId, RejectRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Product is not pending review and cannot be rejected");
        }
        User seller = product.getSeller();

        double suggested = pr.getSuggestedPrice().doubleValue();
        double minRange = round(suggested * 0.90);
        double maxRange = round(suggested * 1.10);

        product.setStatus(ProductStatus.REJECTED);
        productRepository.save(product);

        pr.setStatus(PricingRequestStatus.REJECTED);
        pricingRequestRepository.save(pr);

        return new RejectionData(
                seller.getEmail(), seller.getName(), product.getName(),
                request.getRejectionReason(), minRange, maxRange,
                Map.of("message", "Product rejected and seller notified.", "status", "REJECTED"));
    }

    @Transactional
    private OverrideData doOverrideTransaction(Long productId, OverrideRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getStatus() != ProductStatus.LIVE) {
            throw new IllegalStateException("Only LIVE products can have their price overridden");
        }
        double oldPrice = product.getPrice() != null
                ? product.getPrice().doubleValue() : 0.0;
        double newPrice = request.getNewPrice();
        String brand = product.getBrand() != null ? product.getBrand() : "UNKNOWN";
        User seller = product.getSeller();

        product.setPrice(BigDecimal.valueOf(newPrice));
        productRepository.save(product);

        routingService.cacheApprovedRange(brand, product.getCategory(), newPrice);

        return new OverrideData(
                seller.getEmail(), seller.getName(), product.getName(),
                oldPrice, newPrice, request.getAdminNote(),
                Map.of(
                        "message", "Price overridden, cache updated and seller notified.",
                        "oldPrice", String.valueOf(oldPrice),
                        "newPrice", String.valueOf(newPrice)));
    }

    private AdminProductResponse toAdminProductResponse(Product product) {
        User seller = product.getSeller();

        Optional<PricingRequest> latestPr = pricingRequestRepository
                .findTopByProductOrderByCreatedAtDesc(product);

        Double suggestedPrice = latestPr
                .map(pr -> pr.getSuggestedPrice() != null ? pr.getSuggestedPrice().doubleValue() : null)
                .orElse(null);

        Long requestId = latestPr
                .map(pr -> pr.getStatus() == PricingRequestStatus.PENDING ? pr.getId() : null)
                .orElse(null);

        return AdminProductResponse.builder()
                .requestId(requestId)
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .brand(product.getBrand())
                .status(product.getStatus().name())
                .price(product.getPrice() != null ? product.getPrice().doubleValue() : null)
                .suggestedPrice(suggestedPrice)
                .sellerName(seller.getName())
                .sellerEmail(seller.getEmail())
                .sellerProfilePictureUrl(seller.getProfilePictureUrl())
                .createdAt(product.getCreatedAt())
                .imageUrls(product.getImageUrls() != null ? product.getImageUrls() : List.of())
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
                .requestType(pr.getSellerReasoning() != null && pr.getSellerPrice() != null ? "DISPUTE" : "NEW_LISTING")
                .imageUrls(product.getImageUrls())
                .sellerProfilePictureUrl(seller.getProfilePictureUrl())
                .build();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record ApprovalData(
            String sellerEmail,
            String sellerName,
            String productName,
            double approvedPrice,
            String adminNote,
            Map<String, String> response
    ) {}

    private record RejectionData(
            String sellerEmail,
            String sellerName,
            String productName,
            String rejectionReason,
            double minRange,
            double maxRange,
            Map<String, String> response
    ) {}

    private record OverrideData(
            String sellerEmail,
            String sellerName,
            String productName,
            double oldPrice,
            double newPrice,
            String adminNote,
            Map<String, String> response
    ) {}
}
