package com.ecommerce.service.admin;

import com.ecommerce.dto.request.ApproveRequest;
import com.ecommerce.dto.request.OverrideRequest;
import com.ecommerce.dto.request.RejectRequest;
import com.ecommerce.dto.response.AdminProductResponse;
import com.ecommerce.dto.response.AdminRequestResponse;
import com.ecommerce.dto.response.AdminStatsResponse;

import java.util.List;
import java.util.Map;

public interface AdminService {
    List<AdminRequestResponse> getPendingRequests();
    AdminRequestResponse getRequestById(Long requestId);
    List<AdminProductResponse> getAllProducts(String status);
    Map<String, String> approveRequest(Long requestId, ApproveRequest request);
    Map<String, String> rejectRequest(Long requestId, RejectRequest request);
    Map<String, String> overridePrice(Long productId, OverrideRequest request);
    AdminStatsResponse getStats();
    Map<String, String> deleteProduct(Long productId);
}