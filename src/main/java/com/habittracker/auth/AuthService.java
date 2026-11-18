package com.habittracker.auth;

import com.habittracker.auth.dto.AuthResponse;
import com.habittracker.auth.dto.LoginRequest;
import com.habittracker.auth.dto.RegisterRequest;
import com.habittracker.auth.dto.UserResponse;
import com.habittracker.common.ConflictException;
import com.habittracker.common.ResourceNotFoundException;
import com.habittracker.common.UnauthorizedException;
import com.habittracker.security.JwtService;
import com.habittracker.user.User;
import com.habittracker.user.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("An account with this email already exists");
    }

    User user =
        new User(email, request.displayName().trim(), passwordEncoder.encode(request.password()));
    userRepository.save(user);
    return issueToken(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(email, request.password()));
    } catch (AuthenticationException ex) {
      throw new UnauthorizedException("Invalid email or password");
    }

    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    return issueToken(user);
  }

  @Transactional(readOnly = true)
  public UserResponse me(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return UserResponse.from(user);
  }

  private AuthResponse issueToken(User user) {
    return new AuthResponse(jwtService.generateToken(user.getEmail()), UserResponse.from(user));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
