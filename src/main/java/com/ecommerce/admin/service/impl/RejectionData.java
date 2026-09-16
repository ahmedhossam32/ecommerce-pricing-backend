package com.ecommerce.admin.service.impl;

import java.util.Map;

record RejectionData(
        String sellerEmail,
        String sellerName,
        String productName,
        String rejectionReason,
        double minRange,
        double maxRange,
        Map<String, String> response
) {}
