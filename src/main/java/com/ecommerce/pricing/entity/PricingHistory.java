package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_history")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PricingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal newPrice;

    @CreationTimestamp
    private LocalDateTime changedAt;
}