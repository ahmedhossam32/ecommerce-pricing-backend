package com.ecommerce.service.pricing;

import com.ecommerce.entity.CategoryBounds;
import com.ecommerce.repository.CategoryBoundsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingServiceImpl.class);

    private final StringRedisTemplate redisTemplate;
    private final CategoryBoundsRepository categoryBoundsRepository;

    private static final String PREFIX = "pricing:";

    @Override
    @Transactional(readOnly = true)
    public String determineStatus(double price, String brand, String category, String confidence) {



        // Layer 1 — Redis cache check
        String cacheKey = cacheKey(brand, category, price);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache hit for key: {}", cacheKey);
            String[] parts = cached.split(":");
            if (parts.length == 2) {
                try {
                    double min = Double.parseDouble(parts[0]);
                    double max = Double.parseDouble(parts[1]);
                    if (price >= min && price <= max) return "PENDING_SELLER";
                } catch (NumberFormatException ignored) {}
            }
        }

        // Layer 2 — Category bounds check
        Optional<CategoryBounds> bounds = categoryBoundsRepository
                .findByCategory(category.toLowerCase());
        log.debug("=== BOUNDS CHECK: category={} found={} ===", category.toLowerCase(), bounds.isPresent());
        if (bounds.isPresent()) {
            log.debug("=== BOUNDS: min={} max={} price={} ===", bounds.get().getMinPrice(), bounds.get().getMaxPrice(), price);
            double minBound = bounds.get().getMinPrice().doubleValue();
            double maxBound = bounds.get().getMaxPrice().doubleValue();
            if (price < minBound || price > maxBound) return "PENDING_ADMIN";
        }

        // Layer 3 — Confidence gate
        switch (confidence.toUpperCase()) {
            case "HIGH":
            case "MEDIUM":
                return "PENDING_SELLER";
            default:
                return "PENDING_ADMIN";
        }
    }
    @Override
    public void cacheApprovedRange(String brand, String category, double approvedPrice) {
        String key = cacheKey(brand, category, approvedPrice);
        double min = Math.round(approvedPrice * 0.90 * 100.0) / 100.0;
        double max = Math.round(approvedPrice * 1.10 * 100.0) / 100.0;
        redisTemplate.opsForValue().set(key, min + ":" + max, 30, TimeUnit.DAYS);
    }

    private String cacheKey(String brand, String category, double price) {
        return PREFIX + brand.toLowerCase() + ":" + category.toLowerCase() + ":" + priceBucket(price);
    }

    private String priceBucket(double price) {
        if (price < 200)  return "budget";
        if (price < 500)  return "mid";
        if (price < 1000) return "premium";
        return "luxury";
    }
}