package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySeller(User seller);
    List<Product> findBySellerAndStatusNot(User seller, ProductStatus status);
    Optional<Product> findByIdAndSeller(Long id, User seller);
    long countByStatus(ProductStatus status);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findAllByOrderByCreatedAtDesc();
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);

    @Query("SELECT COALESCE(SUM(o.priceAtPurchase), 0) FROM Order o WHERE o.product.seller = :seller")
    Double calculateRevenueForSeller(@Param("seller") User seller);
}