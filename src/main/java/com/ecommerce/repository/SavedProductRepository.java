package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.SavedProduct;
import com.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {
    List<SavedProduct> findByBuyer(User buyer);
    Optional<SavedProduct> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
}
