package com.ecommerce.wishlist.service;

import com.ecommerce.user.entity.User;
import com.ecommerce.wishlist.dto.response.SavedProductResponse;

import java.util.List;

public interface WishlistService {
    SavedProductResponse saveProduct(Long productId, User buyer);
    List<SavedProductResponse> getSaved(User buyer);
    void unsaveProduct(Long productId, User buyer);
    void clearWishlist(User buyer);
}
