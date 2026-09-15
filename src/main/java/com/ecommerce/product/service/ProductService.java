package com.ecommerce.service.product;

import com.ecommerce.dto.request.AcceptPriceRequest;
import com.ecommerce.dto.request.DisputePriceRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.AcceptPriceResponse;
import com.ecommerce.dto.response.DisputeResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.dto.response.SellerDashboardResponse;
import com.ecommerce.entity.User;
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