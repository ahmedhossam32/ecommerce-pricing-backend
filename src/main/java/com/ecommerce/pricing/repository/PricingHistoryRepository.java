package com.ecommerce.pricing.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.pricing.entity.PricingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingHistoryRepository extends JpaRepository<PricingHistory, Long> {
}