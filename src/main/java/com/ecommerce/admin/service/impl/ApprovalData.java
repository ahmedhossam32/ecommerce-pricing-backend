package com.ecommerce.admin.service.impl;

import java.util.Map;

record ApprovalData(
        String sellerEmail,
        String sellerName,
        String productName,
        double approvedPrice,
        String adminNote,
        String brand,
        String category,
        String condition,
        Map<String, String> response
) {}
