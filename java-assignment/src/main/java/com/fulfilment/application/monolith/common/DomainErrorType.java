package com.fulfilment.application.monolith.common;

/**
 * Classification of a domain failure, independent of any transport (HTTP, etc) and of any single
 * module - shared by every domain that raises business-rule failures (Warehouse, Location,
 * Fulfilment). REST adapters translate this into a concrete status code, keeping the domain layer
 * free of JAX-RS concerns.
 */
public enum DomainErrorType {
  VALIDATION,
  NOT_FOUND,
  CONFLICT
}
