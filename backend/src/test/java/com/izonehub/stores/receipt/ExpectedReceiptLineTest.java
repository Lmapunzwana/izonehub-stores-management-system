package com.izonehub.stores.receipt;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ExpectedReceiptLineTest {
    @Test
    void detectsQuantityVariance() {
        ExpectedReceiptLine line = new ExpectedReceiptLine(null, new BigDecimal("10"));

        line.recordReceived(new BigDecimal("8"), ReceiptLineCondition.GOOD);

        assertThat(line.hasVariance()).isTrue();
    }

    @Test
    void detectsConditionVariance() {
        ExpectedReceiptLine line = new ExpectedReceiptLine(null, new BigDecimal("10"));

        line.recordReceived(new BigDecimal("10"), ReceiptLineCondition.DAMAGED);

        assertThat(line.hasVariance()).isTrue();
    }

    @Test
    void acceptsCleanMatchingReceipt() {
        ExpectedReceiptLine line = new ExpectedReceiptLine(null, new BigDecimal("10"));

        line.recordReceived(new BigDecimal("10"), ReceiptLineCondition.GOOD);

        assertThat(line.hasVariance()).isFalse();
    }
}
