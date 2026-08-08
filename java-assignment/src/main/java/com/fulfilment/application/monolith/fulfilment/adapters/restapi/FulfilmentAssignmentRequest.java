package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

/** POST request body for creating a fulfilment assignment. storeId comes from the path. */
public class FulfilmentAssignmentRequest {

  public Long productId;

  public String warehouseBusinessUnitCode;
}
