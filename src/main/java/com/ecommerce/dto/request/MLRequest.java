package com.ecommerce.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLRequest {

    @JsonProperty("freight_value")
    private Double freightValue;

    @JsonProperty("product_photos_qty")
    private Integer productPhotosQty;

    @JsonProperty("product_weight_g")
    private Double productWeightG;

    @JsonProperty("product_length_cm")
    private Double productLengthCm;

    @JsonProperty("product_height_cm")
    private Double productHeightCm;

    @JsonProperty("product_width_cm")
    private Double productWidthCm;

    @JsonProperty("product_description_lenght")
    private Integer productDescriptionLenght;

    @JsonProperty("product_name_lenght")
    private Integer productNameLenght;

    @JsonProperty("product_category_name_english")
    private String productCategoryNameEnglish;

    @JsonProperty("purchase_month")
    private Integer purchaseMonth;

    @JsonProperty("product_sales_count")
    private Integer productSalesCount;

    @JsonProperty("seller_sales_count")
    private Integer sellerSalesCount;

    @JsonProperty("seller_product_diversity")
    private Integer sellerProductDiversity;

    @JsonProperty("product_avg_review")
    private Double productAvgReview;

    @JsonProperty("seller_avg_review")
    private Double sellerAvgReview;

    @JsonProperty("max_installments")
    private Integer maxInstallments;

    @JsonProperty("payment_type_mode")
    private String paymentTypeMode;

    @JsonProperty("customer_state")
    private String customerState;

    @JsonProperty("seller_state")
    private String sellerState;

    @JsonProperty("category_avg_price")
    private Double categoryAvgPrice;

    @JsonProperty("seller_avg_price")
    private Double sellerAvgPrice;
}