package com.demo.devops.authservice.dto;

public record LoginResponse(String token, long expiresIn, UserInfo user) {
  public record UserInfo(String email, String role) {}
}
