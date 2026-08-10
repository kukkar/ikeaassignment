package com.fulfilment.application.monolith.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Reports DOWN once free heap drops below {@link #MIN_FREE_HEAP_RATIO} of the JVM's max heap -
 * liveness should stay cheap and dependency-free (unlike readiness, it must not call out to the
 * database: a struggling external dependency should make this instance not-ready, not trigger a
 * pointless restart), but it should still reflect a real, checkable condition rather than always
 * returning UP regardless of process health.
 */
@Liveness
@ApplicationScoped
public class MemoryLivenessCheck implements HealthCheck {

  private static final double MIN_FREE_HEAP_RATIO = 0.05;

  @Override
  public HealthCheckResponse call() {
    Runtime runtime = Runtime.getRuntime();
    long max = runtime.maxMemory();
    long used = runtime.totalMemory() - runtime.freeMemory();
    double freeRatio = max == 0 ? 1.0 : 1.0 - ((double) used / max);

    var builder =
        HealthCheckResponse.named("heap-memory")
            .withData("freeHeapRatio", String.format("%.3f", freeRatio));

    return freeRatio >= MIN_FREE_HEAP_RATIO ? builder.up().build() : builder.down().build();
  }
}
