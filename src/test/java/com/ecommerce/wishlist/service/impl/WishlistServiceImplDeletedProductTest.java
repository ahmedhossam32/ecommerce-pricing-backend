package com.ecommerce.wishlist.service.impl;

import com.ecommerce.common.enums.ProductStatus;
import com.ecommerce.common.enums.Role;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.wishlist.dto.response.SavedProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the DELETED-product wishlist leak fix: a product that gets admin-deleted after
 * it was already saved to a buyer's wishlist must disappear from getSaved(), without the
 * SavedProduct row itself being destroyed.
 */
@DataJpaTest
@Import(WishlistServiceImpl.class)
@DisplayName("WishlistServiceImpl — soft-deleted products are hidden from the wishlist")
class WishlistServiceImplDeletedProductTest {

    @Autowired private WishlistServiceImpl wishlistService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private User buyer;
    private User seller;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.builder()
                .name("Seller").email("seller-" + System.nanoTime() + "@test.com")
                .password("hashed").role(Role.SELLER).build());
        buyer = userRepository.save(User.builder()
                .name("Buyer").email("buyer-" + System.nanoTime() + "@test.com")
                .password("hashed").role(Role.BUYER).build());
    }

    @Test
    @DisplayName("getSaved() stops returning an item once its product is admin-deleted")
    void getSaved_excludesItemWhoseProductWasDeleted() {
        Product product = productRepository.save(Product.builder()
                .seller(seller).name("Widget").category("electronics")
                .status(ProductStatus.LIVE).price(BigDecimal.valueOf(99.0))
                .build());

        wishlistService.saveProduct(product.getId(), buyer);
        assertThat(wishlistService.getSaved(buyer)).hasSize(1);

        // Simulate an admin soft-delete (AdminServiceImpl.deleteProduct's own mutation).
        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);

        List<SavedProductResponse> saved = wishlistService.getSaved(buyer);
        assertThat(saved)
                .as("Deleted product must no longer appear in the buyer's wishlist")
                .isEmpty();
    }
}
