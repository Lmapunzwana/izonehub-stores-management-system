package com.izonehub.stores.dashboard;

public record DashboardSummary(long lowStockCount, long openDiscrepancyCount, long inTransitCount, long overdueReceiptCount) {
}
