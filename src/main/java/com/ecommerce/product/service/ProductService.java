package com.ecommerce.product.service;

import com.ecommerce.pricing.dto.response.PricingSuggestionResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.product.dto.request.AcceptPriceRequest;
import com.ecommerce.product.dto.response.AcceptPriceResponse;
import com.ecommerce.product.dto.request.DisputePriceRequest;
import com.ecommerce.product.dto.response.DisputeResponse;
import com.ecommerce.product.dto.request.ProductListingRequest;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.dto.response.SellerDashboardResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import java.util.List;

public interface ProductService {
    PricingSuggestionResponse listProduct(ProductListingRequest request, User seller);
    AcceptPriceResponse acceptPrice(Long productId, AcceptPriceRequest request, User seller);
    DisputeResponse disputePrice(Long productId, DisputePriceRequest request, User seller);
    List<ProductResponse> getSellerProducts(User seller);
    ProductResponse getProductById(Long productId, User seller);
    SellerDashboardResponse getDashboard(User seller);
    List<String> uploadProductImages(Long productId, List<MultipartFile> files, User seller);
    Map<String, String> deleteProduct(Long productId, User seller);
}