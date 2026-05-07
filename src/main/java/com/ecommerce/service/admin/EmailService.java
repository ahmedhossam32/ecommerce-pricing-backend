package com.ecommerce.service.admin;

public interface EmailService {
    void sendApprovalEmail(String toEmail, String sellerName, String productName, double approvedPrice, String adminNote);
    void sendRejectionEmail(String toEmail, String sellerName, String productName, String reason, double minRange, double maxRange);
}