package com.fulfilment.application.monolith.fulfilment.adapters.restapi;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.AssignWarehouseToProductForStoreOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ListStoreFulfilmentAssignmentsOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.RemoveFulfilmentAssignmentOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Handwritten, like Product/Store - not OpenAPI-generated like Warehouse. This is an internal,
 * Store-scoped nested resource rather than a cross-team contract (see QUESTIONS.md Q2 and
 * README.md), so it follows the project's handwritten convention. Business-rule transaction
 * boundaries live on the use cases (the warehouse-style convention this bonus follows), not here.
 */
@Path("/stores/{storeId}/fulfilment-assignments")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FulfilmentAssignmentResource {

  @Inject AssignWarehouseToProductForStoreOperation assignOperation;
  @Inject RemoveFulfilmentAssignmentOperation removeOperation;
  @Inject ListStoreFulfilmentAssignmentsOperation listOperation;

  @POST
  public Response create(@PathParam("storeId") Long storeId, FulfilmentAssignmentRequest request) {
    if (request == null) {
      throw new FulfilmentValidationException("Request body is required", Map.of());
    }

    FulfilmentAssignment created =
        assignOperation.assign(storeId, request.productId, request.warehouseBusinessUnitCode);

    return Response.status(Response.Status.CREATED).entity(toResponse(created)).build();
  }

  @GET
  public List<FulfilmentAssignmentResponse> list(
      @PathParam("storeId") Long storeId, @QueryParam("productId") Long productId) {
    List<FulfilmentAssignment> assignments =
        productId != null
            ? listOperation.listForStoreAndProduct(storeId, productId)
            : listOperation.listForStore(storeId);

    return assignments.stream().map(this::toResponse).toList();
  }

  @DELETE
  @Path("{assignmentId}")
  public Response delete(
      @PathParam("storeId") Long storeId, @PathParam("assignmentId") Long assignmentId) {
    removeOperation.remove(storeId, assignmentId);
    return Response.noContent().build();
  }

  private FulfilmentAssignmentResponse toResponse(FulfilmentAssignment assignment) {
    return new FulfilmentAssignmentResponse(
        assignment.id,
        assignment.storeId,
        assignment.productId,
        assignment.warehouseBusinessUnitCode,
        assignment.warehouseActive,
        assignment.createdAt);
  }
}
