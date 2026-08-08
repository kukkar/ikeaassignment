package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * The generated {@code WarehouseResource} interface returns the {@code Warehouse} bean directly
 * (not a {@code jakarta.ws.rs.core.Response}) for every operation, so JAX-RS applies its default
 * 200 status to all of them. This filter corrects only the creation endpoint to the 201 the
 * OpenAPI contract declares; every other endpoint's default status already matches the contract
 * (200 for get/replace, 204 for the void archive method).
 */
@Provider
public class WarehouseCreatedStatusFilter implements ContainerResponseFilter {

  @Context ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (HttpMethod.POST.equals(requestContext.getMethod())
        && resourceInfo.getResourceMethod() != null
        && "createANewWarehouseUnit".equals(resourceInfo.getResourceMethod().getName())
        && responseContext.getStatus() == Response.Status.OK.getStatusCode()) {
      responseContext.setStatus(Response.Status.CREATED.getStatusCode());
    }
  }
}
