package com.ecommerce.admin.mapper;

import com.ecommerce.admin.dto.response.AdminRequestResponse;
import com.ecommerce.common.enums.Role;
import com.ecommerce.pricing.entity.CategoryBounds;
import com.ecommerce.pricing.entity.PricingRequest;
import com.ecommerce.pricing.repository.CategoryBoundsRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pure unit test — no Spring context needed since toAdminResponse(pr, bounds) takes the
 * already-fetched CategoryBounds as a parameter instead of looking it up itself.
 */
@DisplayName("AdminMapper — routingReason derivation")
class AdminMapperTest {

    private final CategoryBoundsRepository categoryBoundsRepository = Mockito.mock(CategoryBoundsRepository.class);
    private final AdminMapper mapper = new AdminMapper(categoryBoundsRepository);

    private User seller() {
        return User.builder()
                .id(1L)
                .name("Seller Sam")
                .email("seller@test.com")
                .password("hashed")
                .role(Role.SELLER)
                .build();
    }

    private Product product(String category) {
        return Product.builder()
                .id(10L)
                .seller(seller())
                .name("Test Widget")
                .category(category)
                .build();
    }

    private PricingRequest request(Product product, double suggestedPrice) {
        return PricingRequest.builder()
                .id(100L)
                .product(product)
                .suggestedPrice(BigDecimal.valueOf(suggestedPrice))
                .build();
    }

    @Test
    @DisplayName("bounds present, suggested price outside [min,max] -> OUTSIDE_BOUNDS")
    void routingReason_outsideBounds_whenPriceOutsideRange() {
        Product product = product("electronics");
        PricingRequest pr = request(product, 5000.0);
        CategoryBounds bounds = CategoryBounds.builder()
                .category("electronics")
                .minPrice(BigDecimal.valueOf(50))
                .maxPrice(BigDecimal.valueOf(2000))
                .build();

        AdminRequestResponse response = mapper.toAdminResponse(pr, bounds);

        assertThat(response.getRoutingReason()).isEqualTo("OUTSIDE_BOUNDS");
        verifyNoInteractions(categoryBoundsRepository);
    }

    @Test
    @DisplayName("bounds present, suggested price inside [min,max] -> LOW_CONFIDENCE")
    void routingReason_lowConfidence_whenPriceInsideRange() {
        Product product = product("electronics");
        PricingRequest pr = request(product, 500.0);
        CategoryBounds bounds = CategoryBounds.builder()
                .category("electronics")
                .minPrice(BigDecimal.valueOf(50))
                .maxPrice(BigDecimal.valueOf(2000))
                .build();

        AdminRequestResponse response = mapper.toAdminResponse(pr, bounds);

        assertThat(response.getRoutingReason()).isEqualTo("LOW_CONFIDENCE");
        verifyNoInteractions(categoryBoundsRepository);
    }

    @Test
    @DisplayName("no bounds row for the category -> LOW_CONFIDENCE")
    void routingReason_lowConfidence_whenBoundsNull() {
        Product product = product("unmapped_category");
        PricingRequest pr = request(product, 999.0);

        AdminRequestResponse response = mapper.toAdminResponse(pr, null);

        assertThat(response.getRoutingReason()).isEqualTo("LOW_CONFIDENCE");
        verifyNoInteractions(categoryBoundsRepository);
    }

    @Test
    @DisplayName("single-argument overload looks up bounds via the repository and delegates")
    void singleArgOverload_looksUpBoundsAndDelegates() {
        Product product = product("electronics");
        PricingRequest pr = request(product, 5000.0);
        CategoryBounds bounds = CategoryBounds.builder()
                .category("electronics")
                .minPrice(BigDecimal.valueOf(50))
                .maxPrice(BigDecimal.valueOf(2000))
                .build();
        Mockito.when(categoryBoundsRepository.findByCategory("electronics"))
                .thenReturn(java.util.Optional.of(bounds));

        AdminRequestResponse response = mapper.toAdminResponse(pr);

        assertThat(response.getRoutingReason()).isEqualTo("OUTSIDE_BOUNDS");
        Mockito.verify(categoryBoundsRepository).findByCategory("electronics");
    }
}
