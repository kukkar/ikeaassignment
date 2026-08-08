package com.fulfilment.application.monolith.stores;

/**
 * Dedicated PATCH payload for {@code Store}, distinct from the JPA entity.
 *
 * <p>Fields use boxed types so Jackson can leave a field {@code null} when it is omitted from the
 * request JSON, as opposed to the entity's primitive {@code int quantityProductsInStock}, which
 * cannot distinguish "omitted" from "explicitly set to zero".
 */
public class StorePatchRequest {

  public String name;

  public Integer quantityProductsInStock;
}
