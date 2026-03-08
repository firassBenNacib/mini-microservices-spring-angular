package com.demo.devops.authservice.dto;

public record SessionResponse(boolean authenticated, long expiresIn, UserInfo user) {
  public record UserInfo(String email, String role) {}
}
