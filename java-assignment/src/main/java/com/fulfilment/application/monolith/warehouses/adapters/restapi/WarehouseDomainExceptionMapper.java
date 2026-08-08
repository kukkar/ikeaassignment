package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseDomainException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Translates domain failures into the API's stable error shape, keeping HTTP status-code
 * decisions out of the domain/use-case layer. Never leaks stack traces to the client.
 */
@Provider
public class WarehouseDomainExceptionMapper implements ExceptionMapper<WarehouseDomainException> {

  private static final Logger LOGGER = Logger.getLogger(WarehouseDomainExceptionMapper.class);

  @Override
  public Response toResponse(WarehouseDomainException exception) {
    LOGGER.debugf(exception, "Rejecting request: %s", exception.getMessage());

    Response.Status status =
        switch (exception.errorType()) {
          case VALIDATION -> Response.Status.BAD_REQUEST;
          case NOT_FOUND -> Response.Status.NOT_FOUND;
          case CONFLICT -> Response.Status.CONFLICT;
        };

    var details =
        exception instanceof WarehouseValidationException validationException
            ? validationException.violations()
            : null;

    ApiError body = new ApiError(exception.errorType().name(), exception.getMessage(), details);

    return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
