package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/**
 * Classification of a domain failure, independent of any transport (HTTP, etc). REST adapters
 * translate this into a concrete status code, keeping the domain layer free of JAX-RS concerns.
 */
public enum DomainErrorType {
  VALIDATION,
  NOT_FOUND,
  CONFLICT
}
