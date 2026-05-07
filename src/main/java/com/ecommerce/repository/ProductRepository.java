package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySeller(User seller);
    Optional<Product> findByIdAndSeller(Long id, User seller);
    long countByStatus(ProductStatus status);
    List<Product> findByStatus(ProductStatus status);
}