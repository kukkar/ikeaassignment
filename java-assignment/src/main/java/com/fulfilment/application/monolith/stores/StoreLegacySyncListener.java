package com.fulfilment.application.monolith.stores;

import com.fulfilment.application.monolith.stores.events.StoreCreatedEvent;
import com.fulfilment.application.monolith.stores.events.StoreUpdatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Bridges committed {@code Store} changes to the legacy system.
 *
 * <p>Observers are registered with {@link TransactionPhase#AFTER_SUCCESS}, so they only run once
 * the transaction that produced the event has committed - never before, and never on rollback.
 * Because the database change is already durable by the time these methods run, a downstream
 * failure here cannot be rolled back; it is logged so it stays observable/alertable instead of
 * being silently swallowed. See the "Store after-commit integration" note in README.md for why a
 * transactional outbox is the production-grade evolution of this approach (guaranteed delivery,
 * retries, idempotency, crash recovery).
 */
@ApplicationScoped
public class StoreLegacySyncListener {

  private static final Logger LOGGER = Logger.getLogger(StoreLegacySyncListener.class);

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  void onStoreCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreCreatedEvent event) {
    try {
      legacyStoreManagerGateway.createStoreOnLegacySystem(toStoreSnapshot(event));
    } catch (Exception e) {
      LOGGER.errorf(
          e, "Failed to propagate creation of store id=%d to the legacy system", event.id());
    }
  }

  void onStoreUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreUpdatedEvent event) {
    try {
      legacyStoreManagerGateway.updateStoreOnLegacySystem(toStoreSnapshot(event));
    } catch (Exception e) {
      LOGGER.errorf(
          e, "Failed to propagate update of store id=%d to the legacy system", event.id());
    }
  }

  private static Store toStoreSnapshot(StoreCreatedEvent event) {
    Store snapshot = new Store(event.name());
    snapshot.id = event.id();
    snapshot.quantityProductsInStock = event.quantityProductsInStock();
    return snapshot;
  }

  private static Store toStoreSnapshot(StoreUpdatedEvent event) {
    Store snapshot = new Store(event.name());
    snapshot.id = event.id();
    snapshot.quantityProductsInStock = event.quantityProductsInStock();
    return snapshot;
  }
}
