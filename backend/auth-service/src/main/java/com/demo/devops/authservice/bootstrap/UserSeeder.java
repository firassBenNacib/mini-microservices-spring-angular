package com.demo.devops.authservice.bootstrap;

import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements CommandLineRunner {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String demoEmail;
  private final String demoPassword;

  public UserSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.demo-user.email}") String demoEmail,
      @Value("${app.demo-user.password}") String demoPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.demoEmail = demoEmail;
    this.demoPassword = demoPassword;
  }

  @Override
  public void run(String... args) {
    UserAccount user = userRepository.findByEmailIgnoreCase(demoEmail).orElseGet(UserAccount::new);
    user.setEmail(demoEmail);
    user.setRole("user");

    if (user.getPasswordHash() == null || !passwordEncoder.matches(demoPassword, user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(demoPassword));
    }

    userRepository.save(user);
  }
}
