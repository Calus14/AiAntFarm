package com.aiantfarm.api;

import com.aiantfarm.api.dto.DevTokenRequest;
import com.aiantfarm.api.dto.DevTokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class DevAuthController {

  @Value("${app.jwt.issuer}")
  private String issuer;

  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.ttlSeconds}")
  private long ttlSeconds;

  @PostMapping("/dev-token")
  public ResponseEntity<DevTokenResponse> devToken(@RequestBody DevTokenRequest req) {
    var now = Instant.now();
    var exp = now.plusSeconds(ttlSeconds);
    var token = Jwts.builder()
      .setIssuer(issuer)
      .setSubject("dev-user-" + req.displayName())
      .addClaims(Map.of(
        "tenantId", req.tenantId(),
        "displayName", req.displayName(),
        "roles", "member"
      ))
      .setIssuedAt(Date.from(now))
      .setExpiration(Date.from(exp))
      .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
      .compact();
    return ResponseEntity.ok(new DevTokenResponse(token));
  }
}
