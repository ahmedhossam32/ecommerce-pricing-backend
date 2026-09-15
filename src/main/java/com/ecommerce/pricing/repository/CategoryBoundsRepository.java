package com.ecommerce.pricing.repository;
import com.ecommerce.pricing.entity.CategoryBounds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryBoundsRepository extends JpaRepository<CategoryBounds, Long> {
    Optional<CategoryBounds> findByCategory(String category);
}