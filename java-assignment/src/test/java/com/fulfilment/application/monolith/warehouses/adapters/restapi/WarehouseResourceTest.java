package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Every test cleans up any warehouse it leaves active (by archiving it directly through the
 * repository) so location count/capacity limits stay predictable regardless of test execution
 * order - {@code @QuarkusTest} does not roll back the database between test methods the way
 * {@code @TestTransaction} does for single-transaction tests.
 */
@QuarkusTest
public class WarehouseResourceTest {

  private static final String PATH = "warehouse";

  @Inject WarehouseRepository warehouseRepository;

  private Map<String, Object> warehousePayload(String code, String location, int capacity, int stock) {
    return Map.of(
        "businessUnitCode", code,
        "location", location,
        "capacity", capacity,
        "stock", stock);
  }

  private void archiveDirectly(String businessUnitCode) {
    QuarkusTransaction.requiringNew()
        .run(() -> warehouseRepository.archiveActive(businessUnitCode, LocalDateTime.now()));
  }

  @Test
  public void testListActiveWarehousesHappyPath() {
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("businessUnitCode", hasSize(greaterThanOrEqualTo(3)))
        .body("find { it.businessUnitCode == 'MWH.001' }.location", equalTo("ZWOLLE-001"));
  }

  @Test
  public void testCreateWarehouseHappyPathReturns201() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.CREATE.1", "AMSTERDAM-002", 30, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(201)
          .body("businessUnitCode", equalTo("TEST.CREATE.1"))
          .body("location", equalTo("AMSTERDAM-002"))
          .body("capacity", equalTo(30))
          .body("stock", equalTo(10))
          .body("createdAt", notNullValue())
          .body("archivedAt", nullValue());
    } finally {
      archiveDirectly("TEST.CREATE.1");
    }
  }

  @Test
  public void testCreateWithDuplicateActiveCodeReturns409() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.DUP.1", "AMSTERDAM-002", 10, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.DUP.1", "AMSTERDAM-002", 10, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      archiveDirectly("TEST.DUP.1");
    }
  }

  @Test
  public void testCreateWithUnknownLocationReturns404() {
    given()
        .contentType("application/json")
        .body(warehousePayload("TEST.NOLOC.1", "NARNIA-001", 10, 1))
        .when()
        .post(PATH)
        .then()
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testCreateWithInvalidDataReturns400WithDetails() {
    given()
        .contentType("application/json")
        .body(warehousePayload("", "AMSTERDAM-002", -5, -1))
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body("code", equalTo("VALIDATION"))
        .body("details.businessUnitCode", notNullValue())
        .body("details.capacity", notNullValue())
        .body("details.stock", notNullValue());
  }

  @Test
  public void testCreateExceedingLocationWarehouseCountLimitReturns409() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.COUNT.1", "ZWOLLE-002", 5, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.COUNT.2", "ZWOLLE-002", 5, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      // ZWOLLE-002 allows a maximum of 2 warehouses (see LocationGateway)
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.COUNT.3", "ZWOLLE-002", 5, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      archiveDirectly("TEST.COUNT.1");
      archiveDirectly("TEST.COUNT.2");
    }
  }

  @Test
  public void testCreateExceedingLocationAggregateCapacityReturns409() {
    try {
      // EINDHOVEN-001 allows 2 warehouses and 70 aggregate capacity
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.CAP.1", "EINDHOVEN-001", 50, 1))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.CAP.2", "EINDHOVEN-001", 30, 1)) // 50 + 30 = 80 > 70
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));
    } finally {
      archiveDirectly("TEST.CAP.1");
    }
  }

  @Test
  public void testGetUnknownWarehouseReturns404() {
    given().when().get(PATH + "/DOES-NOT-EXIST").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testGetActiveWarehouseHappyPath() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.GET.1", "AMSTERDAM-002", 10, 2))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .when()
          .get(PATH + "/TEST.GET.1")
          .then()
          .statusCode(200)
          .body("businessUnitCode", equalTo("TEST.GET.1"))
          .body("location", equalTo("AMSTERDAM-002"));
    } finally {
      archiveDirectly("TEST.GET.1");
    }
  }

  @Test
  public void testArchiveWarehouseHappyPathAndRepeatedArchiveReturns404() {
    given()
        .contentType("application/json")
        .body(warehousePayload("TEST.ARCHIVE.1", "AMSTERDAM-002", 10, 2))
        .when()
        .post(PATH)
        .then()
        .statusCode(201);

    given().when().delete(PATH + "/TEST.ARCHIVE.1").then().statusCode(204);

    given().when().get(PATH + "/TEST.ARCHIVE.1").then().statusCode(404);

    // repeated archive of an already-archived warehouse: no active row exists anymore
    given().when().delete(PATH + "/TEST.ARCHIVE.1").then().statusCode(404);
  }

  @Test
  public void testReplaceWarehouseHappyPathArchivesOldAndCreatesNewActiveRow() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.REPL.1", "AMSTERDAM-002", 20, 8))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.REPL.1", "AMSTERDAM-002", 25, 8))
          .when()
          .post(PATH + "/TEST.REPL.1/replacement")
          .then()
          .statusCode(200)
          .body("businessUnitCode", equalTo("TEST.REPL.1"))
          .body("capacity", equalTo(25))
          .body("stock", equalTo(8))
          .body("archivedAt", nullValue());

      assertEquals(2, warehouseRepository.count("businessUnitCode", "TEST.REPL.1"));
      var active = warehouseRepository.findActiveByBusinessUnitCode("TEST.REPL.1");
      assertNotNull(active);
      assertEquals(25, active.capacity);
    } finally {
      archiveDirectly("TEST.REPL.1");
    }
  }

  @Test
  public void testReplaceWithStockMismatchReturns409AndLeavesOldWarehouseActive() {
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.REPL.MISMATCH", "AMSTERDAM-002", 20, 8))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      given()
          .contentType("application/json")
          .body(warehousePayload("TEST.REPL.MISMATCH", "AMSTERDAM-002", 20, 999))
          .when()
          .post(PATH + "/TEST.REPL.MISMATCH/replacement")
          .then()
          .statusCode(409)
          .body("code", equalTo("CONFLICT"));

      var active = warehouseRepository.findActiveByBusinessUnitCode("TEST.REPL.MISMATCH");
      assertNotNull(active, "the original warehouse must remain active after a failed replacement");
      assertNull(active.archivedAt);
      assertEquals(1, warehouseRepository.count("businessUnitCode", "TEST.REPL.MISMATCH"));
    } finally {
      archiveDirectly("TEST.REPL.MISMATCH");
    }
  }

  @Test
  public void testReplaceUnknownWarehouseReturns404() {
    given()
        .contentType("application/json")
        .body(warehousePayload("TEST.REPL.MISSING", "AMSTERDAM-002", 10, 1))
        .when()
        .post(PATH + "/TEST.REPL.MISSING/replacement")
        .then()
        .statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  /**
   * Fires two replacement requests at the same warehouse at (as close to) the same time. The test
   * client cannot force the two HTTP requests' database transactions to truly overlap - depending
   * on scheduling, the second request's transaction may only start once the first has already
   * committed, in which case both legitimately succeed one after another (the second replaces
   * whatever the first just made active, which is entirely valid). What must hold regardless of
   * that timing is the safety invariant: the number of rows ever created for this business unit
   * code equals 1 (the original) plus however many requests actually succeeded, and exactly one
   * row stays active - never a lost update, never two active rows, never a phantom extra row.
   */
  @Test
  public void testConcurrentReplacementsOnTheSameWarehouseNeverCorruptState() throws Exception {
    String code = "TEST.REPL.CONCURRENT";
    try {
      given()
          .contentType("application/json")
          .body(warehousePayload(code, "AMSTERDAM-002", 20, 5))
          .when()
          .post(PATH)
          .then()
          .statusCode(201);

      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch startLatch = new CountDownLatch(1);
      AtomicInteger successCount = new AtomicInteger();
      AtomicInteger failureCount = new AtomicInteger();

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
                    .body(warehousePayload(code, "AMSTERDAM-002", 20, 5))
                    .when()
                    .post(PATH + "/" + code + "/replacement")
                    .then()
                    .extract()
                    .statusCode();
            if (status == 200) {
              successCount.incrementAndGet();
            } else {
              failureCount.incrementAndGet();
            }
          };

      Future<?> f1 = executor.submit(attempt);
      Future<?> f2 = executor.submit(attempt);
      startLatch.countDown();
      f1.get(30, TimeUnit.SECONDS);
      f2.get(30, TimeUnit.SECONDS);
      executor.shutdown();

      assertEquals(2, successCount.get() + failureCount.get());
      assertTrue(successCount.get() >= 1, "at least one of the two requests must succeed");
      assertEquals(
          1L + successCount.get(),
          warehouseRepository.count("businessUnitCode", code),
          "row count must be exactly the original plus one per successful replacement - no lost"
              + " updates, no phantom rows");
      var active = warehouseRepository.findActiveByBusinessUnitCode(code);
      assertNotNull(active, "exactly one active row must remain for this business unit code");
    } finally {
      archiveDirectly(code);
    }
  }
}
