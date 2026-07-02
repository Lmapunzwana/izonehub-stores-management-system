package com.izonehub.stores.inventory;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreInventoryTest {
    @Test
    void dispatchRequiresSufficientStockAndMovesToTransit() {
        StoreInventory inventory = new StoreInventory(null, null);
        inventory.receiveUsable(new BigDecimal("10"));

        inventory.dispatch(new BigDecimal("4"));

        assertThat(inventory.getQuantityOnHand()).isEqualByComparingTo("6");
        assertThat(inventory.getQuantityInTransit()).isEqualByComparingTo("4");
    }

    @Test
    void damagedReceiptsAreSeparatedFromUsableStock() {
        StoreInventory inventory = new StoreInventory(null, null);

        inventory.receiveDamaged(new BigDecimal("3"));

        assertThat(inventory.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(inventory.getQuantityDamaged()).isEqualByComparingTo("3");
    }

    @Test
    void canCompleteTransitWithoutReturningStockToSource() {
        StoreInventory inventory = new StoreInventory(null, null);
        inventory.receiveUsable(new BigDecimal("10"));
        inventory.dispatch(new BigDecimal("4"));

        inventory.completeTransit(new BigDecimal("3"));

        assertThat(inventory.getQuantityOnHand()).isEqualByComparingTo("6");
        assertThat(inventory.getQuantityInTransit()).isEqualByComparingTo("1");
    }

    @Test
    void cannotDispatchMoreThanOnHand() {
        StoreInventory inventory = new StoreInventory(null, null);

        assertThatThrownBy(() -> inventory.dispatch(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
    }
}
