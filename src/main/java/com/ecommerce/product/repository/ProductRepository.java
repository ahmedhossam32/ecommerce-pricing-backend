package com.ecommerce.product.repository;

import com.ecommerce.user.entity.User;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySellerAndStatusNot(User seller, ProductStatus status);
    Optional<Product> findByIdAndSeller(Long id, User seller);
    long countByStatus(ProductStatus status);
    List<Product> findByStatus(ProductStatus status);
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.seller ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllByOrderByCreatedAtDescWithSeller(Pageable pageable);

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.seller " +
                   "WHERE p.status = :status ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.status = :status")
    Page<Product> findByStatusOrderByCreatedAtDescWithSeller(
            @Param("status") ProductStatus status, Pageable pageable);


}