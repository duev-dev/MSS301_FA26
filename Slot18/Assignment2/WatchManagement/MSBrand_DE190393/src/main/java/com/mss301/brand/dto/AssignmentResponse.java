package com.mss301.brand.dto;

/**
 * Kết quả gán / gỡ watch. previousBrandId cho orchestrator biết brand cũ (để gán lại khi bù trừ).
 * status: ASSIGNED | UNASSIGNED | NOTHING_TO_DO
 */
// [TODO-5.4]
public record AssignmentResponse(Integer watchId, Integer brandId, Integer previousBrandId, String status) {
}
