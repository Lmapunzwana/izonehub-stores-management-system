package com.izonehub.stores.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** General operations summary — all operational roles. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public DashboardSummary summary() {
        return service.summary();
    }

    /** Finance-specific KPIs: stock value, GRN trends, pending approvals. */
    @GetMapping("/finance")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public FinanceDashboardSummary finance() {
        return service.financeSummary();
    }

    /** Executive high-level summary: stock value, in-transit, discrepancies, request pipeline. */
    @GetMapping("/executive")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public ExecutiveDashboardSummary executive() {
        return service.executiveSummary();
    }
}
