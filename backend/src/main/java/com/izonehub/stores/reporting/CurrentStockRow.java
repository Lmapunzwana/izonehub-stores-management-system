package com.izonehub.stores.reporting;

import java.math.BigDecimal;

public record CurrentStockRow(String storeName, String itemCode, String itemName, BigDecimal onHand,
                              BigDecimal inTransit, BigDecimal frozen, BigDecimal damaged) {
}
