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
    private final PdfExportService pdfExportService;
    private final com.izonehub.stores.user.UserRepository users;
    private final com.izonehub.stores.store.StoreRepository storeRepo;

    public ReportController(InventoryReportService reportService, ReportExportService exportService,
                             SupplierPerformanceService supplierPerformanceService, PdfExportService pdfExportService,
                             com.izonehub.stores.user.UserRepository users, com.izonehub.stores.store.StoreRepository storeRepo) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.supplierPerformanceService = supplierPerformanceService;
        this.pdfExportService = pdfExportService;
        this.users = users;
        this.storeRepo = storeRepo;
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
            @RequestParam(required = false) String  from,
            @RequestParam(required = false) String  to,
            @RequestParam(defaultValue = "json")    String format,
            @org.springframework.security.core.annotation.AuthenticationPrincipal String email) {

        ReportFilter filter = new ReportFilter(null, null, storeId, itemId, projectId, category);
        List<CurrentStockRow> rows = reportService.currentStock(filter);

        if (email != null) {
            com.izonehub.stores.user.AppUser user = users.findByEmail(email).orElse(null);
            if (user != null) {
                boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
                if (isSiteManager) {
                    java.util.List<com.izonehub.stores.store.Store> managedStores = storeRepo.findStoresForUser(user.getId());
                    java.util.List<String> allowedStoreNames = new java.util.ArrayList<>(managedStores.stream().map(com.izonehub.stores.store.Store::getName).toList());
                    
                    if (storeId != null) {
                        com.izonehub.stores.store.Store queriedStore = storeRepo.findById(storeId).orElse(null);
                        if (queriedStore != null && queriedStore.getType() == com.izonehub.stores.store.StoreType.CENTRAL) {
                            allowedStoreNames.add(queriedStore.getName());
                        }
                    }
                    
                    rows = rows.stream().filter(r -> allowedStoreNames.contains(r.storeName())).toList();
                }
            }
        }

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = exportService.currentStockCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"current-stock-" + LocalDate.now() + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(csv);
        } else if ("pdf".equalsIgnoreCase(format)) {
            String dateRange = (from != null && to != null) ? from + " to " + to : "All time";
            List<String> headers = List.of("Store", "Item Code", "Item Name", "On Hand", "In Transit", "Frozen", "Damaged", "Reserved");
            List<List<String>> dataRows = rows.stream().map(r -> List.of(
                    r.storeName(), r.itemCode(), r.itemName(),
                    r.onHand().toString(), r.inTransit().toString(),
                    r.frozen().toString(), r.damaged().toString(), r.reserved().toString()
            )).toList();
            byte[] pdf = pdfExportService.generatePdfReport("Inventory Summary", dateRange, headers, dataRows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory-summary-" + LocalDate.now() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/project-consumption")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<?> projectConsumption(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "json") String format) {
        if ("pdf".equalsIgnoreCase(format)) {
            String dateRange = (from != null && to != null) ? from + " to " + to : "All time";
            byte[] pdf = pdfExportService.generatePdfReport("Project Consumption", dateRange, List.of("Project", "Item", "Quantity Consumed"), List.of());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-consumption-" + LocalDate.now() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/grns")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<?> grnsReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "json") String format) {
        if ("pdf".equalsIgnoreCase(format)) {
            String dateRange = (from != null && to != null) ? from + " to " + to : "All time";
            byte[] pdf = pdfExportService.generatePdfReport("GRN Report", dateRange, List.of("GRN No", "Date", "Supplier", "Received By"), List.of());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"grn-report-" + LocalDate.now() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/discrepancies")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<?> discrepanciesReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "json") String format) {
        if ("pdf".equalsIgnoreCase(format)) {
            String dateRange = (from != null && to != null) ? from + " to " + to : "All time";
            byte[] pdf = pdfExportService.generatePdfReport("Discrepancy Report", dateRange, List.of("Date", "Item", "Type", "Status"), List.of());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"discrepancy-report-" + LocalDate.now() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        return ResponseEntity.ok(List.of());
    }

    // Real metrics computed from GRN history — see SupplierPerformanceService
    // for why grouping is by free-text supplier name rather than a real FK.
    @GetMapping("/supplier-performance")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public ResponseEntity<List<SupplierPerformanceRow>> supplierPerformance() {
        return ResponseEntity.ok(supplierPerformanceService.compute());
    }
}
