package com.ecommerce.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String fromEmail;

    @Override
    public void sendApprovalEmail(String toEmail, String sellerName, String productName,
                                  double approvedPrice, String adminNote) {
        String body = String.format("""
                Hi %s,

                Great news! Your product "%s" has been approved and is now live at $%.2f.

                Admin note: %s

                Your product is now visible to buyers.

                Best regards,
                The Marketplace Team
                """, sellerName, productName, approvedPrice,
                adminNote != null ? adminNote : "No additional notes.");

        send(toEmail, "Your product has been approved!", body);
    }

    @Override
    public void sendRejectionEmail(String toEmail, String sellerName, String productName,
                                   String reason, double minRange, double maxRange) {
        String body = String.format("""
                Hi %s,

                Unfortunately, your product "%s" was not approved.

                Reason: %s

                You can relist your product at a price between $%.2f and $%.2f.

                Best regards,
                The Marketplace Team
                """, sellerName, productName, reason, minRange, maxRange);

        send(toEmail, "Your product listing was not approved", body);
    }

    @Override
    public void sendOverrideEmail(String toEmail, String sellerName, String productName,
                                   double oldPrice, double newPrice, String adminNote) {
        String body = String.format("""
                Hi %s,

                Your product "%s" price has been updated by the admin.

                Previous price: $%.2f
                New price: $%.2f

                Admin note: %s

                Best regards,
                The Marketplace Team
                """, sellerName, productName, oldPrice, newPrice,
                adminNote != null ? adminNote : "No additional notes.");

        send(toEmail, "Your product price has been updated", body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}