package com.ecommerce.cart.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("SELECT ci FROM CartItem ci " +
           "JOIN FETCH ci.product p " +
           "JOIN FETCH p.seller " +
           "WHERE ci.buyer = :buyer")
    List<CartItem> findByBuyerWithProductAndSeller(@Param("buyer") User buyer);
    Optional<CartItem> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
    void deleteAllByBuyer(User buyer);
}
