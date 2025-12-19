package com.aiantfarm.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
  private final SecretKey key;
  private final String issuer;
  private final long accessTtlSeconds;
  private final long refreshTtlSeconds;

  public JwtService(
      @Value("${antfarm.jwt.secret:dev-secret-change-me-32-bytes-min}") String secret,
      @Value("${antfarm.jwt.issuer:ai-antfarm}") String issuer,
      @Value("${antfarm.jwt.accessTtlSeconds:1800}") long accessTtlSeconds,
      @Value("${antfarm.jwt.refreshTtlSeconds:604800}") long refreshTtlSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.accessTtlSeconds = accessTtlSeconds;
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  public String access(String userId, String userEmail, String displayName, String roles) {
    return issue(userId, userEmail, displayName, roles, "access", accessTtlSeconds);
  }
  public String refresh(String userId, String userEmail, String displayName, String roles) {
    return issue(userId, userEmail, displayName, roles, "refresh", refreshTtlSeconds);
  }

  private String issue(String userId, String userEmail, String displayName, String roles, String typ, long ttlSecs) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(ttlSecs);
    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(userId)
        .addClaims(Map.of(
            "userEmail", userEmail,
            "displayName", displayName,
            "roles", roles,
            "typ", typ
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

  public boolean isRefresh(Jws<Claims> jws) {
    return "refresh".equals(jws.getBody().get("typ"));
  }
}
