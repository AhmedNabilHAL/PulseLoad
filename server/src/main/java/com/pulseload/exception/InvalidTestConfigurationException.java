package com.pulseload.exception;

/**
 * Thrown when a test's workload configuration is internally inconsistent in a
 * way that request
 * (annotation) validation can't express — for example, a closed-model profile
 * whose peak target
 * exceeds its own concurrency cap. Business validation, enforced in the service
 * layer.
 */
public class InvalidTestConfigurationException extends RuntimeException {

  public InvalidTestConfigurationException(String message) {
    super(message);
  }
}
