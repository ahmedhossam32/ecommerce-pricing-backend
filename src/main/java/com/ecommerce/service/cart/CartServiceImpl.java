package com.ecommerce.service.cart;

import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

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

        return toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartResponse> getCart(User buyer) {
        return cartItemRepository.findByBuyer(buyer)
                .stream()
                .map(this::toResponse)
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

    private CartResponse toResponse(CartItem item) {
        Product p = item.getProduct();
        return CartResponse.builder()
                .cartItemId(item.getId())
                .productId(p.getId())
                .productName(p.getName())
                .brand(p.getBrand())
                .category(p.getCategory())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .addedAt(item.getAddedAt())
                .build();
    }
}
