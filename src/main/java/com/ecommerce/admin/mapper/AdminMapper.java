package com.ecommerce.admin.mapper;

import com.ecommerce.admin.dto.response.AdminProductResponse;
import com.ecommerce.admin.dto.response.AdminRequestResponse;
import com.ecommerce.common.enums.PricingRequestStatus;
import com.ecommerce.pricing.entity.CategoryBounds;
import com.ecommerce.pricing.entity.PricingRequest;
import com.ecommerce.pricing.repository.CategoryBoundsRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminMapper {

    private final CategoryBoundsRepository categoryBoundsRepository;

    public AdminProductResponse toAdminProductResponse(Product product, PricingRequest latestPrRaw) {
        User seller = product.getSeller();
        Optional<PricingRequest> latestPr = Optional.ofNullable(latestPrRaw);

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
                .condition(latestPr.map(pr -> pr.getCondition()).orElse(null))
                .conditionGrade(latestPr.map(pr -> pr.getConditionGrade()).orElse(null))
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

    public AdminRequestResponse toAdminResponse(PricingRequest pr) {
        CategoryBounds bounds = categoryBoundsRepository
                .findByCategory(pr.getProduct().getCategory())
                .orElse(null);
        return toAdminResponse(pr, bounds);
    }

    public AdminRequestResponse toAdminResponse(PricingRequest pr, CategoryBounds bounds) {
        Product product = pr.getProduct();
        User seller = product.getSeller();

        String routingReason = "LOW_CONFIDENCE";
        if (bounds != null) {
            BigDecimal suggested = pr.getSuggestedPrice();
            if (suggested != null &&
                (suggested.compareTo(bounds.getMinPrice()) < 0 ||
                 suggested.compareTo(bounds.getMaxPrice()) > 0)) {
                routingReason = "OUTSIDE_BOUNDS";
            }
        }

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
                .routingReason(routingReason)
                .condition(pr.getCondition())
                .conditionNotes(pr.getConditionNotes())
                .conditionGrade(pr.getConditionGrade())
                .reasoning(pr.getReasoning())
                .imageUrls(product.getImageUrls())
                .sellerProfilePictureUrl(seller.getProfilePictureUrl())
                .build();
    }
}
