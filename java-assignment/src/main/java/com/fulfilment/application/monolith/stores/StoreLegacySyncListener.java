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
 * <p>Observers run at {@link TransactionPhase#AFTER_SUCCESS}: only once the transaction that
 * produced the event has committed, never before, never on rollback. The write is already durable
 * by then, so a downstream failure here can't be rolled back - it's logged, not swallowed. See
 * README.md ("Store after-commit mechanism") for why a transactional outbox is the production-grade
 * evolution of this.
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
