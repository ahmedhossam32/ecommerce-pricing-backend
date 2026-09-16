package com.ecommerce.wishlist.repository;

import com.ecommerce.common.enums.ProductStatus;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.wishlist.entity.SavedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {
    List<SavedProduct> findByBuyerAndProduct_StatusNot(User buyer, ProductStatus status);
    Optional<SavedProduct> findByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyerAndProduct(User buyer, Product product);
    void deleteByBuyer(User buyer);
}
