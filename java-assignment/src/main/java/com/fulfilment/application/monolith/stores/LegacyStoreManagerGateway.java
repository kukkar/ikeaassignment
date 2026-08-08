package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LegacyStoreManagerGateway {

  private static final Logger LOGGER = Logger.getLogger(LegacyStoreManagerGateway.class);

  public void createStoreOnLegacySystem(Store store) {
    // just to emulate as this would send this to a legacy system, let's write a temp file with the
    writeToFile(store);
  }

  public void updateStoreOnLegacySystem(Store store) {
    // just to emulate as this would send this to a legacy system, let's write a temp file with the
    writeToFile(store);
  }

  private void writeToFile(Store store) {
    try {
      // Step 1: Create a temporary file
      Path tempFile = Files.createTempFile(store.name, ".txt");
      LOGGER.debugf("Temporary file created at: %s", tempFile);

      // Step 2: Write data to the temporary file
      String content =
          "Store created. [ name ="
              + store.name
              + " ] [ items on stock ="
              + store.quantityProductsInStock
              + "]";
      Files.write(tempFile, content.getBytes());

      // Step 3: Optionally, read the data back to verify
      String readContent = new String(Files.readAllBytes(tempFile));
      LOGGER.debugf("Data read from temporary file: %s", readContent);

      // Step 4: Delete the temporary file when done
      Files.delete(tempFile);

    } catch (Exception e) {
      throw new LegacyStoreSyncException(
          "Failed to synchronize store '" + store.name + "' to the legacy system", e);
    }
  }
}
