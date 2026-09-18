package com.ecommerce.order.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @Query("SELECT o FROM Order o JOIN FETCH o.product p JOIN FETCH p.seller WHERE o.buyer.id = :buyerId ORDER BY o.createdAt DESC")
    List<Order> findByBuyerIdWithProductAndSellerOrderByCreatedAtDesc(@Param("buyerId") Long buyerId);

    @Query("SELECT COALESCE(SUM(o.priceAtPurchase), 0) FROM Order o WHERE o.product.seller = :seller")
    Double calculateRevenueForSeller(@Param("seller") User seller);

    long countByProductSeller(User seller);
}