package com.ecommerce.admin.service.impl;

import com.ecommerce.common.service.EmailService;
import com.ecommerce.pricing.entity.ApprovedDecision;
import com.ecommerce.pricing.entity.CategoryBounds;
import com.ecommerce.pricing.entity.PricingRequest;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.common.enums.PricingRequestStatus;
import com.ecommerce.common.enums.ProductStatus;
import com.ecommerce.common.enums.Role;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.pricing.repository.ApprovedDecisionRepository;
import com.ecommerce.pricing.repository.CategoryBoundsRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.pricing.repository.PricingRequestRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.pricing.service.RoutingService;
import com.ecommerce.admin.dto.response.AdminProductResponse;
import com.ecommerce.admin.dto.response.AdminRequestResponse;
import com.ecommerce.admin.mapper.AdminMapper;
import com.ecommerce.admin.service.AdminService;
import com.ecommerce.admin.dto.response.AdminStatsResponse;
import com.ecommerce.admin.dto.request.ApproveRequest;
import com.ecommerce.admin.dto.request.DeleteProductRequest;
import com.ecommerce.admin.dto.request.OverrideRequest;
import com.ecommerce.admin.dto.request.RejectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    private final CategoryBoundsRepository categoryBoundsRepository;
    private final AdminMapper adminMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestResponse> getPendingRequests() {
        List<PricingRequest> pendingRequests = pricingRequestRepository
                .findByStatusAndProductStatusWithProductAndSeller(
                        PricingRequestStatus.PENDING, ProductStatus.PENDING_REVIEW);

        Set<String> categories = pendingRequests.stream()
                .map(pr -> pr.getProduct().getCategory())
                .collect(Collectors.toSet());

        Map<String, CategoryBounds> boundsByCategory = categoryBoundsRepository
                .findByCategoryIn(categories)
                .stream()
                .collect(Collectors.toMap(CategoryBounds::getCategory, b -> b));

        return pendingRequests.stream()
                .map(pr -> adminMapper.toAdminResponse(pr, boundsByCategory.get(pr.getProduct().getCategory())))
                .toList();
    }

    @Override
    @Transactional
    public Map<String, String> approveRequest(Long requestId, ApproveRequest request) {
        ApprovalData data = doApproveTransaction(requestId, request);

        registerCacheAndEmailAfterCommit(
                () -> routingService.cacheApprovedRange(
                        data.brand(), data.category(), data.approvedPrice(), data.condition()),
                () -> emailService.sendApprovalEmail(
                        data.sellerEmail(), data.sellerName(),
                        data.productName(), data.approvedPrice(), data.adminNote()));

        return data.response();
    }

    @Override
    @Transactional
    public Map<String, String> rejectRequest(Long requestId, RejectRequest request) {
        RejectionData data = doRejectTransaction(requestId, request);

        registerEmailAfterCommit(() -> emailService.sendRejectionEmail(
                data.sellerEmail(), data.sellerName(),
                data.productName(), data.rejectionReason(), data.minRange(), data.maxRange()));

        return data.response();
    }

    @Override
    @Transactional
    public Map<String, String> overridePrice(Long productId, OverrideRequest request) {
        OverrideData data = doOverrideTransaction(productId, request);

        registerCacheAndEmailAfterCommit(
                () -> routingService.cacheApprovedRange(
                        data.brand(), data.category(), data.newPrice(), data.condition()),
                () -> emailService.sendOverrideEmail(
                        data.sellerEmail(), data.sellerName(),
                        data.productName(), data.oldPrice(), data.newPrice(), data.adminNote()));

        return data.response();
    }

    private void registerCacheAndEmailAfterCommit(Runnable cacheUpdate, Runnable emailSend) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    cacheUpdate.run();
                } catch (Exception e) {
                    log.warn("Failed to update pricing cache after commit: {}", e.getMessage());
                }
                emailSend.run();
            }
        });
    }

    private void registerEmailAfterCommit(Runnable emailSend) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailSend.run();
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRequestResponse getRequestById(Long requestId) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));
        return adminMapper.toAdminResponse(pr);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminProductResponse> getAllProducts(String status, Pageable pageable) {
        Page<Product> products = (status != null && !status.isBlank())
                ? productRepository.findByStatusOrderByCreatedAtDescWithSeller(parseProductStatus(status), pageable)
                : productRepository.findAllByOrderByCreatedAtDescWithSeller(pageable);

        List<Product> productList = products.getContent();
        Map<Long, PricingRequest> latestRequestByProductId = pricingRequestRepository
                .findByProductIn(productList)
                .stream()
                .collect(Collectors.toMap(
                        pr -> pr.getProduct().getId(),
                        pr -> pr,
                        (existing, incoming) ->
                                incoming.getCreatedAt().isAfter(existing.getCreatedAt()) ? incoming : existing
                ));

        return products.map(product ->
                adminMapper.toAdminProductResponse(product, latestRequestByProductId.get(product.getId())));
    }

    private ProductStatus parseProductStatus(String status) {
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid product status: " + status);
        }
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

    private ApprovalData doApproveTransaction(Long requestId, ApproveRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Product is not pending admin review");
        }
        if (pr.getStatus() != PricingRequestStatus.PENDING) {
            throw new IllegalStateException("Pricing request is not pending");
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

        return new ApprovalData(
                seller.getEmail(), seller.getName(), product.getName(),
                approvedPrice, request.getAdminNote(),
                brand, product.getCategory(), pr.getCondition(),
                Map.of("message", "Product approved and seller notified.", "status", "APPROVED"));
    }

    private RejectionData doRejectTransaction(Long requestId, RejectRequest request) {
        PricingRequest pr = pricingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found: " + requestId));

        Product product = pr.getProduct();
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Product is not pending review and cannot be rejected");
        }
        if (pr.getStatus() != PricingRequestStatus.PENDING) {
            throw new IllegalStateException("Pricing request is not pending");
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

    private OverrideData doOverrideTransaction(Long productId, OverrideRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getStatus() != ProductStatus.LIVE) {
            throw new IllegalStateException("Only LIVE products can have their price overridden");
        }

        PricingRequest latestRequest = pricingRequestRepository
                .findTopByProductOrderByCreatedAtDesc(product)
                .orElseThrow(() -> new IllegalStateException("No pricing request found for this product"));
        if (latestRequest.getStatus() != PricingRequestStatus.APPROVED) {
            throw new IllegalStateException("Only products with an approved pricing request can be overridden");
        }

        double oldPrice = product.getPrice() != null
                ? product.getPrice().doubleValue() : 0.0;
        double newPrice = request.getNewPrice();
        String brand = product.getBrand() != null ? product.getBrand() : "UNKNOWN";
        User seller = product.getSeller();

        product.setPrice(BigDecimal.valueOf(newPrice));
        productRepository.save(product);

        String condition = latestRequest.getCondition();

        return new OverrideData(
                seller.getEmail(), seller.getName(), product.getName(),
                oldPrice, newPrice, request.getAdminNote(),
                brand, product.getCategory(), condition,
                Map.of(
                        "message", "Price overridden, cache updated and seller notified.",
                        "oldPrice", String.valueOf(oldPrice),
                        "newPrice", String.valueOf(newPrice)));
    }

    @Override
    @Transactional
    public Map<String, String> deleteProduct(Long productId, DeleteProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);

        String reason = (request != null && request.getReason() != null
                && !request.getReason().isBlank()) ? request.getReason() : null;

        emailService.sendProductDeletedEmail(
                product.getSeller().getEmail(),
                product.getSeller().getName(),
                product.getName(),
                reason);

        log.info("Product {} deleted by ADMIN", productId);
        return Map.of("message", "Product deleted successfully");
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
