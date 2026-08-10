package com.fulfilment.application.monolith.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports DOWN when the configured PostgreSQL datasource cannot actually be reached, rather than
 * a static UP regardless of dependencies - readiness is meant to gate whether traffic should be
 * routed here, and every request handler in this application needs the database.
 */
@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {

  @Inject EntityManager entityManager;

  @Override
  public HealthCheckResponse call() {
    try {
      entityManager.createNativeQuery("SELECT 1").getSingleResult();
      return HealthCheckResponse.up("database-connection");
    } catch (PersistenceException e) {
      return HealthCheckResponse.named("database-connection")
          .down()
          .withData("reason", String.valueOf(e.getMessage()))
          .build();
    }
  }
}
