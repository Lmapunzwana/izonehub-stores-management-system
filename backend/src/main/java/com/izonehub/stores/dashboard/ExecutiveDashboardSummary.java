package com.izonehub.stores.dashboard;

import java.math.BigDecimal;
import java.util.Map;

public record ExecutiveDashboardSummary(
        BigDecimal totalStockValue,
        BigDecimal inTransitValue,
        long lowStockItems,
        long openDiscrepancies,
        long pendingRequests,
        long overdueReceipts,
        Map<String, Long> requestsByStatus
) {}
