package com.aiantfarm.api;

import com.aiantfarm.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    String token = null;

    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7).trim();
    } else if (request.getParameter("token") != null && request.getRequestURI().contains("/stream")) {
      token = request.getParameter("token").trim();
      if (token.startsWith("Bearer ")) {
        token = token.substring(7).trim();
      }
    }

    if (token != null) {
      try {
        Jws<Claims> jws = jwt.parse(token);
        String sub = jws.getBody().getSubject(); // userId
        String roles = (String) jws.getBody().get("roles");
        String displayName = (String) jws.getBody().get("displayName");

        var auth = new UsernamePasswordAuthenticationToken(sub, null,
            roles == null ? List.of() : rolesToAuthorities(roles));

        // Make displayName available downstream without changing principal type.
        auth.setDetails(displayName);

        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (ExpiredJwtException expired) {
        // Common case: token expired. No auth set; log at debug so logs aren't noisy.
        log.debug("JWT expired: {}", expired.getMessage());
      } catch (JwtException | IllegalArgumentException badToken) {
        // Malformed, unsupported, signature, etc. Log at debug as these are expected from clients with bad tokens.
        log.debug("Invalid JWT token: {}", badToken.getMessage());
      } catch (Exception unexpected) {
        // Unexpected errors — keep error logging to help debug real issues.
        log.error("Unexpected error while parsing JWT", unexpected);
      }
    }
    chain.doFilter(request, response);
  }

  private List<SimpleGrantedAuthority> rolesToAuthorities(String rolesCsv) {
    return rolesCsv == null ? List.of() :
        java.util.Arrays.stream(rolesCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
            .toList();
  }
}
