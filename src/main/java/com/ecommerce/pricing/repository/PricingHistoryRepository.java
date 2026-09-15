package com.ecommerce.repository;

import com.ecommerce.entity.PricingHistory;
import com.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingHistoryRepository extends JpaRepository<PricingHistory, Long> {
    void deleteByProduct(Product product);
}