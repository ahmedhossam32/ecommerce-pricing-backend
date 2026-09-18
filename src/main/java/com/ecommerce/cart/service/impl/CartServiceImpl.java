package com.ecommerce.cart.service.impl;

import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.mapper.CartMapper;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.dto.response.CartResponse;
import com.ecommerce.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional
    public CartResponse addToCart(Long productId, User buyer) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.LIVE) {
            throw new IllegalStateException("Product is not available");
        }

        if (product.getSeller().getId().equals(buyer.getId())) {
            throw new IllegalStateException("You cannot add your own product to cart");
        }

        cartItemRepository.findByBuyerAndProduct(buyer, product).ifPresent(c -> {
            throw new IllegalStateException("Product already in cart");
        });

        CartItem item = CartItem.builder()
                .buyer(buyer)
                .product(product)
                .build();
        cartItemRepository.save(item);

        return cartMapper.toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartResponse> getCart(User buyer) {
        return cartItemRepository.findByBuyerWithProductAndSeller(buyer)
                .stream()
                .map(cartMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeFromCart(Long productId, User buyer) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        cartItemRepository.deleteByBuyerAndProduct(buyer, product);
    }

    @Override
    @Transactional
    public void clearCart(User buyer) {
        cartItemRepository.deleteAllByBuyer(buyer);
    }
}
