package com.ecommerce.cart.service.impl;

import com.ecommerce.cart.dto.response.CartResponse;
import com.ecommerce.common.enums.ProductStatus;
import com.ecommerce.common.enums.Role;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
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
 * Proves the DELETED-product cart leak fix: a product that gets admin-deleted after
 * it was already added to a buyer's cart must disappear from getCart(), without the
 * CartItem row itself being destroyed.
 */
@DataJpaTest
@Import(CartServiceImpl.class)
@DisplayName("CartServiceImpl — soft-deleted products are hidden from the cart")
class CartServiceImplDeletedProductTest {

    @Autowired private CartServiceImpl cartService;
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
    @DisplayName("getCart() stops returning an item once its product is admin-deleted")
    void getCart_excludesItemWhoseProductWasDeleted() {
        Product product = productRepository.save(Product.builder()
                .seller(seller).name("Widget").category("electronics")
                .status(ProductStatus.LIVE).price(BigDecimal.valueOf(99.0))
                .build());

        cartService.addToCart(product.getId(), buyer);
        assertThat(cartService.getCart(buyer)).hasSize(1);

        // Simulate an admin soft-delete (AdminServiceImpl.deleteProduct's own mutation).
        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);

        List<CartResponse> cart = cartService.getCart(buyer);
        assertThat(cart)
                .as("Deleted product must no longer appear in the buyer's cart")
                .isEmpty();
    }
}
