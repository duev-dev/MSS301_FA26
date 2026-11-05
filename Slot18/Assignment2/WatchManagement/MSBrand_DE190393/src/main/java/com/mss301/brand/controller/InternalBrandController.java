package com.mss301.brand.controller;

import com.mss301.brand.dto.ApiResponse;
import com.mss301.brand.dto.AssignmentResponse;
import com.mss301.brand.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API NỘI BỘ - chỉ MSWatch (Saga Orchestrator) gọi trực tiếp tới cổng 8082.
 * API Gateway KHÔNG định tuyến /internal/** nên client bên ngoài không gọi được.
 * Mỗi endpoint = 1 local transaction (hoặc 1 compensating transaction) của Saga, đều IDEMPOTENT.
 */
// [TODO-5.4: API nội bộ /internal/brands/** cho Saga]
@RestController
@RequestMapping("/internal/brands")
@RequiredArgsConstructor
@Tag(name = "Internal (Saga)", description = "Called only by MSWatch saga orchestrator")
public class InternalBrandController {

    private final BrandService brandService;

    @PutMapping("/{brandId}/watches/{watchId}")
    @Operation(summary = "Gán watch vào brand (upsert, idempotent)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assign(@PathVariable Integer brandId,
                                                                  @PathVariable Integer watchId) {
        return ResponseEntity.ok(ApiResponse.success(brandService.assignWatch(brandId, watchId), "Assigned"));
    }

    @DeleteMapping("/watches/{watchId}")
    @Operation(summary = "Gỡ watch khỏi brand (idempotent)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> unassign(@PathVariable Integer watchId) {
        return ResponseEntity.ok(ApiResponse.success(brandService.unassignWatch(watchId), "Unassigned"));
    }
}
