package com.izonehub.stores.reporting;

import java.time.LocalDate;
import java.util.UUID;

public record ReportFilter(LocalDate from, LocalDate to, UUID storeId, UUID itemId, UUID projectId, String category) {
}
