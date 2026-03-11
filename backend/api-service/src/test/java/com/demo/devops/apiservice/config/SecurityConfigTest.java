package com.demo.devops.apiservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {
  @Test
  void csrfProtectionMatcherProtectsApiPostRequests() {
    MockHttpServletRequest request = request("POST", "/api/send-test-email");

    assertTrue(SecurityConfig.csrfProtectionMatcher().matches(request));
  }

  @Test
  void csrfProtectionMatcherIgnoresActuatorRequests() {
    MockHttpServletRequest request = request("POST", "/actuator/health");

    assertFalse(SecurityConfig.csrfProtectionMatcher().matches(request));
  }

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    return request;
  }
}
