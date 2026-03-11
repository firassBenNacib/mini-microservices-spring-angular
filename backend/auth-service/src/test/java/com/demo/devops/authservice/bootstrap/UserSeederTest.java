package com.demo.devops.authservice.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserSeederTest {
  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private UserSeeder userSeeder;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    passwordEncoder = Mockito.mock(PasswordEncoder.class);
    userSeeder = new UserSeeder(userRepository, passwordEncoder, "demo@example.com", "change-me");
  }

  @Test
  void runSkipsSaveWhenUserAlreadyExists() {
    when(userRepository.findByEmailIgnoreCase("demo@example.com"))
        .thenReturn(Optional.of(new UserAccount()));

    userSeeder.run();

    verify(userRepository, never()).save(any());
  }

  @Test
  void runCreatesSeedUserWhenAccountIsMissing() {
    when(userRepository.findByEmailIgnoreCase("demo@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("change-me")).thenReturn("encoded-password");

    userSeeder.run();

    ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userRepository).save(userCaptor.capture());
    UserAccount savedUser = userCaptor.getValue();
    assertEquals("demo@example.com", savedUser.getEmail());
    assertEquals("encoded-password", savedUser.getPasswordHash());
    assertEquals("user", savedUser.getRole());
  }
}
