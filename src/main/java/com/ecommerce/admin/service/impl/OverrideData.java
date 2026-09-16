package com.ecommerce.admin.service.impl;

import java.util.Map;

record OverrideData(
        String sellerEmail,
        String sellerName,
        String productName,
        double oldPrice,
        double newPrice,
        String adminNote,
        String brand,
        String category,
        String condition,
        Map<String, String> response
) {}
