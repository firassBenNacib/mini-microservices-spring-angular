package com.demo.devops.authservice.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
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
  @SuppressWarnings("unused")
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    passwordEncoder = Mockito.mock(PasswordEncoder.class);
    userSeeder = new UserSeeder(userRepository, passwordEncoder, "demo@example.com", "change-me");
  }

  @Test
  void runUpdatesExistingSeedUserWhenPasswordDoesNotMatch() {
    UserAccount existingUser = new UserAccount();
    existingUser.setEmail("demo@example.com");
    existingUser.setPasswordHash("stored-password");
    existingUser.setRole("admin");

    when(userRepository.findByEmailIgnoreCase("demo@example.com"))
        .thenReturn(Optional.of(existingUser));
    when(passwordEncoder.matches("change-me", "stored-password")).thenReturn(false);
    when(passwordEncoder.encode("change-me")).thenReturn("encoded-password");

    userSeeder.run();

    ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userRepository).save(userCaptor.capture());
    UserAccount savedUser = userCaptor.getValue();
    assertEquals("demo@example.com", savedUser.getEmail());
    assertEquals("encoded-password", savedUser.getPasswordHash());
    assertEquals("user", savedUser.getRole());
  }

  @Test
  void runLeavesExistingSeedUserPasswordWhenItAlreadyMatches() {
    UserAccount existingUser = new UserAccount();
    existingUser.setEmail("demo@example.com");
    existingUser.setPasswordHash("stored-password");
    existingUser.setRole("user");

    when(userRepository.findByEmailIgnoreCase("demo@example.com"))
        .thenReturn(Optional.of(existingUser));
    when(passwordEncoder.matches("change-me", "stored-password")).thenReturn(true);

    userSeeder.run();

    verify(passwordEncoder).matches("change-me", "stored-password");
    verify(userRepository).save(eq(existingUser));
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
