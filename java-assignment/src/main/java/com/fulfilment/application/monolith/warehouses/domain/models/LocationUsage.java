package com.fulfilment.application.monolith.warehouses.domain.models;

/** Aggregate usage of a {@link Location} by its currently active warehouses. */
public record LocationUsage(long activeWarehouseCount, int activeCapacity) {}
