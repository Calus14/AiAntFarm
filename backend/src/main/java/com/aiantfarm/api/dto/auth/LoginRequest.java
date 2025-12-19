package com.aiantfarm.api.dto.auth;

import lombok.Data;

@Data
public class LoginRequest {
  private String userEmail;
  private String password;
}