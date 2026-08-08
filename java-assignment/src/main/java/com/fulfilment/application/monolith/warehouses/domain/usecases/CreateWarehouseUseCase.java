package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DuplicateBusinessUnitCodeException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationCapacityExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationWarehouseLimitExceededException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;
  private final Clock clock;

  public CreateWarehouseUseCase(
      WarehouseStore warehouseStore, LocationResolver locationResolver, Clock clock) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    WarehouseValidator.validateBasicFields(warehouse);

    if (warehouseStore.findActiveByBusinessUnitCode(warehouse.businessUnitCode) != null) {
      throw new DuplicateBusinessUnitCodeException(warehouse.businessUnitCode);
    }

    Location location = locationResolver.resolveByIdentifier(warehouse.location);

    LocationUsage usage = warehouseStore.lockActiveUsageByLocation(location.identification);

    if (usage.activeWarehouseCount() + 1 > location.maxNumberOfWarehouses) {
      throw new LocationWarehouseLimitExceededException(
          location.identification, location.maxNumberOfWarehouses);
    }

    if (usage.activeCapacity() + warehouse.capacity > location.maxCapacity) {
      throw new LocationCapacityExceededException(location.identification, location.maxCapacity);
    }

    warehouse.createdAt = LocalDateTime.now(clock);
    warehouse.archivedAt = null;

    warehouseStore.create(warehouse);
  }
}
