package com.ecommerce.pricing.controller;

import com.ecommerce.product.dto.request.ProductListingRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.pricing.service.PricingService;
import com.ecommerce.pricing.dto.response.PricingSuggestionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/suggest")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<PricingSuggestionResponse> suggest(
            @Valid @RequestBody ProductListingRequest request,
            @AuthenticationPrincipal User seller) {

        return ResponseEntity.ok(pricingService.getSuggestion(request, seller));
    }
}