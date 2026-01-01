package com.aiantfarm.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock implementation of EmailService for local development.
 * <p>
 * Why: Avoids AWS SES Sandbox restrictions by logging emails to the console instead of sending them.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "antfarm.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailService implements EmailService {

  private final String frontendBaseUrl;

  public MockEmailService(@Value("${antfarm.frontend.url:http://localhost:5173}") String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void sendPasswordReset(String to, String token) {
    String link = frontendBaseUrl + "/reset-password?token=" + token;
    log.info("================= MOCK EMAIL ==================");
    log.info("Type: Password Reset");
    log.info("To:   {}", to);
    log.info("Link: {}", link);
    log.info("===============================================");
  }

  @Override
  public void sendEmailVerification(String to, String token) {
    String link = frontendBaseUrl + "/verify-email?token=" + token;
    log.info("================= MOCK EMAIL ==================");
    log.info("Type: Email Verification");
    log.info("To:   {}", to);
    log.info("Link: {}", link);
    log.info("===============================================");
  }
}
