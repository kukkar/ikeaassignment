package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.fulfilment.adapters.database.FulfilmentAssignmentRepository;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Every test cleans up the assignments (and any scratch warehouses) it creates, for the same
 * reason {@code WarehouseResourceTest} does: {@code @QuarkusTest} does not roll back the database
 * between test methods, and the fulfilment limits are stateful across a shared store/warehouse.
 */
@QuarkusTest
public class FulfilmentAssignmentResourceTest {

  @Inject FulfilmentAssignmentRepository fulfilmentAssignmentRepository;
  @Inject WarehouseRepository warehouseRepository;

  private static Map<String, Object> payload(Long productId, String warehouseCode) {
    return Map.of("productId", productId, "warehouseBusinessUnitCode", warehouseCode);
  }

  private static String path(Long storeId) {
    return "/stores/" + storeId + "/fulfilment-assignments";
  }

  private void deleteAssignmentsFor(Long storeId, Long productId) {
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                fulfilmentAssignmentRepository
                    .listByStoreAndProduct(storeId, productId)
                    .forEach(a -> fulfilmentAssignmentRepository.deleteByIdForStore(a.id, storeId)));
  }

  private void createWarehouse(String code, String location, int capacity, int stock) {
    given()
        .contentType("application/json")
        .body(Map.of("businessUnitCode", code, "location", location, "capacity", capacity, "stock", stock))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(201);
  }

  private void archiveWarehouse(String code) {
    QuarkusTransaction.requiringNew()
        .run(() -> warehouseRepository.archiveActive(code, LocalDateTime.now()));
  }

  @Test
  public void testCreateAssignmentHappyPathReturns201() {
    try {
      given()
          .contentType("application/json")
          .body(payload(1L, "MWH.001"))
          .when()
          .post(path(1L))
          .then()
          .statusCode(201)
          .body("storeId", equalTo(1))
          .body("productId", equalTo(1))
          .body("warehouseBusinessUnitCode", equalTo("MWH.001"))
          .body("warehouseActive", equalTo(true))
          .body("id", notNullValue())
          .body("createdAt", notNullValue());
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  @Test
  public void testListForStoreReturnsStructuredJson() {
    try {
      given().contentType("application/json").body(payload(1L, "MWH.001")).when().post(path(1L)).then().statusCode(201);
      given().contentType("application/json").body(payload(1L, "MWH.012")).when().post(path(1L)).then().statusCode(201);

      given()
          .when()
          .get(path(1L))
          .then()
          .statusCode(200)
          .body("$", hasSize(2))
          .body("warehouseBusinessUnitCode", org.hamcrest.Matchers.containsInAnyOrder("MWH.001", "MWH.012"));
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  @Test
  public void testListFilteredByProductId() {
    try {
      given().contentType("application/json").body(payload(1L, "MWH.001")).when().post(path(1L)).then().statusCode(201);
      given().contentType("application/json").body(payload(2L, "MWH.001")).when().post(path(1L)).then().statusCode(201);

      given()
          .when()
          .get(path(1L) + "?productId=1")
          .then()
          .statusCode(200)
          .body("$", hasSize(1))
          .body("[0].productId", equalTo(1));
    } finally {
      deleteAssignmentsFor(1L, 1L);
      deleteAssignmentsFor(1L, 2L);
    }
  }

  @Test
  public void testDeleteReturns204() {
    int id =
        given()
            .contentType("application/json")
            .body(payload(1L, "MWH.001"))
            .when()
            .post(path(1L))
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    given().when().delete(path(1L) + "/" + id).then().statusCode(204);
    given().when().get(path(1L)).then().statusCode(200).body("$", hasSize(0));
  }

  @Test
  public void testDeleteUnknownAssignmentReturns404() {
    given().when().delete(path(1L) + "/999999").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testCreateWithMalformedDataReturns400WithDetails() {
    given()
        .contentType("application/json")
        .body(Map.of("warehouseBusinessUnitCode", ""))
        .when()
        .post(path(1L))
        .then()
        .statusCode(400)
        .body("code", equalTo("VALIDATION"))
        .body("details.productId", notNullValue())
        .body("details.warehouseBusinessUnitCode", notNullValue());
  }

  @Test
  public void testCreateForUnknownStoreReturns404() {
    given()
        .contentType("application/json")
        .body(payload(1L, "MWH.001"))
        .when()
        .post(path(999_999L))
        .then()
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testCreateForUnknownProductReturns404() {
    given()
        .contentType("application/json")
        .body(payload(999_999L, "MWH.001"))
        .when()
        .post(path(1L))
        .then()
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testCreateForUnknownWarehouseReturns404() {
    given()
        .contentType("application/json")
        .body(payload(1L, "NOT-A-WAREHOUSE"))
        .when()
        .post(path(1L))
        .then()
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testListForUnknownStoreReturns404() {
    given().when().get(path(999_999L)).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testDuplicateAssignmentReturns409() {
    try {
      given().contentType("application/json").body(payload(1L, "MWH.001")).when().post(path(1L)).then().statusCode(201);

      given()
          .contentType("application/json")
          .body(payload(1L, "MWH.001"))
          .when()
          .post(path(1L))
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  @Test
  public void testThirdDistinctWarehouseForSameProductReturns409() {
    try {
      given().contentType("application/json").body(payload(1L, "MWH.001")).when().post(path(1L)).then().statusCode(201);
      given().contentType("application/json").body(payload(1L, "MWH.012")).when().post(path(1L)).then().statusCode(201);

      given()
          .contentType("application/json")
          .body(payload(1L, "MWH.023"))
          .when()
          .post(path(1L))
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  @Test
  public void testFourthDistinctWarehouseForStoreReturns409() {
    try {
      given().contentType("application/json").body(payload(1L, "MWH.001")).when().post(path(2L)).then().statusCode(201);
      given().contentType("application/json").body(payload(2L, "MWH.012")).when().post(path(2L)).then().statusCode(201);
      given().contentType("application/json").body(payload(3L, "MWH.023")).when().post(path(2L)).then().statusCode(201);

      try {
        createWarehouse("FUL.STORE4TH", "VETSBY-001", 10, 1);

        given()
            .contentType("application/json")
            .body(payload(1L, "FUL.STORE4TH"))
            .when()
            .post(path(2L))
            .then()
            .statusCode(409)
            .body("code", equalTo("CONFLICT"));
      } finally {
        archiveWarehouse("FUL.STORE4TH");
      }
    } finally {
      deleteAssignmentsFor(2L, 1L);
      deleteAssignmentsFor(2L, 2L);
      deleteAssignmentsFor(2L, 3L);
    }
  }

  @Test
  public void testSixthDistinctProductForWarehouseReturns409() {
    createWarehouse("FUL.PRODUCT6TH", "EINDHOVEN-001", 10, 1);
    // Seed data only has 3 products; create 3 more so there are 6 distinct ids to work with.
    long extra1 = createProduct("FUL-EXTRA-1");
    long extra2 = createProduct("FUL-EXTRA-2");
    long extra3 = createProduct("FUL-EXTRA-3");
    long[] allProducts = {1L, 2L, 3L, extra1, extra2, extra3};

    try {
      // All via store 1: only one warehouse is ever used for store 1 in this test, so rules 1
      // and 2 stay well within their limits and only rule 3 (warehouse product-type limit) is
      // exercised.
      for (int i = 0; i < 5; i++) {
        given()
            .contentType("application/json")
            .body(payload(allProducts[i], "FUL.PRODUCT6TH"))
            .when()
            .post(path(1L))
            .then()
            .statusCode(201);
      }

      given()
          .contentType("application/json")
          .body(payload(allProducts[5], "FUL.PRODUCT6TH"))
          .when()
          .post(path(1L))
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      for (long productId : allProducts) {
        deleteAssignmentsFor(1L, productId);
      }
      archiveWarehouse("FUL.PRODUCT6TH");
      deleteProduct(extra1);
      deleteProduct(extra2);
      deleteProduct(extra3);
    }
  }

  /**
   * Problem 3 end-to-end proof: a store's 3 active-warehouse slots are genuinely freed once one
   * of them is archived without replacement, using real warehouses, real archiving, and real HTTP
   * calls against Postgres - not mocks.
   */
  @Test
  public void testArchivingAWarehouseFreesItsStoreSlotForAGenuinelyNewWarehouse() {
    createWarehouse("FUL.LIM.A", "HELMOND-001", 10, 1);
    createWarehouse("FUL.LIM.B", "EINDHOVEN-001", 10, 1);
    createWarehouse("FUL.LIM.C", "ZWOLLE-002", 10, 1);
    try {
      given().contentType("application/json").body(payload(1L, "FUL.LIM.A")).when().post(path(3L)).then().statusCode(201);
      given().contentType("application/json").body(payload(2L, "FUL.LIM.B")).when().post(path(3L)).then().statusCode(201);
      given().contentType("application/json").body(payload(3L, "FUL.LIM.C")).when().post(path(3L)).then().statusCode(201);

      createWarehouse("FUL.LIM.D", "AMSTERDAM-002", 10, 1);
      try {
        // At the limit: a 4th distinct active warehouse is rejected.
        given()
            .contentType("application/json")
            .body(payload(1L, "FUL.LIM.D"))
            .when()
            .post(path(3L))
            .then()
            .statusCode(409)
            .body("code", equalTo("CONFLICT"));

        // Archive one of the 3 - it stops operationally fulfilling the store...
        given().when().delete("/warehouse/FUL.LIM.C").then().statusCode(204);

        // ...the historical row is still listed, now flagged inactive...
        given()
            .when()
            .get(path(3L) + "?productId=3")
            .then()
            .statusCode(200)
            .body("[0].warehouseBusinessUnitCode", equalTo("FUL.LIM.C"))
            .body("[0].warehouseActive", equalTo(false));

        // ...and the store now has a genuine free slot for a new, distinct active warehouse.
        given()
            .contentType("application/json")
            .body(payload(1L, "FUL.LIM.D"))
            .when()
            .post(path(3L))
            .then()
            .statusCode(201);
      } finally {
        archiveWarehouse("FUL.LIM.D");
      }
    } finally {
      deleteAssignmentsFor(3L, 1L);
      deleteAssignmentsFor(3L, 2L);
      deleteAssignmentsFor(3L, 3L);
      archiveWarehouse("FUL.LIM.A");
      archiveWarehouse("FUL.LIM.B");
      archiveWarehouse("FUL.LIM.C"); // no-op if already archived above
    }
  }

  private long createProduct(String name) {
    return given()
        .contentType("application/json")
        .body(Map.of("name", name))
        .when()
        .post("/product")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  private void deleteProduct(long id) {
    given().when().delete("/product/" + id).then().statusCode(204);
  }

  @Test
  public void testWarehouseReplacementPreservesAssignmentResolution() {
    try {
      createWarehouse("FUL.REPL.1", "HELMOND-001", 10, 1);

      int assignmentId =
          given()
              .contentType("application/json")
              .body(payload(1L, "FUL.REPL.1"))
              .when()
              .post(path(1L))
              .then()
              .statusCode(201)
              .extract()
              .path("id");

      given()
          .contentType("application/json")
          .body(Map.of("businessUnitCode", "FUL.REPL.1", "location", "HELMOND-001", "capacity", 15, "stock", 1))
          .when()
          .post("/warehouse/FUL.REPL.1/replacement")
          .then()
          .statusCode(200);

      given()
          .when()
          .get(path(1L))
          .then()
          .statusCode(200)
          .body("[0].id", equalTo(assignmentId))
          .body("[0].warehouseBusinessUnitCode", equalTo("FUL.REPL.1"))
          .body("[0].warehouseActive", equalTo(true));
    } finally {
      deleteAssignmentsFor(1L, 1L);
      archiveWarehouse("FUL.REPL.1");
    }
  }

  @Test
  public void testArchivingWarehouseWithoutReplacementPreservesAssignmentButFlagsInactive() {
    try {
      createWarehouse("FUL.ARCH.1", "ZWOLLE-002", 10, 1);

      given().contentType("application/json").body(payload(1L, "FUL.ARCH.1")).when().post(path(2L)).then().statusCode(201);

      given().when().delete("/warehouse/FUL.ARCH.1").then().statusCode(204);

      given()
          .when()
          .get(path(2L))
          .then()
          .statusCode(200)
          .body("$", hasSize(1))
          .body("[0].warehouseBusinessUnitCode", equalTo("FUL.ARCH.1"))
          .body("[0].warehouseActive", equalTo(false));

      // An archived-only warehouse cannot be newly assigned.
      given()
          .contentType("application/json")
          .body(payload(2L, "FUL.ARCH.1"))
          .when()
          .post(path(2L))
          .then()
          .statusCode(404)
          .body("code", equalTo("NOT_FOUND"));
    } finally {
      deleteAssignmentsFor(2L, 1L);
    }
  }

  @Test
  public void testConcurrentIdenticalAssignmentsResultInExactlyOnePersistedRow() throws Exception {
    try {
      ExecutorService executor = Executors.newFixedThreadPool(4);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successCount = new AtomicInteger();
      AtomicInteger conflictCount = new AtomicInteger();

      Runnable attempt =
          () -> {
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            int status =
                given()
                    .contentType("application/json")
                    .body(payload(1L, "MWH.001"))
                    .when()
                    .post(path(1L))
                    .then()
                    .extract()
                    .statusCode();
            (status == 201 ? successCount : conflictCount).incrementAndGet();
          };

      var futures =
          IntStream.range(0, 4).mapToObj(i -> executor.submit(attempt)).toList();
      startLatch.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      executor.shutdown();

      assertEquals(1, successCount.get(), "exactly one of the identical concurrent requests must succeed");
      assertEquals(3, conflictCount.get());
      assertEquals(1, fulfilmentAssignmentRepository.listByStoreAndProduct(1L, 1L).size());
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  @Test
  public void testConcurrentDistinctWarehousesNeverExceedTheProductLimit() throws Exception {
    try {
      ExecutorService executor = Executors.newFixedThreadPool(3);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successCount = new AtomicInteger();
      AtomicInteger conflictCount = new AtomicInteger();
      String[] codes = {"MWH.001", "MWH.012", "MWH.023"};

      var futures =
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            try {
                              startLatch.await();
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                            int status =
                                given()
                                    .contentType("application/json")
                                    .body(payload(1L, codes[i]))
                                    .when()
                                    .post(path(1L))
                                    .then()
                                    .extract()
                                    .statusCode();
                            (status == 201 ? successCount : conflictCount).incrementAndGet();
                          }))
              .toList();
      startLatch.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      executor.shutdown();

      assertEquals(2, successCount.get(), "the rule-1 limit of 2 distinct warehouses must never be exceeded");
      assertEquals(1, conflictCount.get());
      assertTrue(fulfilmentAssignmentRepository.distinctWarehousesForStoreAndProduct(1L, 1L).size() <= 2);
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  /**
   * Smoke test at the HTTP level for the active-warehouse race fix: fires concurrent archive and
   * assign requests at the same warehouse and checks the outcome is always one of the two valid
   * combinations. This is necessarily a coarse check - HTTP-level request timing can't force true
   * transaction overlap (the two requests may simply run one after the other), and outcome
   * timestamps can't be used to infer commit order either, since {@code ArchiveWarehouseUseCase}
   * computes its {@code archivedAt} value in application code *before* the (potentially blocking)
   * update statement runs, not at actual commit time - so a timestamp comparison would not reflect
   * real database ordering. The actual mechanism - that {@code lockActiveByBusinessUnitCode}
   * genuinely blocks a concurrent {@code archiveActive} at the database level - is proven
   * deterministically, with explicit transaction control, by {@code WarehouseRepositoryTest
   * #testLockActiveByBusinessUnitCodeBlocksAConcurrentArchive}.
   */
  @Test
  public void testConcurrentArchiveAndAssignAlwaysProducesAValidOutcome() throws Exception {
    String code = "FUL.RACE.1";
    createWarehouse(code, "VETSBY-001", 10, 1);
    try {
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch startLatch = new CountDownLatch(1);

      Callable<Integer> assignAttempt =
          () -> {
            startLatch.await();
            return given()
                .contentType("application/json")
                .body(payload(1L, code))
                .when()
                .post(path(1L))
                .then()
                .extract()
                .statusCode();
          };
      Callable<Integer> archiveAttempt =
          () -> {
            startLatch.await();
            return given().when().delete("/warehouse/" + code).then().extract().statusCode();
          };

      Future<Integer> assignFuture = executor.submit(assignAttempt);
      Future<Integer> archiveFuture = executor.submit(archiveAttempt);
      startLatch.countDown();
      int assignStatus = assignFuture.get(30, TimeUnit.SECONDS);
      int archiveStatus = archiveFuture.get(30, TimeUnit.SECONDS);
      executor.shutdown();

      assertEquals(204, archiveStatus, "the warehouse must always end up archived exactly once");
      assertTrue(
          assignStatus == 201 || assignStatus == 404,
          "the assignment must either be created against a genuinely active warehouse, or be "
              + "correctly rejected as not found - never anything else, got: "
              + assignStatus);

      List<FulfilmentAssignment> assignments = fulfilmentAssignmentRepository.listByStoreAndProduct(1L, 1L);
      assertEquals(assignStatus == 201 ? 1 : 0, assignments.size());
    } finally {
      deleteAssignmentsFor(1L, 1L);
    }
  }

  /**
   * Deterministic proof for the warehouse-reactivation race, distinct from {@link
   * #testConcurrentArchiveAndAssignAlwaysProducesAValidOutcome} (archive/replace only): a real,
   * explicitly-paused reactivation of a store's own historical code must block a real concurrent
   * {@code assign()} call already evaluating that code, and once unblocked, must be correctly
   * counted - see README.md ("Concurrency: warehouse reactivation race").
   */
  @Test
  public void testReactivatingAnArchivedWarehouseCodeBlocksAConcurrentStoreLimitEvaluation() throws Exception {
    String reactivatedCode = "FUL.REACT.A";
    String otherActiveB = "FUL.REACT.B";
    String otherActiveC = "FUL.REACT.C";
    String targetD = "FUL.REACT.D";
    // One product per warehouse below, so only rule 2 (not rule 1) is exercised.
    long scratchProductId = createProduct("FUL-REACT-PRODUCT");
    try {
      createWarehouse(reactivatedCode, "AMSTERDAM-002", 10, 1);
      given().contentType("application/json").body(payload(1L, reactivatedCode)).when().post(path(1L)).then().statusCode(201);
      given().when().delete("/warehouse/" + reactivatedCode).then().statusCode(204);

      createWarehouse(otherActiveB, "EINDHOVEN-001", 10, 1);
      given().contentType("application/json").body(payload(2L, otherActiveB)).when().post(path(1L)).then().statusCode(201);

      createWarehouse(otherActiveC, "ZWOLLE-002", 10, 1);
      given().contentType("application/json").body(payload(3L, otherActiveC)).when().post(path(1L)).then().statusCode(201);

      createWarehouse(targetD, "HELMOND-001", 10, 1);

      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch lockAcquired = new CountDownLatch(1);
      CountDownLatch releaseLock = new CountDownLatch(1);
      AtomicInteger assignStatus = new AtomicInteger();

      Future<?> reactivator =
          executor.submit(
              () ->
                  QuarkusTransaction.requiringNew()
                      .run(
                          () -> {
                            warehouseRepository.lockForActivation(reactivatedCode);
                            lockAcquired.countDown();
                            try {
                              releaseLock.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                            Warehouse reactivated = new Warehouse();
                            reactivated.businessUnitCode = reactivatedCode;
                            reactivated.location = "AMSTERDAM-002";
                            reactivated.capacity = 10;
                            reactivated.stock = 1;
                            reactivated.createdAt = LocalDateTime.now();
                            reactivated.archivedAt = null;
                            warehouseRepository.create(reactivated);
                          }));

      assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "reactivator must acquire the activation lock");

      Future<?> assigner =
          executor.submit(
              () ->
                  assignStatus.set(
                      given()
                          .contentType("application/json")
                          .body(payload(scratchProductId, targetD))
                          .when()
                          .post(path(1L))
                          .then()
                          .extract()
                          .statusCode()));

      // Give assign() a real chance to run (and wrongly finish) if it is not blocked.
      Thread.sleep(500);
      assertEquals(0, assignStatus.get(), "assign() must still be blocked while the reactivation lock is held");

      releaseLock.countDown();
      reactivator.get(5, TimeUnit.SECONDS);
      assigner.get(5, TimeUnit.SECONDS);
      executor.shutdown();

      assertEquals(
          409,
          assignStatus.get(),
          "once the reactivation is visible, the store's 3-warehouse limit (reactivatedCode, B, C) "
              + "must correctly reject a 4th - not silently exceed it");
    } finally {
      deleteAssignmentsFor(1L, 1L);
      deleteAssignmentsFor(1L, 2L);
      deleteAssignmentsFor(1L, 3L);
      deleteAssignmentsFor(1L, scratchProductId);
      deleteProduct(scratchProductId);
      archiveWarehouse(reactivatedCode);
      archiveWarehouse(otherActiveB);
      archiveWarehouse(otherActiveC);
      archiveWarehouse(targetD);
    }
  }
}
