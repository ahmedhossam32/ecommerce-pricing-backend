package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerDashboardResponse {
    private long totalProducts;
    private long liveProducts;
    private long pendingReview;
    private long rejected;
    private long draft;
    private Double totalRevenue;
    private long totalOrders;
}
