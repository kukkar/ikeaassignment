package com.fulfilment.application.monolith.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real endpoints against the real Postgres instance (Dev Services), not the check
 * classes directly - the point is proving the checks are correctly registered and wired, not
 * re-testing MicroProfile Health itself.
 */
@QuarkusTest
public class HealthCheckEndpointTest {

  @Test
  public void testOverallHealthIsUpAndListsBothChecks() {
    given()
        .when()
        .get("/q/health")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"))
        .body("checks.name", hasItem("database-connection"))
        .body("checks.name", hasItem("heap-memory"));
  }

  @Test
  public void testLivenessReportsUpWithHeapData() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"))
        .body("checks[0].name", equalTo("heap-memory"))
        .body("checks[0].status", equalTo("UP"))
        .body("checks[0].data.freeHeapRatio", notNullValue());
  }

  @Test
  public void testReadinessReportsUpWhenDatabaseIsReachable() {
    // Quarkus also auto-registers its own Agroal datasource check on this same endpoint, so this
    // asserts our named check is present and UP rather than assuming array position.
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"))
        .body("checks.name", hasItem("database-connection"))
        .body("checks.find { it.name == 'database-connection' }.status", equalTo("UP"));
  }
}
