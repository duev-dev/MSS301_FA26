package com.mss301.brand.dto;

import lombok.Builder;
import lombok.Data;

// [TODO-5.5]
@Data
@Builder
public class BrandResponse {

    private Integer brandId;
    private String brandName;
    private String countryOfOrigin;
    private long watchCount;
}
