package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class FulfilmentAssignmentRepository
    implements FulfilmentAssignmentStore, PanacheRepository<DbFulfilmentAssignment> {

  // Two-integer-key advisory locks (pg_advisory_xact_lock(class, key)) live in a separate
  // keyspace from the single-bigint form WarehouseRepository already uses for its location lock,
  // so these can never collide with it regardless of numeric value - see the Postgres docs on
  // advisory lock functions. The three classes below only need to be distinct from each other.
  private static final int LOCK_CLASS_STORE = 9001;
  private static final int LOCK_CLASS_STORE_PRODUCT = 9002;
  private static final int LOCK_CLASS_WAREHOUSE = 9003;

  @Override
  public void create(FulfilmentAssignment assignment) {
    DbFulfilmentAssignment db = DbFulfilmentAssignment.fromDomain(assignment);
    persist(db);
    assignment.id = db.id;
  }

  @Override
  public boolean deleteByIdForStore(Long assignmentId, Long storeId) {
    return delete("id = ?1 and storeId = ?2", assignmentId, storeId) > 0;
  }

  @Override
  public List<FulfilmentAssignment> listByStore(Long storeId) {
    return find("storeId = ?1", Sort.by("id"), storeId).stream()
        .map(DbFulfilmentAssignment::toDomain)
        .toList();
  }

  @Override
  public List<FulfilmentAssignment> listByStoreAndProduct(Long storeId, Long productId) {
    return find("storeId = ?1 and productId = ?2", Sort.by("id"), storeId, productId).stream()
        .map(DbFulfilmentAssignment::toDomain)
        .toList();
  }

  @Override
  public Set<String> distinctWarehousesForStoreAndProduct(Long storeId, Long productId) {
    return new HashSet<>(
        getEntityManager()
            .createQuery(
                "select distinct f.warehouseBusinessUnitCode from DbFulfilmentAssignment f "
                    + "where f.storeId = :storeId and f.productId = :productId",
                String.class)
            .setParameter("storeId", storeId)
            .setParameter("productId", productId)
            .getResultList());
  }

  @Override
  public Set<String> distinctWarehousesForStore(Long storeId) {
    return new HashSet<>(
        getEntityManager()
            .createQuery(
                "select distinct f.warehouseBusinessUnitCode from DbFulfilmentAssignment f "
                    + "where f.storeId = :storeId",
                String.class)
            .setParameter("storeId", storeId)
            .getResultList());
  }

  @Override
  public Set<Long> distinctProductsForWarehouse(String warehouseBusinessUnitCode) {
    return new HashSet<>(
        getEntityManager()
            .createQuery(
                "select distinct f.productId from DbFulfilmentAssignment f "
                    + "where f.warehouseBusinessUnitCode = :code",
                Long.class)
            .setParameter("code", warehouseBusinessUnitCode)
            .getResultList());
  }

  @Override
  public void acquireAssignmentLocks(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    lockAdvisory(LOCK_CLASS_STORE, String.valueOf(storeId));
    lockAdvisory(LOCK_CLASS_STORE_PRODUCT, storeId + ":" + productId);
    lockAdvisory(LOCK_CLASS_WAREHOUSE, warehouseBusinessUnitCode);
  }

  private void lockAdvisory(int lockClass, String key) {
    getEntityManager()
        .createNativeQuery("SELECT pg_advisory_xact_lock(?1, hashtext(?2))")
        .setParameter(1, lockClass)
        .setParameter(2, key)
        .getSingleResult();
  }
}
