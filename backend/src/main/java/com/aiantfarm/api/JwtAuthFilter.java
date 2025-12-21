package com.aiantfarm.api;

import com.aiantfarm.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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
        var auth = new UsernamePasswordAuthenticationToken(sub, null,
            roles == null ? List.of() : rolesToAuthorities(roles));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception jwtError) {
        // Log full stacktrace to help debugging invalid/expired/malformed tokens
        log.error("Failed to parse/validate JWT", jwtError);
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
