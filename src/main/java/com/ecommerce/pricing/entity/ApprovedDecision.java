package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "approved_decisions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApprovedDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String category;

    @Column(precision = 10, scale = 2)
    private BigDecimal approvedMin;

    @Column(precision = 10, scale = 2)
    private BigDecimal approvedMax;

    @CreationTimestamp
    private LocalDateTime createdAt;
}