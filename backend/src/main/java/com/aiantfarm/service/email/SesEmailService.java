package com.aiantfarm.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * AWS SES implementation of EmailService for production.
 * <p>
 * Why: Used for Production. It uses the AWS SDK to send emails via SES, which is reliable and scalable.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "antfarm.email.provider", havingValue = "ses")
public class SesEmailService implements EmailService {

  private final SesClient sesClient;
  private final String fromAddress;
  private final String frontendBaseUrl;

  public SesEmailService(SesClient sesClient,
                         @Value("${antfarm.email.from:noreply@theaiantfarm.com}") String fromAddress,
                         @Value("${antfarm.frontend.url:https://theaiantfarm.com}") String frontendBaseUrl) {
    this.sesClient = sesClient;
    this.fromAddress = fromAddress;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void sendPasswordReset(String to, String token) {
    String link = frontendBaseUrl + "/reset-password?token=" + token;
    String subject = "Reset your password";
    String body = "<html><body><p>Click the link below to reset your password:</p>" +
        "<p><a href=\"" + link + "\">Reset Password</a></p>" +
        "<p>This link expires in 1 hour.</p></body></html>";
    send(to, subject, body);
  }

  @Override
  public void sendEmailVerification(String to, String token) {
    String link = frontendBaseUrl + "/verify-email?token=" + token;
    String subject = "Verify your email";
    String body = "<html><body><p>Click the link below to verify your email:</p>" +
        "<p><a href=\"" + link + "\">Verify Email</a></p></body></html>";
    send(to, subject, body);
  }

  private void send(String to, String subject, String htmlBody) {
    try {
      SendEmailRequest request = SendEmailRequest.builder()
          .source(fromAddress)
          .destination(Destination.builder().toAddresses(to).build())
          .message(Message.builder()
              .subject(Content.builder().data(subject).build())
              .body(Body.builder().html(Content.builder().data(htmlBody).build()).build())
              .build())
          .build();
      sesClient.sendEmail(request);
      log.info("Sent email to {} via SES", to);
    } catch (SesException e) {
      log.error("Failed to send email to {} via SES", to, e);
      throw new RuntimeException("Failed to send email via SES", e);
    }
  }
}
