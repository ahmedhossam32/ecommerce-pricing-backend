package com.ecommerce.service.pricing;

import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.request.ProductListingRequest;
import com.ecommerce.dto.response.LLMResponse;
import com.ecommerce.entity.CategoryStats;
import com.ecommerce.entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class FeatureBuilderServiceImpl implements FeatureBuilderService {

    @Override
    public MLRequest buildFeatures(ProductListingRequest request,
                                   LLMResponse llm,
                                   User seller,
                                   CategoryStats stats) {

        double weight = resolveWeight(request.getWeight(), llm.getEstimatedWeight());


        double estimatedVolume = weight * 5.0;
        double side = Math.cbrt(estimatedVolume);

        int month = LocalDate.now().getMonthValue();

        return MLRequest.builder()
                // Required physical fields
                .freightValue(request.getFreightValue())
                .productPhotosQty(request.getPhotosQty())
                .productWeightG(weight)
                .productLengthCm(side)
                .productHeightCm(side)
                .productWidthCm(side)
                .productDescriptionLenght(request.getDescription().length())
                .productNameLenght(request.getDescription().length())

                // Category
                .productCategoryNameEnglish(request.getCategory())

                // Temporal
                .purchaseMonth(month)

                // Seller stats with fallbacks
                .sellerAvgPrice(d(stats != null ? stats.getAvgPrice() : null, 100.0))
                .sellerAvgReview(d(stats != null ? stats.getAvgReview() : null, 4.0))
                .sellerSalesCount(i(stats != null ? stats.getMedianSalesCount() : null, 50))
                .sellerProductDiversity(1)

                // Product stats with fallbacks
                .productAvgReview(d(stats != null ? stats.getAvgReview() : null, 4.0))
                .productSalesCount(i(stats != null ? stats.getMedianSalesCount() : null, 50))

                // Category stats
                .categoryAvgPrice(d(stats != null ? stats.getAvgPrice() : null, 100.0))
                .maxInstallments(i(stats != null ? stats.getDefaultMaxInstallments() : null, 12))
                .paymentTypeMode(stats != null && stats.getMostCommonPaymentType() != null
                        ? stats.getMostCommonPaymentType() : "credit_card")

                // Geography defaults
                .customerState("SP")
                .sellerState("SP")
                .build();
    }

    private double resolveWeight(Double sellerWeight, Double llmWeight) {
        if (sellerWeight != null && sellerWeight > 0) return sellerWeight;
        if (llmWeight != null && llmWeight > 0) return llmWeight;
        return 500.0;
    }

    private String sizeCategory(double weightGrams) {
        if (weightGrams < 500) return "small";
        if (weightGrams < 2000) return "medium";
        if (weightGrams < 10000) return "large";
        return "very_large";
    }

    private double d(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private int i(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}