package com.fulfilment.application.monolith.stores.events;

/**
 * Immutable snapshot of a {@code Store} as it was finally persisted after an update (full or
 * partial), fired after the updating transaction has committed successfully.
 */
public record StoreUpdatedEvent(Long id, String name, int quantityProductsInStock) {}
