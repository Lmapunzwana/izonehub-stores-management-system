package com.izonehub.stores.project;

import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projects;
    private final StoreRepository   stores;
    private final UserRepository    users;

    public ProjectController(ProjectRepository projects, StoreRepository stores, UserRepository users) {
        this.projects = projects;
        this.stores   = stores;
        this.users    = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<Project> list(@RequestParam(defaultValue = "0")    int page,
                              @RequestParam(defaultValue = "50")   int size,
                              @RequestParam(defaultValue = "true") boolean active) {
        var projectPage = projects.findByActive(active, PageRequest.of(page, size));
        projectPage.forEach(this::resolveLazy);
        return projectPage;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Project get(@PathVariable UUID id) {
        Project p = projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveLazy(p);
        return p;
    }

    /** Force-initialize lazy associations needed by the JSON serializer. */
    private void resolveLazy(Project p) {
        if (p.getSiteStore() != null) {
            p.getSiteStore().getName();
            if (p.getSiteStore().getManager() != null) {
                p.getSiteStore().getManager().getFullName();
            }
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Project create(@Valid @RequestBody ProjectRequest req) {
        var store = stores.findById(req.siteStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
        if (!store.isActive())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That site has been closed and can't take a new project");
        return projects.save(new Project(req.code(), req.name(), store, req.budgetCeiling()));
    }

    // Closing a project also closes its dedicated site store — but only if
    // no other still-active project shares that same store, since Store has
    // no hard 1:1 constraint back to a single project.
    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Project close(@PathVariable UUID id) {
        Project p = projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        p.close();
        projects.save(p);

        var store = p.getSiteStore();
        if (store != null && store.getType() == com.izonehub.stores.store.StoreType.SITE
                && !projects.existsBySiteStoreIdAndActiveTrueAndIdNot(store.getId(), p.getId())) {
            store.close();
            stores.save(store);
        }
        return p;
    }

    @PostMapping("/{id}/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Project assignEmployee(@PathVariable UUID id, @PathVariable UUID employeeId) {
        Project p = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AppUser u = users.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        p.assignEmployee(u);
        return projects.save(p);
    }

    @DeleteMapping("/{id}/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Project removeEmployee(@PathVariable UUID id, @PathVariable UUID employeeId) {
        Project p = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AppUser u = users.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        p.removeEmployee(u);
        return projects.save(p);
    }

    public record ProjectRequest(
            @NotBlank String code,
            @NotBlank String name,
            @jakarta.validation.constraints.NotNull UUID siteStoreId,
            BigDecimal budgetCeiling) {}
}
