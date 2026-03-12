package com.demo.devops.apiservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SharedCookieCsrfTokenRepositoryTest {
  private final SharedCookieCsrfTokenRepository repository = new SharedCookieCsrfTokenRepository();

  @Test
  void loadTokenReturnsNullWhenCookieIsMissing() {
    var request = new MockHttpServletRequest();

    assertThat(repository.loadToken(request)).isNull();
  }

  @Test
  void loadTokenReturnsCookieBackedToken() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(SharedCookieCsrfTokenRepository.COOKIE_NAME, "shared-token"));

    var token = repository.loadToken(request);

    assertThat(token).isNotNull();
    assertThat(token.getToken()).isEqualTo("shared-token");
    assertThat(token.getHeaderName()).isEqualTo(SharedCookieCsrfTokenRepository.HEADER_NAME);
    assertThat(token.getParameterName()).isEqualTo(SharedCookieCsrfTokenRepository.PARAMETER_NAME);
  }

  @Test
  void saveTokenDoesNotWriteOrClearCookies() {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var token = repository.generateToken(request);

    repository.saveToken(token, request, response);

    assertThat(response.getHeaders("Set-Cookie")).isEmpty();
  }
}
