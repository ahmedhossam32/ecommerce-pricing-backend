package com.ecommerce.repository;

import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByBuyer(User buyer);
    Optional<CartItem> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
}
