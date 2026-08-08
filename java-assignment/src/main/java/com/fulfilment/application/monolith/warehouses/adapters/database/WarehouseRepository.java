package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public List<Warehouse> getAll() {
    return find("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public Warehouse findActiveByBusinessUnitCode(String businessUnitCode) {
    // Deliberately not "find first": replacement leaves multiple historical rows sharing the
    // same business unit code, only one of which (archivedAt is null) may ever be active.
    DbWarehouse active =
        find("businessUnitCode = ?1 and archivedAt is null", businessUnitCode).firstResult();
    return active == null ? null : active.toWarehouse();
  }

  @Override
  public Warehouse lockActiveByBusinessUnitCode(String businessUnitCode) {
    // SELECT ... FOR UPDATE. Under PostgreSQL's read-committed default, if a concurrent
    // transaction commits a change to this same row while we're blocked waiting for the lock,
    // our query re-checks the WHERE clause against the row's new committed version once the lock
    // is granted. So if that other transaction archived this row, "archivedAt is null" no longer
    // matches and we correctly get back no row - never a stale, already-archived snapshot.
    DbWarehouse active =
        find("businessUnitCode = ?1 and archivedAt is null", businessUnitCode)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult();
    return active == null ? null : active.toWarehouse();
  }

  @Override
  public LocationUsage lockActiveUsageByLocation(String locationIdentifier) {
    // A transaction-scoped advisory lock keyed by the location identifier, held until this
    // transaction commits or rolls back. Unlike a row-level lock, this also serializes concurrent
    // requests when the location currently has zero active warehouses - there would be no row to
    // lock in that case, but a create/replace race there is exactly what would otherwise let two
    // concurrent requests both pass the count/capacity check.
    getEntityManager()
        .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1))")
        .setParameter(1, locationIdentifier)
        .getSingleResult();

    List<DbWarehouse> activeAtLocation = find("location = ?1 and archivedAt is null", locationIdentifier).list();
    int activeCapacity = activeAtLocation.stream().mapToInt(w -> w.capacity).sum();
    return new LocationUsage(activeAtLocation.size(), activeCapacity);
  }

  @Override
  public void create(Warehouse warehouse) {
    persist(DbWarehouse.fromWarehouse(warehouse));
  }

  @Override
  public boolean archiveActive(String businessUnitCode, LocalDateTime archivedAt) {
    long updated =
        update(
            "archivedAt = ?1 where businessUnitCode = ?2 and archivedAt is null",
            archivedAt,
            businessUnitCode);
    return updated > 0;
  }
}
