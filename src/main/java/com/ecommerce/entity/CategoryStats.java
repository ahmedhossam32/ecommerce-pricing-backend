package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "category_stats")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CategoryStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String category;

    private Double avgPrice;
    private Double avgReview;
    private Integer medianSalesCount;
    private String mostCommonPaymentType;
    private Integer defaultMaxInstallments;
}