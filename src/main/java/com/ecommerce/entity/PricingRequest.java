package com.ecommerce.entity;

import com.ecommerce.enums.PricingRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PricingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(precision = 10, scale = 2)
    private BigDecimal suggestedPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal sellerPrice;

    @Column(columnDefinition = "TEXT")
    private String sellerReasoning;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PricingRequestStatus status = PricingRequestStatus.PENDING;

    // LLM-derived fields
    private String brand;
    private String llmConfidence;

    @Column(columnDefinition = "TEXT")
    private String condition;

    @Column(columnDefinition = "TEXT")
    private String conditionNotes;

    @Column(name = "condition_grade")
    private String conditionGrade;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(precision = 10, scale = 2)
    private BigDecimal mlBaselinePrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal marketPriceMin;

    @Column(precision = 10, scale = 2)
    private BigDecimal marketPriceMax;

    @CreationTimestamp
    private LocalDateTime createdAt;
}