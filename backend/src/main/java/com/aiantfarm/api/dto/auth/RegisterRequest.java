package com.aiantfarm.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email(message = "Must be a valid email")
    @NotBlank(message = "Email is required")
    private String userEmail;
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
    private String password;
    @NotBlank(message = "Display name is required")
    private String displayName;
}
