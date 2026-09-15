package com.ecommerce.pricing.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.common.enums.PricingRequestStatus;
import com.ecommerce.common.enums.ProductStatus;
import com.ecommerce.pricing.entity.PricingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingRequestRepository extends JpaRepository<PricingRequest, Long> {
    List<PricingRequest> findByStatusAndProduct_Status(PricingRequestStatus status, ProductStatus productStatus);
    Optional<PricingRequest> findTopByProductOrderByCreatedAtDesc(Product product);
    List<PricingRequest> findByProduct(Product product);
    List<PricingRequest> findByProductIn(List<Product> products);
}