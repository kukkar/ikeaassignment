package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * No {@code @ManyToOne} associations to Store/Product/Warehouse on purpose: this entity only
 * needs their ids for the business rules, and modelling full JPA relationships here would couple
 * this table's lifecycle to theirs (cascade behaviour, lazy-loading proxies) for no benefit. Store
 * and Product foreign keys are added via raw DDL in import.sql instead, since a bare {@code Long}
 * column has no association for {@code @JoinColumn} to attach to.
 */
@Entity
@Table(
    name = "fulfilment_assignment",
    indexes = {
      @Index(name = "ix_fulfilment_assignment_store", columnList = "store_id"),
      @Index(name = "ix_fulfilment_assignment_store_product", columnList = "store_id, product_id"),
      @Index(name = "ix_fulfilment_assignment_warehouse", columnList = "warehouse_business_unit_code")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "ux_fulfilment_assignment_store_product_warehouse",
            columnNames = {"store_id", "product_id", "warehouse_business_unit_code"}))
@Cacheable
public class DbFulfilmentAssignment {

  @Id @GeneratedValue public Long id;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "warehouse_business_unit_code", nullable = false)
  public String warehouseBusinessUnitCode;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt;

  public DbFulfilmentAssignment() {}

  public static DbFulfilmentAssignment fromDomain(FulfilmentAssignment assignment) {
    var db = new DbFulfilmentAssignment();
    db.storeId = assignment.storeId;
    db.productId = assignment.productId;
    db.warehouseBusinessUnitCode = assignment.warehouseBusinessUnitCode;
    db.createdAt = assignment.createdAt;
    return db;
  }

  public FulfilmentAssignment toDomain() {
    var assignment = new FulfilmentAssignment();
    assignment.id = this.id;
    assignment.storeId = this.storeId;
    assignment.productId = this.productId;
    assignment.warehouseBusinessUnitCode = this.warehouseBusinessUnitCode;
    assignment.createdAt = this.createdAt;
    return assignment;
  }
}
