package com.demo.devops.mailerservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {
  @Test
  void csrfProtectionMatcherIgnoresMailerIngress() {
    MockHttpServletRequest request = request("POST", "/send");

    assertFalse(SecurityConfig.csrfProtectionMatcher().matches(request));
  }

  @Test
  void csrfProtectionMatcherProtectsOtherMailerPosts() {
    MockHttpServletRequest request = request("POST", "/internal");

    assertTrue(SecurityConfig.csrfProtectionMatcher().matches(request));
  }

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    return request;
  }
}
