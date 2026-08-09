package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import com.fulfilment.application.monolith.common.ApiError;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentDomainException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Mirrors {@code WarehouseDomainExceptionMapper} exactly, both mapping to the shared {@link
 * ApiError} body (owned by {@code common}, not by either module) so the whole API has one stable
 * error contract, not one per module.
 */
@Provider
public class FulfilmentDomainExceptionMapper implements ExceptionMapper<FulfilmentDomainException> {

  private static final Logger LOGGER = Logger.getLogger(FulfilmentDomainExceptionMapper.class);

  @Override
  public Response toResponse(FulfilmentDomainException exception) {
    LOGGER.debugf(exception, "Rejecting request: %s", exception.getMessage());

    Response.Status status =
        switch (exception.errorType()) {
          case VALIDATION -> Response.Status.BAD_REQUEST;
          case NOT_FOUND -> Response.Status.NOT_FOUND;
          case CONFLICT -> Response.Status.CONFLICT;
        };

    var details =
        exception instanceof FulfilmentValidationException validationException
            ? validationException.violations()
            : null;

    ApiError body = new ApiError(exception.errorType().name(), exception.getMessage(), details);

    return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
