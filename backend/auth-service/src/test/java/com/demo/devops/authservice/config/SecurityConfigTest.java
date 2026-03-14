package com.demo.devops.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.devops.authservice.client.AuditClient;
import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.repository.UserRepository;
import com.demo.devops.authservice.security.JwtAuthFilter;
import com.demo.devops.authservice.security.JwtService;
import com.demo.devops.authservice.security.RefreshTokenService;
import com.demo.devops.authservice.web.AuthController;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = {
    "app.cookie.secure=false",
    "logging.level.org.springdoc.core.events.SpringDocAppInitializer=ERROR"
})
@Import({SecurityConfig.class, JwtAuthFilter.class})
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserRepository userRepository;

  @MockitoBean
  private RefreshTokenService refreshTokenService;

  @MockitoBean
  private AuditClient auditClient;

  @MockitoBean
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private JwtService jwtService;

  @Test
  void sessionEndpointProvidesBootstrapCookie() throws Exception {
    given(jwtService.getAccessExpirationSeconds()).willReturn(3600L);
    given(jwtService.getRefreshExpirationSeconds()).willReturn(604800L);

    mockMvc.perform(get("/auth/session"))
        .andExpect(status().isOk());
  }

  @Test
  void loginRequiresMatchingXsrfCookieAndHeader() throws Exception {
    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"secret"}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginAcceptsReadableXsrfCookieAndHeader() throws Exception {
    var user = new UserAccount();
    user.setEmail("user@example.com");
    user.setPasswordHash("hashed-password");
    user.setRole("user");

    given(userRepository.findByEmailIgnoreCase("user@example.com")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("secret", "hashed-password")).willReturn(true);
    given(jwtService.generateAccessToken("user@example.com", "user")).willReturn("access-token");
    given(jwtService.generateRefreshToken("user@example.com", "user")).willReturn("refresh-token");
    given(jwtService.getAccessExpirationSeconds()).willReturn(3600L);
    given(jwtService.getRefreshExpirationSeconds()).willReturn(604800L);
    given(jwtService.refreshExpiresAt()).willReturn(java.time.Instant.now().plusSeconds(604800));

    mockMvc.perform(post("/auth/login")
            .cookie(new Cookie("XSRF-TOKEN", "csrf-token"))
            .header("X-XSRF-TOKEN", "csrf-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"secret"}
                """))
        .andExpect(status().isOk());

    then(auditClient).should().sendEvent("LOGIN_SUCCESS", "user@example.com", "login successful", "auth-service");
  }

  @Test
  void sharedRepositoryLoadsReadableCookieBackedToken() throws Exception {
    var repository = sharedCookieCsrfTokenRepository();
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("XSRF-TOKEN", "shared-token"));

    var token = repository.loadToken(request);

    assertThat(token).isNotNull();
    assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
    assertThat(token.getParameterName()).isEqualTo("_csrf");
    assertThat(token.getToken()).isEqualTo("shared-token");
  }

  @Test
  void sharedRepositoryReturnsNullForMissingOrBlankCookieAndDoesNotWriteCookies() throws Exception {
    var repository = sharedCookieCsrfTokenRepository();
    var missingCookieRequest = new MockHttpServletRequest();
    var blankCookieRequest = new MockHttpServletRequest();
    blankCookieRequest.setCookies(new Cookie("XSRF-TOKEN", " "));
    var response = new MockHttpServletResponse();
    var token = repository.generateToken(new MockHttpServletRequest());

    assertThat(repository.loadToken(missingCookieRequest)).isNull();
    assertThat(repository.loadToken(blankCookieRequest)).isNull();

    repository.saveToken(token, missingCookieRequest, response);

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
    assertThat(token.getParameterName()).isEqualTo("_csrf");
    assertThat(token.getToken()).isNotBlank();
  }

  private CsrfTokenRepository sharedCookieCsrfTokenRepository() throws Exception {
    Method method = SecurityConfig.class.getDeclaredMethod("sharedCookieCsrfTokenRepository");
    method.setAccessible(true);
    return (CsrfTokenRepository) method.invoke(new SecurityConfig(new JwtAuthFilter(jwtService)));
  }
}
