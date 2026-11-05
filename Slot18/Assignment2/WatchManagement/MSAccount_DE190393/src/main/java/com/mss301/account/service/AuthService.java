package com.mss301.account.service;

import com.mss301.account.dto.LoginRequest;
import com.mss301.account.dto.LoginResponse;
import com.mss301.account.entity.StoreAccount;
import com.mss301.account.repository.AccountRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// [TODO-3.3: login, refresh, kiểm tra isActive]
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // [TODO-3.3: login] thứ tự: tìm username -> isActive (403) -> mật khẩu (401)
    public LoginResponse login(LoginRequest request) {
        StoreAccount account = accountRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        return issueTokens(account);
    }

    // [TODO-3.3: refresh token] chỉ nhận token có type = refresh
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || !jwtService.isTokenValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        Claims claims = jwtService.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not a refresh token");
        }
        StoreAccount account = accountRepository.findById(Integer.parseInt(claims.getSubject()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        return issueTokens(account);
    }

    private LoginResponse issueTokens(StoreAccount account) {
        String subject = account.getAccountId().toString();
        return LoginResponse.builder()
            .accessToken(jwtService.generateAccessToken(subject, account.getRole()))
            .refreshToken(jwtService.generateRefreshToken(subject))
            .tokenType("Bearer")
            .role(account.getRole())
            .username(account.getUsername())
            .build();
    }
}
