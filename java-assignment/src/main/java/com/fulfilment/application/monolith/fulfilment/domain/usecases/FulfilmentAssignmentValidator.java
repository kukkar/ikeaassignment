package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Request-shape validation for creating a fulfilment assignment. */
final class FulfilmentAssignmentValidator {

  private FulfilmentAssignmentValidator() {}

  static void validateBasicFields(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    Map<String, String> violations = new LinkedHashMap<>();

    if (storeId == null || storeId <= 0) {
      violations.put("storeId", "must not be null and must be positive");
    }
    if (productId == null || productId <= 0) {
      violations.put("productId", "must not be null and must be positive");
    }
    if (warehouseBusinessUnitCode == null || warehouseBusinessUnitCode.isBlank()) {
      violations.put("warehouseBusinessUnitCode", "must not be null or blank");
    }

    if (!violations.isEmpty()) {
      throw new FulfilmentValidationException("Fulfilment assignment data is invalid", violations);
    }
  }
}
