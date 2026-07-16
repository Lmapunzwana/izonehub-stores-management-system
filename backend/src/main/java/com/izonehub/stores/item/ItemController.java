package com.izonehub.stores.item;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemRepository repo;
    private final com.izonehub.stores.user.UserRepository users;
    private final com.izonehub.stores.store.StoreRepository stores;

    public ItemController(ItemRepository repo, com.izonehub.stores.user.UserRepository users, com.izonehub.stores.store.StoreRepository stores) {
        this.repo = repo;
        this.users = users;
        this.stores = stores;
    }

    @GetMapping
    public Page<Item> list(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(required = false)      String search,
            @RequestParam(required = false)      String category,
            @RequestParam(defaultValue = "true") boolean active,
            @org.springframework.security.core.annotation.AuthenticationPrincipal String email) {

        ItemCategory categoryEnum = category == null ? null : ItemCategory.valueOf(category.toUpperCase());
        
        if (email != null) {
            com.izonehub.stores.user.AppUser user = users.findByEmail(email).orElse(null);
            if (user != null) {
                boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER) 
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
                if (isSiteManager) {
                    java.util.List<com.izonehub.stores.store.Store> managedStores = stores.findByManager_Id(user.getId());
                    if (!managedStores.isEmpty()) {
                        java.util.List<UUID> storeIds = managedStores.stream().map(com.izonehub.stores.store.Store::getId).toList();
                        return repo.searchWithStoreFilter(active, search == null ? "" : search, categoryEnum, storeIds, PageRequest.of(page, size));
                    } else {
                        return Page.empty();
                    }
                }
            }
        }
        
        return repo.search(active, search == null ? "" : search, categoryEnum, PageRequest.of(page, size));
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return Arrays.stream(ItemCategory.values())
                .map(Enum::name)
                .toList();
    }

    @GetMapping("/{id}")
    public Item get(@PathVariable UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Item create(@Valid @RequestBody ItemRequest req) {
        if (repo.existsByCodeIgnoreCase(req.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item code already exists");
        return repo.save(new Item(req.code(), req.name(), req.description(),
                req.unitOfMeasure(), ItemCategory.valueOf(req.category()), req.reorderThreshold()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Item update(@PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.update(req.name(), req.description(), req.unitOfMeasure(),
                ItemCategory.valueOf(req.category()), req.reorderThreshold());
        return repo.save(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public void deactivate(@PathVariable UUID id) {
        Item item = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.deactivate();
        repo.save(item);
    }

    /** POST /api/items/import — CSV columns: code,name,unitOfMeasure,category,reorderThreshold */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");

        List<String> errors   = new ArrayList<>();
        int saved = 0, row = 1;

        // One query for the whole batch instead of one findAll() per CSV
        // row — the old code called repo.findAll() inside the loop, which
        // meant importing N rows against an existing table of M items did
        // up to N full-table loads of M rows each in a single request.
        Set<String> existingCodes = new HashSet<>(repo.findAllCodesLowercase());
        Set<String> seenInThisFile = new HashSet<>();

        try (CSVReader csv = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            csv.readNext(); // skip header
            String[] line;
            while ((line = csv.readNext()) != null) {
                row++;
                if (line.length < 5) { errors.add("Row " + row + ": need 5 columns"); continue; }
                String code = line[0].trim(), name = line[1].trim(),
                       unit = line[2].trim(), catStr = line[3].trim().toUpperCase(),
                       threshStr = line[4].trim();
                if (code.isBlank() || name.isBlank()) { errors.add("Row " + row + ": code/name required"); continue; }
                ItemCategory cat;
                try { cat = ItemCategory.valueOf(catStr); }
                catch (IllegalArgumentException e) { errors.add("Row " + row + ": unknown category '" + catStr + "'"); continue; }
                BigDecimal thresh;
                try { thresh = new BigDecimal(threshStr); }
                catch (NumberFormatException e) { errors.add("Row " + row + ": bad threshold"); continue; }
                String lowerCode = code.toLowerCase();
                if (existingCodes.contains(lowerCode) || !seenInThisFile.add(lowerCode)) {
                    errors.add("Row " + row + ": code '" + code + "' already exists — skipped");
                    continue;
                }
                repo.save(new Item(code, name, null, unit, cat, thresh));
                saved++;
            }
        } catch (IOException | CsvValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot parse CSV: " + e.getMessage());
        }
        return Map.of("imported", saved, "skipped", errors.size(), "errors", errors);
    }

    public record ItemRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String unitOfMeasure,
            @NotBlank String category,
            @NotNull @DecimalMin("0") BigDecimal reorderThreshold) {}
}
