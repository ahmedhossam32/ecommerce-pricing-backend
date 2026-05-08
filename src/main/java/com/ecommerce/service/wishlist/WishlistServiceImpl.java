package com.ecommerce.service.wishlist;

import com.ecommerce.dto.response.SavedProductResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.SavedProduct;
import com.ecommerce.entity.User;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.SavedProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final SavedProductRepository savedProductRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public SavedProductResponse saveProduct(Long productId, User buyer) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != ProductStatus.LIVE) {
            throw new IllegalStateException("Product is not available");
        }

        savedProductRepository.findByBuyerAndProduct(buyer, product).ifPresent(s -> {
            throw new IllegalStateException("Product already saved");
        });

        SavedProduct saved = SavedProduct.builder()
                .buyer(buyer)
                .product(product)
                .build();
        savedProductRepository.save(saved);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedProductResponse> getSaved(User buyer) {
        return savedProductRepository.findByBuyer(buyer)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void unsaveProduct(Long productId, User buyer) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        savedProductRepository.deleteByBuyerAndProduct(buyer, product);
    }

    private SavedProductResponse toResponse(SavedProduct saved) {
        Product p = saved.getProduct();
        return SavedProductResponse.builder()
                .savedId(saved.getId())
                .productId(p.getId())
                .productName(p.getName())
                .brand(p.getBrand())
                .category(p.getCategory())
                .price(p.getPrice() != null ? p.getPrice().doubleValue() : null)
                .sellerName(p.getSeller().getName())
                .savedAt(saved.getSavedAt())
                .build();
    }
}
