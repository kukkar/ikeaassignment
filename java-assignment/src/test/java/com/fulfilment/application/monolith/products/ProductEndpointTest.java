package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProductEndpointTest {

  /**
   * Deletes a scratch Product it creates itself, not the seeded TONSTAD/KALLAX/BESTÅ rows -
   * {@code @QuarkusTest} classes share committed database state across the whole Surefire JVM (no
   * per-test transaction rollback here), so permanently deleting a seeded row would make every
   * other test that assumes it exists (several bonus fulfilment-assignment tests hardcode product
   * id 1) depend on this class happening to run after them, which Surefire's test order does not
   * guarantee.
   */
  @Test
  public void testCrudProduct() {
    final String path = "product";

    // List all, should have all 3 seeded products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("TONSTAD"), containsString("KALLAX"), containsString("BESTÅ"));

    long scratchId =
        given()
            .contentType("application/json")
            .body(Map.of("name", "PRODUCT-ENDPOINT-TEST-SCRATCH"))
            .when()
            .post(path)
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("id");

    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("PRODUCT-ENDPOINT-TEST-SCRATCH"));

    // Delete the scratch product, not a seeded one:
    given().when().delete(path + "/" + scratchId).then().statusCode(204);

    // List all: the scratch product is gone, every seeded product is still present.
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(
            not(containsString("PRODUCT-ENDPOINT-TEST-SCRATCH")),
            containsString("TONSTAD"),
            containsString("KALLAX"),
            containsString("BESTÅ"));
  }

  @Test
  public void testGetSingleProductReturnsTheProduct() {
    given().when().get("product/1").then().statusCode(200).body("name", equalTo("TONSTAD"));
  }

  @Test
  public void testGetSingleProductThatDoesNotExistReturns404() {
    given().when().get("product/999999999").then().statusCode(404);
  }

  @Test
  public void testCreateProductWithIdSetIsRejected() {
    given()
        .contentType("application/json")
        .body(Map.of("id", 999999999, "name", "SHOULD-NOT-BE-CREATED"))
        .when()
        .post("product")
        .then()
        .statusCode(422);
  }

  @Test
  public void testUpdateProductAppliesEveryField() {
    long scratchId =
        given()
            .contentType("application/json")
            .body(Map.of("name", "PRODUCT-UPDATE-TEST"))
            .when()
            .post("product")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("id");

    try {
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "name", "PRODUCT-UPDATE-TEST-RENAMED",
                  "description", "a description",
                  "price", 19.99,
                  "stock", 7))
          .when()
          .put("product/" + scratchId)
          .then()
          .statusCode(200)
          .body("name", equalTo("PRODUCT-UPDATE-TEST-RENAMED"))
          .body("description", equalTo("a description"))
          .body("price", equalTo(19.99f))
          .body("stock", equalTo(7));
    } finally {
      given().when().delete("product/" + scratchId);
    }
  }

  @Test
  public void testUpdateProductWithoutNameIsRejected() {
    given()
        .contentType("application/json")
        .body(Map.of("description", "no name supplied"))
        .when()
        .put("product/1")
        .then()
        .statusCode(422);
  }

  @Test
  public void testUpdateProductThatDoesNotExistReturns404() {
    given()
        .contentType("application/json")
        .body(Map.of("name", "DOES-NOT-EXIST"))
        .when()
        .put("product/999999999")
        .then()
        .statusCode(404);
  }
}
