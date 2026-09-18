package com.ecommerce.pricing.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.pricing.enums.PricingRequestStatus;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.pricing.entity.PricingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PricingRequestRepository extends JpaRepository<PricingRequest, Long> {
    Optional<PricingRequest> findTopByProductOrderByCreatedAtDesc(Product product);
    List<PricingRequest> findByProduct(Product product);
    List<PricingRequest> findByProductIn(List<Product> products);

    @Query("SELECT pr FROM PricingRequest pr JOIN FETCH pr.product p JOIN FETCH p.seller " +
           "WHERE pr.status = :status AND p.status = :productStatus")
    List<PricingRequest> findByStatusAndProductStatusWithProductAndSeller(
            @Param("status") PricingRequestStatus status,
            @Param("productStatus") ProductStatus productStatus);
}