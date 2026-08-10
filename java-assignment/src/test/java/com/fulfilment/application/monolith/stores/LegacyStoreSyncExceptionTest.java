package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * {@link LegacyStoreManagerGateway} only throws this when the simulated legacy-system write fails
 * (a real filesystem I/O failure) - not practically triggerable through a normal test, so its
 * message/cause plumbing is verified directly here instead.
 */
public class LegacyStoreSyncExceptionTest {

  @Test
  void testMessageAndCauseAreCarried() {
    Throwable cause = new java.io.IOException("disk full");

    LegacyStoreSyncException exception = new LegacyStoreSyncException("Failed to synchronize store 'X'", cause);

    assertEquals("Failed to synchronize store 'X'", exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
