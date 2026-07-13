package com.izonehub.stores.reporting;

import java.time.LocalDate;
import java.util.UUID;
import java.math.BigDecimal;

public record ExpiringStockRow(
        String item,
        String batchNo,
        LocalDate expiryDate,
        long daysRemaining,
        BigDecimal quantity,
        Integer tier
) {}
