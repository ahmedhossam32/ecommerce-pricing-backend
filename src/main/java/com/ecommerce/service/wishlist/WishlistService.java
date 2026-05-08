package com.ecommerce.service.wishlist;

import com.ecommerce.dto.response.SavedProductResponse;
import com.ecommerce.entity.User;

import java.util.List;

public interface WishlistService {
    SavedProductResponse saveProduct(Long productId, User buyer);
    List<SavedProductResponse> getSaved(User buyer);
    void unsaveProduct(Long productId, User buyer);
}
