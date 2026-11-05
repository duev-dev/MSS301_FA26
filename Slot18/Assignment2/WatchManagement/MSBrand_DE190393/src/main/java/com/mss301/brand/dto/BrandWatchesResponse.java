package com.mss301.brand.dto;

import java.util.List;

// [TODO-5.5: dùng để kiểm chứng Saga]
public record BrandWatchesResponse(Integer brandId, String brandName, int watchCount, List<Integer> watchIds) {
}
