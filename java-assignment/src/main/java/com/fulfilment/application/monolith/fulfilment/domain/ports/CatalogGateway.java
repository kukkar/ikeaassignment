package com.fulfilment.application.monolith.fulfilment.domain.ports;

/**
 * Read-only existence checks against the Store and Product catalogues, so use cases don't depend
 * on their Panache/Active-Record persistence directly.
 */
public interface CatalogGateway {

  boolean storeExists(Long storeId);

  boolean productExists(Long productId);
}
