package com.aiantfarm.api;

import com.aiantfarm.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        Jws<Claims> jws = jwt.parse(token);
        String sub = jws.getBody().getSubject(); // userId
        String roles = (String) jws.getBody().get("roles");
        var auth = new UsernamePasswordAuthenticationToken(sub, null,
            roles == null ? List.of() : rolesToAuthorities(roles));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception ignored) { }
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
