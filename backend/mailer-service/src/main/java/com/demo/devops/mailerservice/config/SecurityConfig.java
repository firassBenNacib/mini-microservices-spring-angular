package com.demo.devops.mailerservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private static final String MAILER_KEY_HEADER = "x-mailer-key";

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(new CookieCsrfTokenRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            .requireCsrfProtectionMatcher(SecurityConfig::requiresCsrf))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/health").permitAll()
            .requestMatchers(HttpMethod.POST, "/send").permitAll()
            .requestMatchers("/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/actuator/info", "/actuator/prometheus").permitAll()
            .anyRequest().denyAll());

    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return new InMemoryUserDetailsManager();
  }

  private static boolean requiresCsrf(HttpServletRequest request) {
    return CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
        && isBlank(request.getHeader(MAILER_KEY_HEADER));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
