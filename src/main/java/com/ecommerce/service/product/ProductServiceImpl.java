package com.ecommerce.service.product;

import com.ecommerce.dto.request.AcceptPriceRequest;
import com.ecommerce.dto.request.DisputePriceRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.AcceptPriceResponse;
import com.ecommerce.dto.response.DisputeResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.dto.response.SellerDashboardResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.User;
import com.ecommerce.enums.PricingRequestStatus;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PricingRequestRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.pricing.PricingService;
import com.ecommerce.service.pricing.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final PricingService pricingService;
    private final RoutingService routingService;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public PricingSuggestionResponse listProduct(ProductListingRequest request, User seller) {
        // Save product as DRAFT first so we have an id for PricingRequest
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
        product = productRepository.save(product);

        // Run the full pricing engine
        PricingSuggestionResponse suggestion = pricingService.getSuggestion(request, seller);

        // Set brand on product from LLM extraction result
        product.setBrand(suggestion.getBrand());

        // Persist PricingRequest with all LLM-derived fields linked to this product
        PricingRequest pr = PricingRequest.builder()
                .product(product)
                .suggestedPrice(BigDecimal.valueOf(suggestion.getSuggestedPrice()))
                .brand(suggestion.getBrand())
                .llmConfidence(suggestion.getConfidence())
                .mlBaselinePrice(suggestion.getMlBaselinePrice() != null
                        ? BigDecimal.valueOf(suggestion.getMlBaselinePrice()) : null)
                .marketPriceMin(suggestion.getMarketPriceMin() != null
                        ? BigDecimal.valueOf(suggestion.getMarketPriceMin()) : null)
                .marketPriceMax(suggestion.getMarketPriceMax() != null
                        ? BigDecimal.valueOf(suggestion.getMarketPriceMax()) : null)
                .status(PricingRequestStatus.PENDING)
                .build();
        pricingRequestRepository.save(pr);

        // Update product status based on routing decision
        switch (suggestion.getStatus()) {
            case "PENDING_ADMIN" -> product.setStatus(ProductStatus.PENDING_REVIEW);
            // PENDING_SELLER stays DRAFT — seller must accept or dispute
        }
        productRepository.save(product);

        return PricingSuggestionResponse.builder()
                .productId(product.getId())
                .suggestedPrice(suggestion.getSuggestedPrice())
                .minRange(suggestion.getMinRange())
                .maxRange(suggestion.getMaxRange())
                .confidence(suggestion.getConfidence())
                .status(suggestion.getStatus())
                .message(suggestion.getMessage())
                .brand(suggestion.getBrand())
                .mlBaselinePrice(suggestion.getMlBaselinePrice())
                .marketPriceMin(suggestion.getMarketPriceMin())
                .marketPriceMax(suggestion.getMarketPriceMax())
                .build();
    }

    @Override
    @Transactional
    public AcceptPriceResponse acceptPrice(Long productId, AcceptPriceRequest request, User seller) {
        Product product = productRepository.findByIdAndSeller(productId, seller)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new IllegalStateException("Product is not awaiting a seller decision");
        }

        PricingRequest pr = pricingRequestRepository.findTopByProductOrderByCreatedAtDesc(product)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found"));

        double suggested = pr.getSuggestedPrice().doubleValue();
        double minRange = round(suggested * 0.85);
        double maxRange = round(suggested * 1.15);

        double finalPrice;
        if (request != null && request.getChosenPrice() != null) {
            double chosen = request.getChosenPrice();
            if (chosen < minRange || chosen > maxRange) {
                throw new IllegalArgumentException(String.format(
                        "Chosen price %.2f is outside allowed range [%.2f - %.2f]", chosen, minRange, maxRange));
            }
            finalPrice = chosen;
        } else {
            finalPrice = suggested;
        }

        product.setStatus(ProductStatus.LIVE);
        product.setPrice(BigDecimal.valueOf(finalPrice));
        productRepository.save(product);

        pr.setStatus(PricingRequestStatus.APPROVED);
        pr.setSellerPrice(BigDecimal.valueOf(finalPrice));
        pricingRequestRepository.save(pr);

        String brand = pr.getBrand() != null ? pr.getBrand() : "UNKNOWN";
        routingService.cacheApprovedRange(brand, product.getCategory(), finalPrice);

        return AcceptPriceResponse.builder()
                .productId(product.getId())
                .finalPrice(finalPrice)
                .status("ACCEPTED")
                .message("Your product is now live at $" + finalPrice)
                .build();
    }

    @Override
    @Transactional
    public DisputeResponse disputePrice(Long productId, DisputePriceRequest request, User seller) {
        Product product = productRepository.findByIdAndSeller(productId, seller)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new IllegalStateException("Product is not awaiting a seller decision");
        }

        PricingRequest pr = pricingRequestRepository.findTopByProductOrderByCreatedAtDesc(product)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing request not found"));

        pr.setSellerPrice(BigDecimal.valueOf(request.getSellerPrice()));
        pr.setSellerReasoning(request.getSellerReasoning());
        pr.setStatus(PricingRequestStatus.PENDING);
        pricingRequestRepository.save(pr);

        product.setStatus(ProductStatus.PENDING_REVIEW);
        productRepository.save(product);

        return DisputeResponse.builder()
                .productId(product.getId())
                .suggestedPrice(pr.getSuggestedPrice().doubleValue())
                .sellerPrice(request.getSellerPrice())
                .status("PENDING_ADMIN")
                .message("Your dispute has been submitted for admin review.")
                .build();
    }

    @Override
    public List<ProductResponse> getSellerProducts(User seller) {
        return productRepository.findBySeller(seller).stream()
                .map(p -> {
                    Double suggestedPrice = pricingRequestRepository
                            .findTopByProductOrderByCreatedAtDesc(p)
                            .map(pr -> pr.getSuggestedPrice().doubleValue())
                            .orElse(null);
                    return toResponse(p, suggestedPrice);
                })
                .toList();
    }

    @Override
    public ProductResponse getProductById(Long productId, User seller) {
        Product product = productRepository.findByIdAndSeller(productId, seller)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Double suggestedPrice = pricingRequestRepository
                .findTopByProductOrderByCreatedAtDesc(product)
                .map(pr -> pr.getSuggestedPrice().doubleValue())
                .orElse(null);

        return toResponse(product, suggestedPrice);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerDashboardResponse getDashboard(User seller) {
        List<Product> products = productRepository.findBySeller(seller);
        Double revenue = orderRepository.calculateRevenueForSeller(seller);
        return SellerDashboardResponse.builder()
                .totalProducts(products.size())
                .liveProducts(products.stream().filter(p -> p.getStatus() == ProductStatus.LIVE).count())
                .pendingReview(products.stream().filter(p -> p.getStatus() == ProductStatus.PENDING_REVIEW).count())
                .rejected(products.stream().filter(p -> p.getStatus() == ProductStatus.REJECTED).count())
                .draft(products.stream().filter(p -> p.getStatus() == ProductStatus.DRAFT).count())
                .totalRevenue(revenue != null ? revenue : 0.0)
                .build();
    }

    private ProductResponse toResponse(Product p, Double suggestedPrice) {
        return ProductResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .brand(p.getBrand())
                .status(p.getStatus().name())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .suggestedPrice(suggestedPrice)
                .sellerName(p.getSeller() != null ? p.getSeller().getName() : null)
                .weight(p.getWeight())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImageUrls())
                .build();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}