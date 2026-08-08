package com.fulfilment.application.monolith.fulfilment.adapters.database;

import com.fulfilment.application.monolith.fulfilment.domain.ports.CatalogGateway;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin existence-check adapter over the existing Store (Active Record) and Product (Panache
 * repository) persistence - reused as-is, not rewritten, since the bonus only needs to know
 * whether a given id exists.
 */
@ApplicationScoped
public class CatalogGatewayAdapter implements CatalogGateway {

  @Inject ProductRepository productRepository;

  @Override
  public boolean storeExists(Long storeId) {
    return storeId != null && Store.findById(storeId) != null;
  }

  @Override
  public boolean productExists(Long productId) {
    return productId != null && productRepository.findById(productId) != null;
  }
}
