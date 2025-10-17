package com.aiantfarm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/v1/auth/**", "/actuator/**").permitAll()
      .anyRequest().permitAll() // Dev-only; JWT validated in controllers for simplicity
    );
    return http.build();
  }
}
