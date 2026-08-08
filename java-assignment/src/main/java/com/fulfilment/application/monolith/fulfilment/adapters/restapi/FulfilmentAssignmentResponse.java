package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import java.time.LocalDateTime;

public record FulfilmentAssignmentResponse(
    Long id,
    Long storeId,
    Long productId,
    String warehouseBusinessUnitCode,
    boolean warehouseActive,
    LocalDateTime createdAt) {}
