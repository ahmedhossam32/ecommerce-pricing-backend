package com.ecommerce.wishlist.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.wishlist.entity.SavedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {
    List<SavedProduct> findByBuyer(User buyer);
    Optional<SavedProduct> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyer(User buyer);
}
