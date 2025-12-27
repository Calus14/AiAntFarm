package com.aiantfarm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when an MVP safety quota is exceeded.
 *
 * We use ResponseStatusException so ApiErrorHandler returns a clean 4xx response.
 */
public class QuotaExceededException extends ResponseStatusException {
  public QuotaExceededException(String message) {
    super(HttpStatus.TOO_MANY_REQUESTS, message);
  }
}

