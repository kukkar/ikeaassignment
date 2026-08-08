package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request-shape validation shared by warehouse creation and replacement.
 *
 * <p>Creation has no prior state to compare against, so "stock must not exceed capacity" is
 * validated as a plain cross-field rule there. Replacement, however, has its own more specific
 * rules for the same underlying concern - the new stock must equal the warehouse being replaced,
 * and the new capacity must accommodate that stock ({@link StockMismatchException} /
 * {@link com.fulfilment.application.monolith.warehouses.domain.exceptions.InsufficientCapacityException}).
 * Applying the generic cross-field check there too would make those two exceptions unreachable
 * (given equal stock and stock &lt;= capacity, capacity &gt;= old stock always holds), so
 * replacement uses {@link #validateShape} - field-level checks only - and leaves the cross-field
 * comparison to those replacement-specific rules.
 */
final class WarehouseValidator {

  private WarehouseValidator() {}

  static void validateBasicFields(Warehouse warehouse) {
    Map<String, String> violations = fieldViolations(warehouse);

    if (warehouse.capacity != null
        && warehouse.capacity > 0
        && warehouse.stock != null
        && warehouse.stock >= 0
        && warehouse.stock > warehouse.capacity) {
      violations.put("stock", "must not exceed capacity");
    }

    failIfAny(violations);
  }

  static void validateShape(Warehouse warehouse) {
    failIfAny(fieldViolations(warehouse));
  }

  private static Map<String, String> fieldViolations(Warehouse warehouse) {
    Map<String, String> violations = new LinkedHashMap<>();

    if (isBlank(warehouse.businessUnitCode)) {
      violations.put("businessUnitCode", "must not be null or blank");
    }
    if (isBlank(warehouse.location)) {
      violations.put("location", "must not be null or blank");
    }
    if (warehouse.capacity == null || warehouse.capacity <= 0) {
      violations.put("capacity", "must not be null and must be greater than zero");
    }
    if (warehouse.stock == null || warehouse.stock < 0) {
      violations.put("stock", "must not be null and must not be negative");
    }
    return violations;
  }

  private static void failIfAny(Map<String, String> violations) {
    if (!violations.isEmpty()) {
      throw new WarehouseValidationException("Warehouse data is invalid", violations);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
