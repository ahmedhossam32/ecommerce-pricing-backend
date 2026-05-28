package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyer(User buyer);
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @Query("SELECT COALESCE(SUM(o.priceAtPurchase), 0) FROM Order o WHERE o.product.seller = :seller")
    Double calculateRevenueForSeller(@Param("seller") User seller);

    long countByProductSeller(User seller);
}