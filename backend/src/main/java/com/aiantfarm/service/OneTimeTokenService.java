package com.aiantfarm.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating and parsing short-lived JWTs for specific purposes (reset, verify).
 * <p>
 * Why: To generate and parse JWTs specifically for "reset" or "verify" purposes.
 * It uses a separate secret (optional) and shorter expiration times than login tokens.
 * <p>
 * What: Creates a JWT with a unique ID (jti) and a purpose claim.
 */
@Service
public class OneTimeTokenService {

  private final SecretKey key;
  private final String issuer;
  private final long resetTtlSeconds;

  public OneTimeTokenService(
      @Value("${antfarm.jwt.resetSecret:${antfarm.jwt.secret}}") String secret,
      @Value("${antfarm.jwt.issuer:ai-antfarm}") String issuer,
      @Value("${antfarm.auth.reset.ttlSeconds:3600}") long resetTtlSeconds) {
    // Fallback to main JWT secret if resetSecret is not explicitly set
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.resetTtlSeconds = resetTtlSeconds;
  }

  public String createResetToken(String email) {
    return createToken(email, "reset", resetTtlSeconds);
  }

  public String createVerifyToken(String email) {
    return createToken(email, "verify", resetTtlSeconds); // Reuse TTL or add separate config
  }

  private String createToken(String email, String purpose, long ttlSeconds) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(ttlSeconds);
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(email) // Subject is email for these tokens
        .setId(jti)
        .addClaims(Map.of(
            "purpose", purpose,
            "typ", "ott" // One Time Token
        ))
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  public Jws<Claims> parse(String token) {
    return Jwts.parserBuilder()
        .requireIssuer(issuer)
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token);
  }
}

