    @PostMapping("/standalone-return")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','SITE_STORE_MANAGER')")
    public MaterialRequest standaloneReturn(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal String email) {
        AppUser submitter = currentUser(email);
        com.izonehub.stores.store.Store sourceStore = stores.findById(req.sourceStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source store not found"));
        com.izonehub.stores.store.Store requestingStore = stores.findById(req.requestingStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requesting store not found"));
        com.izonehub.stores.project.Project project = projects.findById(req.projectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found"));

        MaterialRequest mr = new MaterialRequest(requestingStore, sourceStore, project, submitter, "Standalone Return to Central");
        for (LineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            mr.addLine(new MaterialRequestLine(item, l.requestedQuantity()));
        }
        
        MaterialRequest saved = requests.save(mr);
        saved.submit();
        
        // Auto-approve and dispatch
        List<BigDecimal> quantities = req.lines().stream().map(LineRequest::requestedQuantity).toList();
        svc.approve(saved, submitter, quantities);
        svc.dispatch(saved, submitter, submitter.getFullName(), "RETURN", quantities);
        
        return saved;
    }
