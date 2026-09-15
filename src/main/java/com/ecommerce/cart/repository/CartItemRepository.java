package com.ecommerce.repository;

import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByBuyer(User buyer);

    @Query("SELECT ci FROM CartItem ci " +
           "JOIN FETCH ci.product p " +
           "JOIN FETCH p.seller " +
           "WHERE ci.buyer = :buyer")
    List<CartItem> findByBuyerWithProductAndSeller(@Param("buyer") User buyer);
    Optional<CartItem> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
    void deleteAllByBuyer(User buyer);
    void deleteByProduct(Product product);
}
