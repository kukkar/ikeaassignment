package com.fulfilment.application.monolith.fulfilment.domain.models;

import java.time.LocalDateTime;

/**
 * A Store + Product + Warehouse fulfilment assignment: the warehouse identified by {@code
 * warehouseBusinessUnitCode} is one of the fulfilment units for {@code productId} at {@code
 * storeId}.
 *
 * <p>Unlike {@code Warehouse}, this model exposes its own database-generated {@code id} as the
 * public identifier: there is no single natural business key for a three-way association the way
 * businessUnitCode is for a warehouse, so the row id is the simplest stable reference for deletion
 * and is exposed deliberately (see README.md).
 */
public class FulfilmentAssignment {

  public Long id;

  public Long storeId;

  public Long productId;

  public String warehouseBusinessUnitCode;

  public LocalDateTime createdAt;

  /**
   * Whether {@code warehouseBusinessUnitCode} currently resolves to an active warehouse.
   * Populated by listing use cases; always {@code true} for a freshly created assignment, since
   * creation requires an active warehouse to exist. Not persisted - the row itself never changes
   * when its warehouse is later archived (see README.md "Archive without replacement").
   */
  public boolean warehouseActive = true;
}
