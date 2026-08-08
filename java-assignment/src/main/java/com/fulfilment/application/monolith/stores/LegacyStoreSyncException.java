package com.fulfilment.application.monolith.stores;

/** Raised when propagating a {@code Store} change to the legacy system fails. */
public class LegacyStoreSyncException extends RuntimeException {

  public LegacyStoreSyncException(String message, Throwable cause) {
    super(message, cause);
  }
}
