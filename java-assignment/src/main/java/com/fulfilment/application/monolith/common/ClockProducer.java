package com.fulfilment.application.monolith.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/** Makes {@link Clock} injectable so use cases can be tested with a fixed/controllable time. */
@ApplicationScoped
public class ClockProducer {

  @Produces
  @ApplicationScoped
  public Clock systemClock() {
    return Clock.systemUTC();
  }
}
