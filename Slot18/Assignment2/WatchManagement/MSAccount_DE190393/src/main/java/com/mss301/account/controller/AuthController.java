package com.mss301.account.controller;

import com.mss301.account.dto.ApiResponse;
import com.mss301.account.dto.LoginRequest;
import com.mss301.account.dto.LoginResponse;
import com.mss301.account.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// [TODO-3.4: /api/auth/login, /refresh, /logout]
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, Refresh Token, Logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(body.get("refreshToken")), "Token refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (client should discard tokens)")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
