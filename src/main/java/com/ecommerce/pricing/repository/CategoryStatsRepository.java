package com.ecommerce.pricing.repository;
import com.ecommerce.pricing.entity.CategoryStats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryStatsRepository extends JpaRepository<CategoryStats, Long> {
    Optional<CategoryStats> findByCategory(String category);
}