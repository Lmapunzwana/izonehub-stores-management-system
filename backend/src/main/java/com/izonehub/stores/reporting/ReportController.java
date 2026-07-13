package com.izonehub.stores.reporting;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final InventoryReportService reportService;
    private final ReportExportService    exportService;
    private final SupplierPerformanceService supplierPerformanceService;

    public ReportController(InventoryReportService reportService, ReportExportService exportService,
                             SupplierPerformanceService supplierPerformanceService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.supplierPerformanceService = supplierPerformanceService;
    }

    // Procurement Officer and Site Store Manager both need real stock
    // visibility to do their actual jobs (raising expected receipts against
    // low stock, and checking availability before a material request) — the
    // class-level restriction below to Admin/Finance/Executive/Central Store
    // Manager only was locking those two roles out of every item view.
    @GetMapping("/current-stock")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<?> currentStock(
            @RequestParam(required = false) UUID    storeId,
            @RequestParam(required = false) UUID    itemId,
            @RequestParam(required = false) String  category,
            @RequestParam(required = false) UUID    projectId,
            @RequestParam(defaultValue = "json")    String format) {

        ReportFilter filter = new ReportFilter(null, null, storeId, itemId, projectId, category);
        List<CurrentStockRow> rows = reportService.currentStock(filter);

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = exportService.currentStockCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"current-stock-" + LocalDate.now() + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(csv);
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/inventory/expiry")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<List<ExpiringStockRow>> expiry(
            @RequestParam(defaultValue = "180") int daysThreshold) {
        return ResponseEntity.ok(reportService.expiringStock(daysThreshold));
    }

    // Real metrics computed from GRN history — see SupplierPerformanceService
    // for why grouping is by free-text supplier name rather than a real FK.
    @GetMapping("/supplier-performance")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public ResponseEntity<List<SupplierPerformanceRow>> supplierPerformance() {
        return ResponseEntity.ok(supplierPerformanceService.compute());
    }
}
