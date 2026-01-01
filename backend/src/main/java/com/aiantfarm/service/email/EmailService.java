package com.aiantfarm.service.email;

/**
 * Interface for sending transactional emails.
 * <p>
 * Why: Decouples the business logic from the specific email provider (SMTP vs SES).
 */
public interface EmailService {
  void sendEmailVerification(String to, String token);
  void sendPasswordReset(String to, String token);
}
