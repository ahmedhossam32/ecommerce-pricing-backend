package com.ecommerce.pricing.repository;
import com.ecommerce.pricing.entity.ApprovedDecision;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovedDecisionRepository extends JpaRepository<ApprovedDecision, Long> {
}