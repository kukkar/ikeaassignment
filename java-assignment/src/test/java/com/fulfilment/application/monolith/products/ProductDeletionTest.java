package com.fulfilment.application.monolith.products;

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
 * Problem 4: {@code fulfilment_assignment} has a foreign key to {@code product(id)} with no {@code
 * ON DELETE} clause. Deleting a Product that still has assignments referencing it must not surface
 * a raw constraint-violation 500 - it must return a clean 409, and the failed attempt must destroy
 * neither the Product nor the assignment. Mirrors {@code StoreDeletionTest} exactly, since the two
 * resources must behave consistently (same FK shape, same policy).
 *
 * <p>Cleanup calls in {@code finally} blocks below are best-effort and unasserted.
 */
@QuarkusTest
public class ProductDeletionTest {

  @Inject FulfilmentAssignmentRepository fulfilmentAssignmentRepository;
  @Inject WarehouseRepository warehouseRepository;

  @Test
  public void testDeletingAProductWithAssignmentsReturns409AndDeletesNothing() {
    long productId = createProduct("FUL-DEL-PRODUCT");
    createWarehouse("FUL.DEL.PRODUCT.WH", "EINDHOVEN-001", 10, 1);
    // Store 1 is seeded and always present - only this test's own product/warehouse/assignment
    // are new, so reusing it doesn't risk interfering with other tests' assumptions.
    long assignmentId = createAssignment(1L, productId, "FUL.DEL.PRODUCT.WH");
    try {
      given().when().delete("/product/" + productId).then().statusCode(409);

      given().when().get("/product/" + productId).then().statusCode(200);
      assertEquals(1, fulfilmentAssignmentRepository.listByStoreAndProduct(1L, productId).size());

      given()
          .when()
          .delete("/stores/1/fulfilment-assignments/" + assignmentId)
          .then()
          .statusCode(204);
      given().when().delete("/product/" + productId).then().statusCode(204);
    } finally {
      given().when().delete("/stores/1/fulfilment-assignments/" + assignmentId);
      given().when().delete("/product/" + productId);
      archiveWarehouse("FUL.DEL.PRODUCT.WH");
    }
  }

  @Test
  public void testDeletingAProductWithoutAssignmentsStillSucceeds() {
    long productId = createProduct("FUL-DEL-PRODUCT-EMPTY");

    given().when().delete("/product/" + productId).then().statusCode(204);
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
