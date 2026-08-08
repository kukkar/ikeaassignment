package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import java.util.Map;

/** Stable JSON error body returned for every failed warehouse/location request. */
public record ApiError(String code, String message, Map<String, String> details) {

  public ApiError(String code, String message) {
    this(code, message, null);
  }
}
