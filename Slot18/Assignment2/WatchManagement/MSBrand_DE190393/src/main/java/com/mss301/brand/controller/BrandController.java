package com.mss301.brand.controller;

import com.mss301.brand.dto.ApiResponse;
import com.mss301.brand.dto.BrandResponse;
import com.mss301.brand.dto.BrandWatchesResponse;
import com.mss301.brand.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API public - đi qua API Gateway (cần JWT). */
// [TODO-5.5: API public /api/brands, /{id}, /{id}/watches]
@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Tag(name = "Brand", description = "Watch Brand management")
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @Operation(summary = "Get all brands (kèm watchCount)")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(brandService.findAll(), "Success"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get brand by ID")
    public ResponseEntity<ApiResponse<BrandResponse>> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(brandService.findById(id), "Success"));
    }

    @GetMapping("/{id}/watches")
    @Operation(summary = "Danh sách watchId đang gắn với brand (dùng để kiểm chứng Saga)")
    public ResponseEntity<ApiResponse<BrandWatchesResponse>> watches(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(brandService.watchesOf(id), "Success"));
    }
}
