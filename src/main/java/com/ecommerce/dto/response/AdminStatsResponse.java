package com.ecommerce.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalProducts;
    private long liveProducts;
    private long pendingReview;
    private long rejectedProducts;
    private long totalSellers;
    private long totalApprovedDecisions;
}