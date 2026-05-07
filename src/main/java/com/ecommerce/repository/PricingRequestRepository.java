package com.ecommerce.repository;

import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.Product;
import com.ecommerce.enums.PricingRequestStatus;
import com.ecommerce.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingRequestRepository extends JpaRepository<PricingRequest, Long> {
    List<PricingRequest> findByStatus(PricingRequestStatus status);
    List<PricingRequest> findByStatusAndProduct_Status(PricingRequestStatus status, ProductStatus productStatus);
    Optional<PricingRequest> findTopByProductOrderByCreatedAtDesc(Product product);
}