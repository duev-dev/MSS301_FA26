package com.fudn.customerservice.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,      // giay
        String role,         // ADMIN | CUSTOMER
        Long userId,         // Admin = 0
        String email,
        String fullName) {
}
