package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.GetWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ListWarehousesOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject ListWarehousesOperation listWarehousesOperation;
  @Inject GetWarehouseOperation getWarehouseOperation;
  @Inject CreateWarehouseOperation createWarehouseOperation;
  @Inject ArchiveWarehouseOperation archiveWarehouseOperation;
  @Inject ReplaceWarehouseOperation replaceWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return listWarehousesOperation.listActive().stream().map(this::toApiResponse).toList();
  }

  @Override
  public Warehouse createANewWarehouseUnit(Warehouse data) {
    var warehouse = toDomain(data);
    createWarehouseOperation.create(warehouse);
    return toApiResponse(warehouse);
  }

  @Override
  public Warehouse getTheActiveWarehouseUnitByBusinessUnitCode(String businessUnitCode) {
    return toApiResponse(getWarehouseOperation.getActiveByBusinessUnitCode(businessUnitCode));
  }

  @Override
  public void archiveTheActiveWarehouseUnitByBusinessUnitCode(String businessUnitCode) {
    archiveWarehouseOperation.archive(businessUnitCode);
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(String businessUnitCode, Warehouse data) {
    var replacement = toDomain(data);
    var replaced = replaceWarehouseOperation.replace(businessUnitCode, replacement);
    return toApiResponse(replaced);
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomain(
      Warehouse data) {
    if (data == null) {
      throw new WarehouseValidationException("Request body is required");
    }

    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }

  private Warehouse toApiResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    response.setCreatedAt(toDate(warehouse.createdAt));
    response.setArchivedAt(toDate(warehouse.archivedAt));
    return response;
  }

  private static Date toDate(LocalDateTime dateTime) {
    return dateTime == null ? null : Date.from(dateTime.toInstant(ZoneOffset.UTC));
  }
}
