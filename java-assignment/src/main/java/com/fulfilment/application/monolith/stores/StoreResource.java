package com.fulfilment.application.monolith.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fulfilment.application.monolith.stores.events.StoreCreatedEvent;
import com.fulfilment.application.monolith.stores.events.StoreUpdatedEvent;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

  private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());

  // CDI events are only delivered to observers registered for TransactionPhase.AFTER_SUCCESS,
  // once this method's transaction has committed. This guarantees the legacy system only ever
  // sees confirmed data - see StoreLegacySyncListener. Deletion is intentionally not synced: the
  // legacy gateway offers no delete operation today. Should that requirement appear, apply the
  // same after-commit event pattern with a StoreDeletedEvent.
  @Inject Event<StoreCreatedEvent> storeCreatedEvent;
  @Inject Event<StoreUpdatedEvent> storeUpdatedEvent;

  @GET
  public List<Store> get() {
    return Store.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Store getSingle(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    return entity;
  }

  @POST
  @Transactional
  public Response create(Store store) {
    if (store.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    store.persist();

    storeCreatedEvent.fire(new StoreCreatedEvent(store.id, store.name, store.quantityProductsInStock));

    return Response.ok(store).status(201).build();
  }

  @PUT
  @Path("{id}")
  @Transactional
  public Store update(Long id, Store updatedStore) {
    if (updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

    storeUpdatedEvent.fire(new StoreUpdatedEvent(entity.id, entity.name, entity.quantityProductsInStock));

    return entity;
  }

  @PATCH
  @Path("{id}")
  @Transactional
  public Store patch(Long id, StorePatchRequest patch) {
    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    if (patch.name != null) {
      entity.name = patch.name;
    }

    if (patch.quantityProductsInStock != null) {
      entity.quantityProductsInStock = patch.quantityProductsInStock;
    }

    storeUpdatedEvent.fire(new StoreUpdatedEvent(entity.id, entity.name, entity.quantityProductsInStock));

    return entity;
  }

  // fulfilment_assignment FKs to store(id)/product(id) have no ON DELETE clause, by design -
  // deleting a Store/Product with live assignments must not cascade-delete that history, and
  // pre-checking via a dependency on the fulfilment module would recreate the coupling removed
  // elsewhere in this codebase. So the FK violation is caught below and translated to 409 instead
  // (flushed immediately so it surfaces here, not at the @Transactional interceptor's commit).
  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    try {
      entity.delete();
      Panache.flush();
    } catch (PersistenceException e) {
      if (!isForeignKeyViolation(e)) {
        throw e;
      }
      throw new WebApplicationException(
          "Store with id of "
              + id
              + " cannot be deleted because other records (e.g. fulfilment assignments) still reference it.",
          409);
    }
    return Response.status(204).build();
  }

  // PostgreSQL SQLState 23503 = foreign_key_violation. Checked explicitly, not just "is a
  // PersistenceException", so an unrelated persistence failure is never mislabeled as a 409.
  private static boolean isForeignKeyViolation(PersistenceException e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException cve) {
        return "23503".equals(cve.getSQLState());
      }
    }
    return false;
  }

  @Provider
  public static class ErrorMapper implements ExceptionMapper<Exception> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
      LOGGER.error("Failed to handle request", exception);

      int code = 500;
      if (exception instanceof WebApplicationException) {
        code = ((WebApplicationException) exception).getResponse().getStatus();
      }

      ObjectNode exceptionJson = objectMapper.createObjectNode();
      exceptionJson.put("exceptionType", exception.getClass().getName());
      exceptionJson.put("code", code);

      if (exception.getMessage() != null) {
        exceptionJson.put("error", exception.getMessage());
      }

      return Response.status(code).entity(exceptionJson).build();
    }
  }
}
