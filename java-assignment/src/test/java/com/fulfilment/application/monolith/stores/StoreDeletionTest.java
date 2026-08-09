package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fulfilment.application.monolith.fulfilment.adapters.database.FulfilmentAssignmentRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Problem 4: {@code fulfilment_assignment} has a foreign key to {@code store(id)} with no {@code
 * ON DELETE} clause. Deleting a Store that still has assignments referencing it must not surface a
 * raw constraint-violation 500 - it must return a clean 409, and the failed attempt must destroy
 * neither the Store nor the assignment (no accidental partial/cascading deletion).
 *
 * <p>Cleanup calls in {@code finally} blocks below are best-effort and unasserted - by the time
 * they run, the target may already have been deleted by the test body itself, in which case they
 * just get a harmless 404.
 */
@QuarkusTest
public class StoreDeletionTest {

  @Inject FulfilmentAssignmentRepository fulfilmentAssignmentRepository;
  @Inject WarehouseRepository warehouseRepository;

  @Test
  public void testDeletingAStoreWithAssignmentsReturns409AndDeletesNothing() {
    long storeId = createStore("FUL-DEL-STORE");
    // A dedicated Product, not the seeded product id 1 - ProductEndpointTest's own CRUD test
    // permanently deletes product id 1 as part of its documented behaviour, and @QuarkusTest
    // classes share committed database state (no transaction rollback), so this test's outcome
    // must not depend on Surefire's unspecified test-class execution order.
    long productId = createProduct("FUL-DEL-STORE-PRODUCT");
    createWarehouse("FUL.DEL.STORE.WH", "HELMOND-001", 10, 1);
    long assignmentId = createAssignment(storeId, productId, "FUL.DEL.STORE.WH");
    try {
      given().when().delete("/store/" + storeId).then().statusCode(409);

      // Neither the store nor the assignment was destroyed by the failed attempt.
      given().when().get("/store/" + storeId).then().statusCode(200);
      assertEquals(1, fulfilmentAssignmentRepository.listByStore(storeId).size());

      // Removing the assignment first allows the store to be deleted normally afterward.
      given()
          .when()
          .delete("/stores/" + storeId + "/fulfilment-assignments/" + assignmentId)
          .then()
          .statusCode(204);
      given().when().delete("/store/" + storeId).then().statusCode(204);
    } finally {
      given().when().delete("/stores/" + storeId + "/fulfilment-assignments/" + assignmentId);
      given().when().delete("/store/" + storeId);
      given().when().delete("/product/" + productId);
      archiveWarehouse("FUL.DEL.STORE.WH");
    }
  }

  @Test
  public void testDeletingAStoreWithoutAssignmentsStillSucceeds() {
    long storeId = createStore("FUL-DEL-STORE-EMPTY");

    given().when().delete("/store/" + storeId).then().statusCode(204);
  }

  private long createStore(String name) {
    return given()
        .contentType("application/json")
        .body(Map.of("name", name, "quantityProductsInStock", 0))
        .when()
        .post("/store")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
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

  private void createWarehouse(String code, String location, int capacity, int stock) {
    given()
        .contentType("application/json")
        .body(Map.of("businessUnitCode", code, "location", location, "capacity", capacity, "stock", stock))
        .when()
        .post("/warehouse")
        .then()
        .statusCode(201);
  }

  private long createAssignment(long storeId, long productId, String warehouseCode) {
    return given()
        .contentType("application/json")
        .body(Map.of("productId", productId, "warehouseBusinessUnitCode", warehouseCode))
        .when()
        .post("/stores/" + storeId + "/fulfilment-assignments")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  private void archiveWarehouse(String code) {
    QuarkusTransaction.requiringNew()
        .run(() -> warehouseRepository.archiveActive(code, LocalDateTime.now()));
  }
}
