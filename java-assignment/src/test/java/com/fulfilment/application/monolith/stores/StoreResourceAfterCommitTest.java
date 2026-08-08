package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
public class StoreResourceAfterCommitTest {

  @Inject StoreResource storeResource;

  @InjectMock LegacyStoreManagerGateway legacyStoreManagerGateway;

  @Test
  public void testLegacyGatewayIsCalledOnlyAfterCommit() {
    AtomicBoolean rowVisibleInIndependentTransaction = new AtomicBoolean(false);

    Mockito.doAnswer(
            invocation -> {
              Store snapshot = invocation.getArgument(0);
              // If the transaction that persisted this store had truly committed already, a
              // brand new, independent transaction must be able to see the row.
              boolean visible =
                  QuarkusTransaction.requiringNew()
                      .call(() -> Store.findById(snapshot.id) != null);
              rowVisibleInIndependentTransaction.set(visible);
              return null;
            })
        .when(legacyStoreManagerGateway)
        .createStoreOnLegacySystem(any());

    Store store = new Store("AFTER-COMMIT-STORE");
    store.quantityProductsInStock = 7;

    storeResource.create(store);

    verify(legacyStoreManagerGateway, times(1)).createStoreOnLegacySystem(any());
    assertTrue(
        rowVisibleInIndependentTransaction.get(),
        "the store row must already be committed and visible to other transactions when the"
            + " legacy gateway is invoked");
  }

  @Test
  public void testLegacyGatewayReceivesFinalPersistedValues() {
    Store store = new Store("FINAL-STATE-STORE");
    store.quantityProductsInStock = 5;
    storeResource.create(store);

    Store updateRequest = new Store("FINAL-STATE-STORE-RENAMED");
    updateRequest.quantityProductsInStock = 99;

    storeResource.update(store.id, updateRequest);

    org.mockito.ArgumentCaptor<Store> captor = org.mockito.ArgumentCaptor.forClass(Store.class);
    verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(captor.capture());

    Store snapshot = captor.getValue();
    assertEquals(store.id, snapshot.id);
    assertEquals("FINAL-STATE-STORE-RENAMED", snapshot.name);
    assertEquals(99, snapshot.quantityProductsInStock);
  }

  @Test
  public void testPatchOnlyAppliesSuppliedFieldsAndSyncsFinalState() {
    Store store = new Store("PATCH-STORE");
    store.quantityProductsInStock = 12;
    storeResource.create(store);

    StorePatchRequest patch = new StorePatchRequest();
    patch.quantityProductsInStock = 0; // intentional zero, must be distinguishable from "omitted"

    Store patched = storeResource.patch(store.id, patch);

    assertEquals("PATCH-STORE", patched.name, "name was omitted from the patch, must stay unchanged");
    assertEquals(0, patched.quantityProductsInStock, "an explicit zero must be applied, not ignored");

    org.mockito.ArgumentCaptor<Store> captor = org.mockito.ArgumentCaptor.forClass(Store.class);
    verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(captor.capture());
    assertEquals(0, captor.getValue().quantityProductsInStock);
  }

  @Test
  public void testNoLegacyCallWhenTransactionRollsBack() {
    Store store = new Store("ROLLBACK-STORE");
    store.quantityProductsInStock = 3;

    assertThrows(
        IllegalStateException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(
                    () -> {
                      storeResource.create(store);
                      throw new IllegalStateException("simulated failure after persist");
                    }));

    verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(any());
  }
}
