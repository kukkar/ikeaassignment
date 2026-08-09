package com.fulfilment.application.monolith.common;

import java.util.Map;

/**
 * Stable JSON error body returned for every failed request across modules that use {@link
 * DomainErrorType}-classified domain exceptions (Warehouse, Location, Fulfilment). Not tied to any
 * single module - lives here so no module's REST adapter has to depend on another's package to
 * share this shape.
 */
public record ApiError(String code, String message, Map<String, String> details) {

  public ApiError(String code, String message) {
    this(code, message, null);
  }
}
