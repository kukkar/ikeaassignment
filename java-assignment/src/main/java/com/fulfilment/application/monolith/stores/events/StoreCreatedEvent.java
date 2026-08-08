package com.fulfilment.application.monolith.stores.events;

/**
 * Immutable snapshot of a {@code Store} as it was finally persisted, fired after the creating
 * transaction has committed successfully. Carries plain values (not the JPA entity) so observers
 * cannot accidentally touch a detached/stale managed instance.
 */
public record StoreCreatedEvent(Long id, String name, int quantityProductsInStock) {}
