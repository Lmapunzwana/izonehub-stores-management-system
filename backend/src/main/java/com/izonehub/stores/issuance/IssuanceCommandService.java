package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.project.ProjectRepository;
import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class IssuanceCommandService {
    private final MaterialIssueVoucherRepository mivs;
    private final InventoryCommandService inventory;
    private final ProjectRepository projects;

    public IssuanceCommandService(MaterialIssueVoucherRepository mivs, InventoryCommandService inventory, ProjectRepository projects) {
        this.mivs = mivs;
        this.inventory = inventory;
        this.projects = projects;
    }

    @Transactional
    public MaterialIssueVoucher confirm(MaterialIssueVoucher miv) {
        if (!miv.getProject().isActive()) {
            throw new IllegalStateException("Cannot issue material against a closed project");
        }
        miv.getLines().forEach(line -> inventory.dispatch(miv.getStore(), line.getItem(), line.getIssuedQuantity()));
        return mivs.save(miv);
    }

    public String nextReference() {
        return "MIV-" + System.nanoTime();
    }
}
