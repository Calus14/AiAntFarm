package com.aiantfarm.api;

import com.aiantfarm.api.dto.auth.AuthResponse;
import com.aiantfarm.api.dto.auth.LoginRequest;
import com.aiantfarm.api.dto.auth.RefreshRequest;
import com.aiantfarm.api.dto.auth.RegisterRequest;
import com.aiantfarm.domain.User;
import com.aiantfarm.exception.QuotaExceededException;
import com.aiantfarm.repository.AuthCredentialsRepository;
import com.aiantfarm.repository.PasswordResetTokenRepository;
import com.aiantfarm.repository.UserRepository;
import com.aiantfarm.repository.entity.AuthCredentialsEntity;
import com.aiantfarm.repository.entity.PasswordResetTokenEntity;
import com.aiantfarm.service.JwtService;
import com.aiantfarm.service.OneTimeTokenService;
import com.aiantfarm.service.email.EmailService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final AuthCredentialsRepository authRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final OneTimeTokenService ottService;
  private final EmailService emailService;

  private final int maxUsers;
  private final int defaultAntLimit;
  private final int defaultAntRoomLimit;

  // Simple in-memory rate limiter: email -> lastRequestTime
  private final Map<String, Instant> rateLimitMap = new ConcurrentHashMap<>();

  public AuthController(UserRepository userRepository,
                        AuthCredentialsRepository authRepository,
                        PasswordResetTokenRepository tokenRepository,
                        PasswordEncoder encoder,
                        JwtService jwt,
                        OneTimeTokenService ottService,
                        EmailService emailService,
                        @Value("${antfarm.limits.maxUsers:5}") int maxUsers,
                        @Value("${antfarm.limits.defaultAntLimit:3}") int defaultAntLimit,
                        @Value("${antfarm.limits.defaultAntRoomLimit:3}") int defaultAntRoomLimit) {
    this.userRepository = userRepository;
    this.authRepository = authRepository;
    this.tokenRepository = tokenRepository;
    this.encoder = encoder;
    this.jwt = jwt;
    this.ottService = ottService;
    this.emailService = emailService;
    this.maxUsers = maxUsers;
    this.defaultAntLimit = defaultAntLimit;
    this.defaultAntRoomLimit = defaultAntRoomLimit;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    if (maxUsers > 0) {
      // MVP: fastest implementation is a scan-based count.
      long existingUsers = userRepository.countUsers();
      if (existingUsers >= maxUsers) {
        throw new QuotaExceededException("Registration is temporarily limited (max users reached)");
      }
    }

    String email = req.getUserEmail().toLowerCase();

    var userOptional = authRepository.findByEmail(email);
    if (userOptional.isPresent()) {
      throw new IllegalArgumentException("Email already taken");
    }

    // Persist per-user limits at signup so UI can display them and future upgrades can edit them.
    User base = User.create(email, req.getDisplayName());
    User newUser = new User(base.id(), base.userEmail(), base.displayName(), base.createdAt(), base.active(),
        defaultAntLimit, defaultAntRoomLimit);

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
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
    }

    // Our JwtAuthFilter sets principal to the userId string.
    String userId = String.valueOf(auth.getPrincipal());
    if (userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)) {
      return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
    }

    var u = userRepository.findByUserId(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).body(Map.of("error","unauthorized"));
    return ResponseEntity.ok(Map.of(
        "id", u.id(),
        "userEmail", u.userEmail(),
        "active", u.active(),
        "displayName", u.displayName(),
        "antLimit", u.antLimit() == null ? defaultAntLimit : u.antLimit(),
        "antRoomLimit", u.antRoomLimit() == null ? defaultAntRoomLimit : u.antRoomLimit()
    ));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout() { return ResponseEntity.ok(Map.of("ok", true)); }

  /**
   * Initiates the password reset flow.
   * <p>
   * Why: To allow users to recover their account if they forget their password.
   * <p>
   * What:
   * 1. Checks rate limits to prevent abuse.
   * 2. Checks if user exists (silently to prevent enumeration).
   * 3. If exists: creates a signed token, saves metadata to DB, and sends an email.
   * 4. Always returns a generic 200 OK response.
   */
  @PostMapping("/request-password-reset")
  public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body) {
    String email = body.get("email");
    if (email == null || email.isBlank()) {
      return ResponseEntity.ok(Map.of("message", "If an account exists, instructions have been sent."));
    }
    email = email.toLowerCase();

    // Rate limit check (e.g., 1 request per minute per email)
    Instant last = rateLimitMap.get(email);
    if (last != null && last.plusSeconds(60).isAfter(Instant.now())) {
      return ResponseEntity.ok(Map.of("message", "If an account exists, instructions have been sent."));
    }
    rateLimitMap.put(email, Instant.now());

    // Check if user exists (silently)
    var creds = authRepository.findByEmail(email);
    if (creds.isPresent()) {
      String token = ottService.createResetToken(email);
      // Persist token metadata
      Jws<Claims> jws = ottService.parse(token);
      String jti = jws.getBody().getId();
      Instant exp = jws.getBody().getExpiration().toInstant();

      PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
      entity.setTokenId(jti);
      entity.setEmail(email);
      entity.setPurpose("reset");
      entity.setExpiresAt(exp);
      entity.setTtl(exp.getEpochSecond());
      tokenRepository.save(entity);

      emailService.sendPasswordReset(email, token);
    }

    // Always return generic success
    return ResponseEntity.ok(Map.of("message", "If an account exists, instructions have been sent."));
  }

  /**
   * Completes the password reset flow.
   * <p>
   * Why: To securely update the user's password using the token received via email.
   * <p>
   * What:
   * 1. Parses and validates the JWT signature.
   * 2. Checks DynamoDB to ensure the token hasn't been used or expired.
   * 3. Updates the password hash in the repository.
   * 4. Marks the token as used to prevent replay attacks.
   */
  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    try {
      Jws<Claims> jws = ottService.parse(req.getToken());
      String jti = jws.getBody().getId();
      String email = jws.getBody().getSubject();
      String purpose = (String) jws.getBody().get("purpose");

      if (!"reset".equals(purpose)) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid token purpose"));
      }

      var tokenEntity = tokenRepository.findByTokenId(jti);
      if (tokenEntity.isEmpty() || tokenEntity.get().isUsed()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Token invalid or already used"));
      }

      // Update password
      var creds = authRepository.findByEmail(email).orElseThrow();
      String hash = encoder.encode(req.getNewPassword());
      creds.setPasswordHash(hash);
      authRepository.create(creds); // Overwrite/update

      // Mark token used
      tokenRepository.markUsed(jti);

      return ResponseEntity.ok(Map.of("message", "Password updated successfully"));

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired token"));
    }
  }

  @Data
  public static class ResetPasswordRequest {
    @NotBlank
    private String token;
    @NotBlank
    private String newPassword;
  }
}
