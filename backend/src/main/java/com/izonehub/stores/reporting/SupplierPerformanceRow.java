package com.izonehub.stores.reporting;

import java.math.BigDecimal;
import java.util.List;

public record SupplierPerformanceRow(
        String supplierName,
        long ordersCount,
        BigDecimal accuracyPercent,
        BigDecimal fulfillmentPercent,
        BigDecimal defectRatePercent,
        BigDecimal avgLeadTimeDays,
        List<MonthlyAccuracy> accuracyTrend
) {
    public record MonthlyAccuracy(String month, BigDecimal accuracyPercent) {}
}
