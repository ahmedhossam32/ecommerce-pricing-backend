package com.ecommerce.controller;

import com.ecommerce.dto.request.AcceptPriceRequest;
import com.ecommerce.dto.request.DisputePriceRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import io.swagger.v3.oas.annotations.Operation;
import com.ecommerce.dto.response.AcceptPriceResponse;
import com.ecommerce.dto.response.DisputeResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.PricingSuggestionResponse;
import com.ecommerce.dto.response.SellerDashboardResponse;
import com.ecommerce.entity.User;
import com.ecommerce.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<PricingSuggestionResponse> listProduct(
            @Valid @RequestBody ProductListingRequest request,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.listProduct(request, seller));
    }

    @PostMapping("/products/{id}/accept")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AcceptPriceResponse> acceptPrice(
            @PathVariable Long id,
            @RequestBody(required = false) AcceptPriceRequest request,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.acceptPrice(id, request, seller));
    }

    @PostMapping("/products/{id}/dispute")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<DisputeResponse> disputePrice(
            @PathVariable Long id,
            @Valid @RequestBody DisputePriceRequest request,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.disputePrice(id, request, seller));
    }

    @GetMapping("/seller/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<ProductResponse>> getSellerProducts(
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.getSellerProducts(seller));
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.getProductById(id, seller));
    }

    @GetMapping("/seller/dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerDashboardResponse> getDashboard(
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.getDashboard(seller));
    }

    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<String>> uploadProductImages(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal User seller) {

        if (files == null || files.isEmpty() || files.size() > 5) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(productService.uploadProductImages(id, files, seller));
    }

    @DeleteMapping("/seller/products/{id}")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "Delete one of the seller's own products")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(productService.deleteProduct(id, seller));
    }
}