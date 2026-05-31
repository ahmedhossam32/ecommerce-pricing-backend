package com.ecommerce.service.admin;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String fromEmail;

    private static final String LOGO_URL = "https://res.cloudinary.com/dnqp6wte7/image/upload/v1780241076/dynamartlogo_final_v3tfb2.png";

    private String buildHtml(String title, String bodyContent) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td align="center" style="padding:30px 0;">
                        <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;">

                          <!-- Header -->
                          <tr>
                            <td style="background-color:#1C1F2E;padding:24px;text-align:center;">
                              <img src="%s" alt="DynaMart" width="160" style="display:block;margin:0 auto;max-width:100%%;" />
                            </td>
                          </tr>

                          <!-- Title bar -->
                          <tr>
                            <td style="background-color:#C9A96E;padding:12px 24px;text-align:center;">
                              <h2 style="margin:0;color:#1C1F2E;font-size:16px;font-weight:bold;">%s</h2>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:32px 40px;color:#333333;font-size:14px;line-height:1.8;">
                              %s
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#1C1F2E;padding:16px;text-align:center;">
                              <p style="margin:0;color:#9CA3AF;font-size:12px;">© 2026 DynaMart. All rights reserved.</p>
                              <p style="margin:4px 0 0;color:#9CA3AF;font-size:12px;">AI-Powered Dynamic Pricing Marketplace</p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(LOGO_URL, title, bodyContent);
    }

    @Override
    public void sendApprovalEmail(String toEmail, String sellerName, String productName,
                                  double approvedPrice, String adminNote) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>Great news! Your product has been approved and is now live.</p>
                <table width="100%%" style="background:#f9f9f9;border-radius:6px;padding:16px;margin:16px 0;">
                  <tr><td style="color:#666;width:40%%;">Product</td><td><strong>%s</strong></td></tr>
                  <tr><td style="color:#666;">Approved Price</td><td><strong style="color:#1C1F2E;">$%.2f</strong></td></tr>
                  <tr><td style="color:#666;">Admin Note</td><td>%s</td></tr>
                </table>
                <p>Your product is now visible to buyers on DynaMart.</p>
                <p style="color:#888;font-size:12px;">Best regards,<br/>The DynaMart Team</p>
                """.formatted(sellerName, productName, approvedPrice,
                adminNote != null ? adminNote : "No additional notes.");

        send(toEmail, "Your product has been approved! ✅", buildHtml("Product Approved", body));
    }

    @Override
    public void sendRejectionEmail(String toEmail, String sellerName, String productName,
                                   String reason, double minRange, double maxRange) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>Unfortunately, your product listing was not approved at this time.</p>
                <table width="100%%" style="background:#f9f9f9;border-radius:6px;padding:16px;margin:16px 0;">
                  <tr><td style="color:#666;width:40%%;">Product</td><td><strong>%s</strong></td></tr>
                  <tr><td style="color:#666;">Reason</td><td>%s</td></tr>
                  <tr><td style="color:#666;">Acceptable Range</td><td><strong>$%.2f — $%.2f</strong></td></tr>
                </table>
                <p>You can relist your product within the acceptable price range above.</p>
                <p style="color:#888;font-size:12px;">Best regards,<br/>The DynaMart Team</p>
                """.formatted(sellerName, productName, reason, minRange, maxRange);

        send(toEmail, "Your product listing was not approved", buildHtml("Listing Not Approved", body));
    }

    @Override
    public void sendOverrideEmail(String toEmail, String sellerName, String productName,
                                   double oldPrice, double newPrice, String adminNote) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>Your product price has been updated by the admin.</p>
                <table width="100%%" style="background:#f9f9f9;border-radius:6px;padding:16px;margin:16px 0;">
                  <tr><td style="color:#666;width:40%%;">Product</td><td><strong>%s</strong></td></tr>
                  <tr><td style="color:#666;">Previous Price</td><td><s>$%.2f</s></td></tr>
                  <tr><td style="color:#666;">New Price</td><td><strong style="color:#C9A96E;">$%.2f</strong></td></tr>
                  <tr><td style="color:#666;">Admin Note</td><td>%s</td></tr>
                </table>
                <p style="color:#888;font-size:12px;">Best regards,<br/>The DynaMart Team</p>
                """.formatted(sellerName, productName, oldPrice, newPrice,
                adminNote != null ? adminNote : "No additional notes.");

        send(toEmail, "Your product price has been updated", buildHtml("Price Updated", body));
    }

    @Override
    public void sendOrderConfirmationEmail(String toEmail, String buyerName,
                                            String productName, double price) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>Your order has been placed successfully!</p>
                <table width="100%%" style="background:#f9f9f9;border-radius:6px;padding:16px;margin:16px 0;">
                  <tr><td style="color:#666;width:40%%;">Product</td><td><strong>%s</strong></td></tr>
                  <tr><td style="color:#666;">Price Paid</td><td><strong style="color:#1C1F2E;">$%.2f</strong></td></tr>
                </table>
                <p>Thank you for shopping with DynaMart!</p>
                <p style="color:#888;font-size:12px;">Best regards,<br/>The DynaMart Team</p>
                """.formatted(buyerName, productName, price);

        send(toEmail, "Order confirmed — " + productName, buildHtml("Order Confirmed! 🎉", body));
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "DynaMart");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("✅ Email sent successfully to {} | Subject: {}", to, subject);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {} | Subject: {} | Error: {}", to, subject, e.getMessage());
        }
    }
}
