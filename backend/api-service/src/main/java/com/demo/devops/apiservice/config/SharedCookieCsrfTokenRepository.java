package com.demo.devops.apiservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.util.WebUtils;

final class SharedCookieCsrfTokenRepository implements CsrfTokenRepository {
  static final String COOKIE_NAME = "XSRF-TOKEN";
  static final String HEADER_NAME = "X-XSRF-TOKEN";
  static final String PARAMETER_NAME = "_csrf";

  @Override
  public CsrfToken generateToken(HttpServletRequest request) {
    return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, UUID.randomUUID().toString());
  }

  @Override
  public void saveToken(
      CsrfToken token,
      HttpServletRequest request,
      HttpServletResponse response) {
    // The auth service owns the XSRF-TOKEN cookie for the SPA.
  }

  @Override
  public CsrfToken loadToken(HttpServletRequest request) {
    var cookie = WebUtils.getCookie(request, COOKIE_NAME);
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return null;
    }
    return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, cookie.getValue());
  }
}
