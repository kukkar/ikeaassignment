package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

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
}
