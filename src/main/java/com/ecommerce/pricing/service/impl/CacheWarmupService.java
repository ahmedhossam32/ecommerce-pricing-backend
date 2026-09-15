package com.ecommerce.pricing.service.impl;
import com.ecommerce.pricing.entity.ApprovedDecision;
import com.ecommerce.pricing.repository.ApprovedDecisionRepository;
import com.ecommerce.pricing.service.RoutingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupService {

    private final ApprovedDecisionRepository approvedDecisionRepository;
    private final RoutingService routingService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmUpCache() {
        try {
            List<ApprovedDecision> decisions = approvedDecisionRepository.findAll();
            for (ApprovedDecision decision : decisions) {
                double midpoint = (decision.getApprovedMin().doubleValue()
                        + decision.getApprovedMax().doubleValue()) / 2.0;
                routingService.cacheApprovedRange(
                        decision.getBrand(),
                        decision.getCategory(),
                        midpoint,
                        null);
            }
            log.info("Redis cache warmed up with {} approved decisions", decisions.size());
        } catch (Exception e) {
            log.warn("Redis cache warmup failed — app will start without warm cache: {}", e.getMessage());
        }
    }
}
