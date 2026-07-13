package com.izonehub.stores.dashboard;

import java.math.BigDecimal;
import java.util.Map;

public record FinanceDashboardSummary(
        BigDecimal totalStockValue,
        BigDecimal frozenValue,
        long pendingApprovals,
        long totalGrns,
        Map<String, Long> grnByMonth
) {}
