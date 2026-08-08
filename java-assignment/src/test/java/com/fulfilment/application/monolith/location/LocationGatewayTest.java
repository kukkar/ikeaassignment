package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  private final LocationGateway locationGateway = new LocationGateway();

  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertEquals("ZWOLLE-001", location.identification);
    assertEquals(1, location.maxNumberOfWarehouses);
    assertEquals(40, location.maxCapacity);
  }

  @Test
  public void testWhenResolveUnknownLocationShouldThrow() {
    assertThrows(
        LocationNotFoundException.class, () -> locationGateway.resolveByIdentifier("UNKNOWN-999"));
  }

  @Test
  public void testWhenResolveBlankLocationShouldThrow() {
    assertThrows(LocationNotFoundException.class, () -> locationGateway.resolveByIdentifier("   "));
    assertThrows(LocationNotFoundException.class, () -> locationGateway.resolveByIdentifier(""));
  }

  @Test
  public void testWhenResolveNullLocationShouldThrow() {
    assertThrows(LocationNotFoundException.class, () -> locationGateway.resolveByIdentifier(null));
  }
}
