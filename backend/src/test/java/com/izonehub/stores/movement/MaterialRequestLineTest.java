package com.izonehub.stores.movement;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialRequestLineTest {
    @Test
    void approvalCannotExceedRequestedQuantity() {
        MaterialRequestLine line = new MaterialRequestLine(null, new BigDecimal("10"));

        assertThatThrownBy(() -> line.approve(new BigDecimal("11"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispatchedQuantityCannotExceedApprovedQuantity() {
        MaterialRequestLine line = new MaterialRequestLine(null, new BigDecimal("10"));
        line.approve(new BigDecimal("8"));

        assertThatThrownBy(() -> line.dispatch(new BigDecimal("9"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void varianceIsDifferenceBetweenDispatchedAndReceivedQuantity() {
        MaterialRequestLine line = new MaterialRequestLine(null, new BigDecimal("10"));
        line.approve(new BigDecimal("10"));
        line.dispatch(new BigDecimal("10"));

        line.receive(new BigDecimal("7"));

        assertThat(line.hasReceiptVariance()).isTrue();
        assertThat(line.varianceQuantity()).isEqualByComparingTo("3");
    }
}
