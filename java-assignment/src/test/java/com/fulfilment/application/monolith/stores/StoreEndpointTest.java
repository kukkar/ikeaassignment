package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level CRUD/validation coverage for {@code StoreResource} that {@code
 * StoreResourceAfterCommitTest} (calls the Java methods directly, to control transaction
 * boundaries) and {@code StoreDeletionTest} (delete only) don't exercise: the 422/404 validation
 * guards on create/update/patch/get, reached only through the real JAX-RS layer.
 */
@QuarkusTest
public class StoreEndpointTest {

  @Test
  public void testGetSingleStoreThatDoesNotExistReturns404() {
    given().when().get("store/999999999").then().statusCode(404);
  }

  @Test
  public void testCreateStoreWithIdSetIsRejected() {
    given()
        .contentType("application/json")
        .body(Map.of("id", 999999999, "name", "SHOULD-NOT-BE-CREATED"))
        .when()
        .post("store")
        .then()
        .statusCode(422);
  }

  @Test
  public void testUpdateStoreHappyPathAppliesEveryField() {
    long storeId = createStore("STORE-UPDATE-TEST");

    try {
      given()
          .contentType("application/json")
          .body(Map.of("name", "STORE-UPDATE-TEST-RENAMED", "quantityProductsInStock", 42))
          .when()
          .put("store/" + storeId)
          .then()
          .statusCode(200)
          .body("name", equalTo("STORE-UPDATE-TEST-RENAMED"))
          .body("quantityProductsInStock", equalTo(42));
    } finally {
      given().when().delete("store/" + storeId);
    }
  }

  @Test
  public void testUpdateStoreWithoutNameIsRejected() {
    long storeId = createStore("STORE-UPDATE-VALIDATION-TEST");

    try {
      given()
          .contentType("application/json")
          .body(Map.of("quantityProductsInStock", 5))
          .when()
          .put("store/" + storeId)
          .then()
          .statusCode(422);
    } finally {
      given().when().delete("store/" + storeId);
    }
  }

  @Test
  public void testUpdateStoreThatDoesNotExistReturns404() {
    given()
        .contentType("application/json")
        .body(Map.of("name", "DOES-NOT-EXIST"))
        .when()
        .put("store/999999999")
        .then()
        .statusCode(404);
  }

  @Test
  public void testPatchStoreThatDoesNotExistReturns404() {
    given()
        .contentType("application/json")
        .body(Map.of("name", "DOES-NOT-EXIST"))
        .when()
        .patch("store/999999999")
        .then()
        .statusCode(404);
  }

  private long createStore(String name) {
    return given()
        .contentType("application/json")
        .body(Map.of("name", name, "quantityProductsInStock", 0))
        .when()
        .post("store")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }
}
