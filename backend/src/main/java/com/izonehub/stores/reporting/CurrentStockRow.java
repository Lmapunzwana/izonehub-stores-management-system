package com.izonehub.stores.reporting;

import java.util.UUID;
import java.math.BigDecimal;

public record CurrentStockRow(String storeName, UUID itemId, String itemCode, String itemName, BigDecimal onHand,
                              BigDecimal inTransit, BigDecimal frozen, BigDecimal damaged, BigDecimal reserved) {
}
