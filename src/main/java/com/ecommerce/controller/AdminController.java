package com.ecommerce.controller;

import com.ecommerce.dto.request.ApproveRequest;
import com.ecommerce.dto.request.OverrideRequest;
import com.ecommerce.dto.request.RejectRequest;
import com.ecommerce.dto.response.AdminRequestResponse;
import com.ecommerce.dto.response.AdminStatsResponse;
import com.ecommerce.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminRequestResponse>> getPendingRequests() {
        return ResponseEntity.ok(adminService.getPendingRequests());
    }

    @PostMapping("/approve/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> approveRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ApproveRequest request) {
        return ResponseEntity.ok(adminService.approveRequest(requestId, request));
    }

    @PostMapping("/reject/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejectRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(adminService.rejectRequest(requestId, request));
    }

    @PostMapping("/override/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> overridePrice(
            @PathVariable Long productId,
            @Valid @RequestBody OverrideRequest request) {
        return ResponseEntity.ok(adminService.overridePrice(productId, request));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}