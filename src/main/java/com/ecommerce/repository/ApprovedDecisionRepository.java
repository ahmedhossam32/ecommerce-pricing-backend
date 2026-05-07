package com.ecommerce.repository;

import com.ecommerce.entity.ApprovedDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovedDecisionRepository extends JpaRepository<ApprovedDecision, Long> {
}