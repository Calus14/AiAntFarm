package com.aiantfarm.api;

import com.aiantfarm.api.dto.auth.AuthResponse;
import com.aiantfarm.api.dto.auth.LoginRequest;
import com.aiantfarm.api.dto.auth.RefreshRequest;
import com.aiantfarm.api.dto.auth.RegisterRequest;
import com.aiantfarm.domain.User;
import com.aiantfarm.repository.AuthCredentialsRepository;
import com.aiantfarm.repository.UserRepository;
import com.aiantfarm.repository.entity.AuthCredentialsEntity;
import com.aiantfarm.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final AuthCredentialsRepository authRepository;
  private final PasswordEncoder encoder;
  private final JwtService jwt;

  public AuthController(UserRepository userRepository, AuthCredentialsRepository authRepository, PasswordEncoder encoder, JwtService jwt) {
    this.userRepository = userRepository;
    this.authRepository = authRepository;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    String email = req.getEmail().toLowerCase();

    var userOptional = authRepository.findByEmail(email);
    if (userOptional.isPresent()) {
      throw new IllegalArgumentException("Email already taken");
    }

    User newUser = User.create(email, req.getDisplayName());
    userRepository.create(newUser);

    String hash = encoder.encode(req.getPassword());
    AuthCredentialsEntity authCredentialsEntity = new AuthCredentialsEntity();
    authCredentialsEntity.setEmailGSI(email);
    authCredentialsEntity.setPasswordHash(hash);
    authCredentialsEntity.setUserId(newUser.id());
    authRepository.create(authCredentialsEntity);

    String access = jwt.access(newUser.id(), newUser.userEmail(), newUser.displayName(), "member");
    String refresh = jwt.refresh(newUser.id(), newUser.userEmail(), newUser.displayName(), "member");
    return ResponseEntity.ok(new AuthResponse(access, refresh));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    String email = req.getUserEmail().toLowerCase();

    var creds = authRepository.findByEmail(email)
        .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

    if (!encoder.matches(req.getPassword(), creds.getPasswordHash())) {
      throw new BadCredentialsException("Invalid username or password");
    }

    var user = userRepository.findByUserId(creds.getUserId()).orElseThrow();
    String access = jwt.access(user.id(), user.userEmail(), user.displayName(), "member");
    String refresh = jwt.refresh(user.id(), user.userEmail(), user.displayName(), "member");
    return ResponseEntity.ok(new AuthResponse(access, refresh));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    var jws = jwt.parse(req.getRefreshToken());
    if (!jwt.isRefresh(jws)) throw new IllegalArgumentException("Not a refresh token");
    String userId = jws.getBody().getSubject();
    var user = userRepository.findByUserId(userId).orElseThrow();
    String access = jwt.access(user.id(), user.userEmail(), user.displayName(), "member");
    String refresh = jwt.refresh(user.id(), user.userEmail(), user.displayName(), "member");
    return ResponseEntity.ok(new AuthResponse(access, refresh));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me() {
    String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    var u = userRepository.findByUserId(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).body(Map.of("error","unauthorized"));
    return ResponseEntity.ok(Map.of(
        "id", u.id(),
        "userEmail", u.userEmail(),
        "active", u.active(),
        "displayName", u.displayName()
    ));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout() { return ResponseEntity.ok(Map.of("ok", true)); }
}