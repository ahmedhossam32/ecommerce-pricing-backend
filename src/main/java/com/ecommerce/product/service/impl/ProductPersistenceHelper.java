package com.ecommerce.service.product;

import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.PricingRequestStatus;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.repository.PricingRequestRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductPersistenceHelper {

    private final ProductRepository productRepository;
    private final PricingRequestRepository pricingRequestRepository;

    @Transactional
    public Product saveDraftProduct(ProductListingRequest request, User seller) {
        Product product = Product.builder()
                .seller(seller)
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .weight(request.getWeight())
                .freightValue(request.getFreightValue())
                .photosQty(request.getPhotosQty())
                .status(ProductStatus.DRAFT)
                .build();
        return productRepository.save(product);
    }

    @Transactional
    public void finalizePricingRequest(Product product, ProductListingRequest request,
                                       PricingSuggestionResponse suggestion) {
        product.setBrand(suggestion.getBrand());
        switch (suggestion.getStatus()) {
            case "PENDING_ADMIN" -> product.setStatus(ProductStatus.PENDING_REVIEW);
            // PENDING_SELLER stays DRAFT — seller must accept or dispute
        }
        // Merge the detached entity back into this transaction's persistence context
        // before using it as a FK reference in PricingRequest.
        Product managedProduct = productRepository.save(product);

        PricingRequest pr = PricingRequest.builder()
                .product(managedProduct)
                .suggestedPrice(BigDecimal.valueOf(suggestion.getSuggestedPrice()))
                .brand(suggestion.getBrand())
                .llmConfidence(suggestion.getConfidence())
                .mlBaselinePrice(suggestion.getMlBaselinePrice() != null
                        ? BigDecimal.valueOf(suggestion.getMlBaselinePrice()) : null)
                .marketPriceMin(suggestion.getMarketPriceMin() != null
                        ? BigDecimal.valueOf(suggestion.getMarketPriceMin()) : null)
                .marketPriceMax(suggestion.getMarketPriceMax() != null
                        ? BigDecimal.valueOf(suggestion.getMarketPriceMax()) : null)
                .condition(suggestion.getCondition())
                .conditionNotes(suggestion.getConditionNotes())
                .conditionGrade(request.getConditionGrade())
                .reasoning(suggestion.getReasoning())
                .status(PricingRequestStatus.PENDING)
                .build();
        pricingRequestRepository.save(pr);
    }
}
