package com.mss301.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// [TODO-3.4]
@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
