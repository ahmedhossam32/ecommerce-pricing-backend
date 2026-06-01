package com.ecommerce.service.pricing;

public interface RoutingService {
    String determineStatus(double suggestedPrice, String brand, String category, String confidence, String condition);
    void cacheApprovedRange(String brand, String category, double approvedPrice, String condition);
}