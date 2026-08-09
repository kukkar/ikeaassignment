package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Runtime contract-<b>shape</b> test for the standalone, hand-maintained {@code
 * fulfilment-assignment-openapi.yaml} (not wired into {@code quarkus.openapi.generator.spec} -
 * see README.md "API"). Each assertion below is a literal transcription of that YAML's schemas,
 * checked against the real running server.
 *
 * <p><b>What this does not do:</b> it does not parse or load the YAML file - it does not detect
 * drift the way a real contract test would. Editing the YAML without updating this file (or vice
 * versa) will not fail here; both sides are maintained by hand and can silently diverge. This is
 * useful endpoint-shape coverage, not a drift guarantee - see README.md ("Preventing drift") for
 * why that gap is accepted for this assignment rather than adding schema-parsing/validation.
 */
@QuarkusTest
public class FulfilmentAssignmentContractShapeTest {

  private static final String PATH = "/stores/1/fulfilment-assignments";

  @Test
  public void testCreatedResponseMatchesTheDocumentedFulfilmentAssignmentSchema() {
    int id =
        given()
            .contentType("application/json")
            .body(Map.of("productId", 1, "warehouseBusinessUnitCode", "MWH.001"))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            // Every property the "FulfilmentAssignment" schema marks required.
            .body("$", hasKey("id"))
            .body("$", hasKey("storeId"))
            .body("$", hasKey("productId"))
            .body("$", hasKey("warehouseBusinessUnitCode"))
            .body("$", hasKey("warehouseActive"))
            .body("$", hasKey("createdAt"))
            .body("id", instanceOf(Number.class))
            .body("storeId", equalTo(1))
            .body("productId", equalTo(1))
            .body("warehouseBusinessUnitCode", equalTo("MWH.001"))
            .body("warehouseActive", equalTo(true))
            .body("createdAt", notNullValue())
            .extract()
            .path("id");

    try {
      given()
          .when()
          .get(PATH)
          .then()
          .statusCode(200)
          .body("id", everyItem(instanceOf(Number.class)))
          .body("warehouseActive", everyItem(instanceOf(Boolean.class)));
    } finally {
      given().when().delete(PATH + "/" + id);
    }
  }

  @Test
  public void testValidationErrorResponseMatchesTheDocumentedApiErrorSchemaWithDetails() {
    // The YAML's 400 example: missing productId and warehouseBusinessUnitCode.
    given()
        .contentType("application/json")
        .body(Map.of())
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body("$", hasKey("code"))
        .body("$", hasKey("message"))
        .body("code", equalTo("VALIDATION"))
        .body("message", notNullValue())
        .body("details", notNullValue())
        .body("details.productId", notNullValue())
        .body("details.warehouseBusinessUnitCode", notNullValue());
  }

  @Test
  public void testNotFoundErrorResponseMatchesTheDocumentedApiErrorSchema() {
    given()
        .contentType("application/json")
        .body(Map.of("productId", 1, "warehouseBusinessUnitCode", "NOT-A-REAL-CODE"))
        .when()
        .post(PATH)
        .then()
        .statusCode(404)
        .body("$", hasKey("code"))
        .body("$", hasKey("message"))
        .body("code", equalTo("NOT_FOUND"))
        .body("message", notNullValue());
  }

  @Test
  public void testConflictErrorResponseMatchesTheDocumentedApiErrorSchema() {
    given()
        .contentType("application/json")
        .body(Map.of("productId", 1, "warehouseBusinessUnitCode", "MWH.001"))
        .when()
        .post(PATH)
        .then()
        .statusCode(201);

    try {
      given()
          .contentType("application/json")
          .body(Map.of("productId", 1, "warehouseBusinessUnitCode", "MWH.001"))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("$", hasKey("code"))
          .body("$", hasKey("message"))
          .body("code", equalTo("CONFLICT"))
          .body("message", notNullValue());
    } finally {
      String assignmentId =
          given().when().get(PATH).then().extract().jsonPath().getString("find { it.productId == 1 }.id");
      given().when().delete(PATH + "/" + assignmentId);
    }
  }

  @Test
  public void testDeleteOfUnknownAssignmentMatchesTheDocumented404() {
    given()
        .when()
        .delete(PATH + "/999999999")
        .then()
        .statusCode(404)
        .body("$", hasKey("code"))
        .body("$", hasKey("message"))
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  public void testDeleteOfAnExistingAssignmentMatchesTheDocumented204WithNoBody() {
    String id =
        given()
            .contentType("application/json")
            .body(Map.of("productId", 1, "warehouseBusinessUnitCode", "MWH.001"))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id")
            .toString();

    given().when().delete(PATH + "/" + id).then().statusCode(204).body(equalTo(""));
  }
}
