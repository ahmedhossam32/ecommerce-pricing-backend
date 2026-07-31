package com.ecommerce.service.admin;

import com.ecommerce.dto.request.ApproveRequest;
import com.ecommerce.dto.request.OverrideRequest;
import com.ecommerce.entity.PricingRequest;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.PricingRequestStatus;
import com.ecommerce.enums.ProductStatus;
import com.ecommerce.enums.Role;
import com.ecommerce.repository.ApprovedDecisionRepository;
import com.ecommerce.repository.PricingRequestRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.pricing.RoutingService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral proof for the AdminServiceImpl transaction-boundary fix.
 *
 * Real embedded H2 + real ProductRepository/PricingRequestRepository so rollback is
 * observed against actually-persisted rows, not mocks. RoutingService, EmailService and
 * ApprovedDecisionRepository are mocked — they're the side-effect boundaries the fix
 * relocated relative to the transaction, not what's under test.
 *
 * Class-level @Transactional(NOT_SUPPORTED) disables @DataJpaTest's default
 * wrap-every-test-and-roll-back behavior. Without this, AdminServiceImpl's own
 * @Transactional would just join the surrounding test transaction, and a failed
 * approval would look identical (from the test's point of view) whether the bug
 * were present or fixed — the whole point is to let the service's own transaction
 * be the real, outermost commit/rollback boundary.
 */
@DataJpaTest
@Import(AdminServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("AdminServiceImpl — transaction boundary fix (behavioral verification)")
class AdminServiceImplTransactionTest {

    @Autowired private AdminServiceImpl adminService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @SpyBean  private PricingRequestRepository pricingRequestRepository;
    @MockBean private ApprovedDecisionRepository approvedDecisionRepository;
    @MockBean private RoutingService routingService;
    @MockBean private EmailService emailService;

    private User seller;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.builder()
                .name("Test Seller")
                .email("seller-" + System.nanoTime() + "@test.com")
                .password("hashed")
                .role(Role.SELLER)
                .build());
    }

    private Product persistProduct(ProductStatus status, BigDecimal price) {
        return productRepository.save(Product.builder()
                .seller(seller)
                .name("Test Widget")
                .category("electronics")
                .status(status)
                .price(price)
                .build());
    }

    private PricingRequest persistPricingRequest(Product product, double suggestedPrice) {
        return pricingRequestRepository.save(PricingRequest.builder()
                .product(product)
                .suggestedPrice(BigDecimal.valueOf(suggestedPrice))
                .status(PricingRequestStatus.PENDING)
                .brand("Sony")
                .condition("NEW")
                .build());
    }

    // ── Test 1a — atomicity on failure: approveRequest ──────────────────────
    @Test
    @DisplayName("Test 1a: approveRequest rolls back Product + PricingRequest when ApprovedDecision save fails")
    void approveRequest_failureRollsBackAllWrites() {
        Product product = persistProduct(ProductStatus.PENDING_REVIEW, null);
        PricingRequest pr = persistPricingRequest(product, 500.0);

        when(approvedDecisionRepository.save(any()))
                .thenThrow(new RuntimeException("boom-simulated-failure"));

        ApproveRequest request = new ApproveRequest();
        request.setApprovedPrice(500.0);
        request.setAdminNote("looks good");

        assertThatThrownBy(() -> adminService.approveRequest(pr.getId(), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom-simulated-failure");

        entityManager.clear(); // force a genuinely fresh read, bypass the L1 cache

        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        PricingRequest reloadedPr = pricingRequestRepository.findById(pr.getId()).orElseThrow();

        assertThat(reloadedProduct.getStatus())
                .as("Product status must NOT have been committed as LIVE")
                .isEqualTo(ProductStatus.PENDING_REVIEW);
        assertThat(reloadedProduct.getPrice())
                .as("Product price must NOT have been committed")
                .isNull();
        assertThat(reloadedPr.getStatus())
                .as("PricingRequest status must NOT have been committed as APPROVED")
                .isEqualTo(PricingRequestStatus.PENDING);
    }

    // ── Test 1b — atomicity on failure: overridePrice ───────────────────────
    // doOverrideTransaction's only repository call after productRepository.save(product) is now
    // the condition lookup (findTopByProductOrderByCreatedAtDesc) -- the fix moved the Redis
    // write (the original "later write") out of this method entirely. To still prove the same
    // atomicity property (does an in-method failure AFTER the product save roll the save back),
    // this test fails that lookup instead.
    @Test
    @DisplayName("Test 1b: overridePrice rolls back the Product price change when the post-save read fails")
    void overridePrice_failureRollsBackPriceChange() {
        Product product = persistProduct(ProductStatus.LIVE, BigDecimal.valueOf(300.0));

        doThrow(new RuntimeException("boom-simulated-failure"))
                .when(pricingRequestRepository).findTopByProductOrderByCreatedAtDesc(any());

        OverrideRequest request = new OverrideRequest();
        request.setNewPrice(450.0);
        request.setAdminNote("manual override");

        assertThatThrownBy(() -> adminService.overridePrice(product.getId(), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom-simulated-failure");

        entityManager.clear();

        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloadedProduct.getPrice())
                .as("Product price must have rolled back to the pre-override value")
                .isEqualByComparingTo(BigDecimal.valueOf(300.0));
    }

    // ── Test 2 — afterCommit fires on success ───────────────────────────────
    @Test
    @DisplayName("Test 2: on success, afterCommit fires exactly once — cache write and email both invoked")
    void approveRequest_afterCommitFiresOnSuccess() {
        Product product = persistProduct(ProductStatus.PENDING_REVIEW, null);
        PricingRequest pr = persistPricingRequest(product, 500.0);

        ApproveRequest request = new ApproveRequest();
        request.setApprovedPrice(500.0);
        request.setAdminNote("approved");

        adminService.approveRequest(pr.getId(), request);

        verify(routingService, times(1))
                .cacheApprovedRange(anyString(), anyString(), anyDouble(), any());
        verify(emailService, times(1))
                .sendApprovalEmail(anyString(), anyString(), anyString(), anyDouble(), any());
    }

    // ── Test 3 — afterCommit does NOT fire on failure ───────────────────────
    @Test
    @DisplayName("Test 3: on failure, afterCommit never fires — no cache write, no email")
    void approveRequest_afterCommitDoesNotFireOnFailure() {
        Product product = persistProduct(ProductStatus.PENDING_REVIEW, null);
        PricingRequest pr = persistPricingRequest(product, 500.0);

        when(approvedDecisionRepository.save(any()))
                .thenThrow(new RuntimeException("boom-simulated-failure"));

        ApproveRequest request = new ApproveRequest();
        request.setApprovedPrice(500.0);
        request.setAdminNote("approved");

        assertThatThrownBy(() -> adminService.approveRequest(pr.getId(), request))
                .isInstanceOf(RuntimeException.class);

        verify(routingService, never())
                .cacheApprovedRange(any(), any(), anyDouble(), any());
        verify(emailService, never())
                .sendApprovalEmail(any(), any(), any(), anyDouble(), any());
    }

    // ── Test 4 — Redis failure doesn't break the approval ───────────────────
    @Test
    @DisplayName("Test 4: a Redis (cacheApprovedRange) failure does not break or roll back the approval")
    void approveRequest_redisFailureDoesNotBreakApproval() {
        Product product = persistProduct(ProductStatus.PENDING_REVIEW, null);
        PricingRequest pr = persistPricingRequest(product, 500.0);

        doThrow(new RuntimeException("redis-down"))
                .when(routingService).cacheApprovedRange(any(), any(), anyDouble(), any());

        ApproveRequest request = new ApproveRequest();
        request.setApprovedPrice(500.0);
        request.setAdminNote("approved despite redis outage");

        AtomicReference<Map<String, String>> resultRef = new AtomicReference<>();
        assertThatCode(() -> resultRef.set(adminService.approveRequest(pr.getId(), request)))
                .as("approveRequest must not throw when only the cache write fails")
                .doesNotThrowAnyException();

        assertThat(resultRef.get()).containsEntry("status", "APPROVED");

        entityManager.clear();
        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloadedProduct.getStatus())
                .as("Product must still be committed as LIVE despite the Redis failure")
                .isEqualTo(ProductStatus.LIVE);
        assertThat(reloadedProduct.getPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(500.0));

        // Email is a separate side effect from the cache write and must still fire.
        verify(emailService, times(1))
                .sendApprovalEmail(anyString(), anyString(), anyString(), anyDouble(), any());
    }
}
