package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.movement.Discrepancy;
import com.izonehub.stores.movement.DiscrepancyRepository;
import com.izonehub.stores.issuance.ReturnController.ConfirmReturnRequest;
import com.izonehub.stores.issuance.ReturnController.ConfirmLineRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class ReturnCommandService {
    private final StockReturnRepository returns;
    private final InventoryCommandService inventory;
    private final DiscrepancyRepository discrepancies;

    public ReturnCommandService(StockReturnRepository returns, InventoryCommandService inventory, DiscrepancyRepository discrepancies) {
        this.returns = returns;
        this.inventory = inventory;
        this.discrepancies = discrepancies;
    }

    @Transactional
    public StockReturn createPendingReturn(MaterialIssueVoucher miv, StockReturn stockReturn) {
        stockReturn.setStatus(ReturnStatus.PENDING_CONFIRMATION);
        Store siteStore = miv.getProject().getSiteStore();
        stockReturn.getLines().forEach(line -> {
            if (line != null) inventory.dispatch(siteStore, line.getItem(), line.getQuantity());
        });
        return returns.save(stockReturn);
    }

    @Transactional
    public StockReturn confirm(StockReturn stockReturn, ConfirmReturnRequest req) {
        stockReturn.setStatus(ReturnStatus.CONFIRMED);
        MaterialIssueVoucher miv = stockReturn.getMiv();
        Store siteStore = miv != null ? miv.getProject().getSiteStore() : null;
        Store centralStore = miv != null ? miv.getStore() : stockReturn.getStore();

        stockReturn.getLines().forEach(line -> {
            if (line == null) return;
            BigDecimal expectedQuantity = line.getQuantity();
            BigDecimal receivedQuantity = req.lines().stream()
                    .filter(cl -> cl.itemId().equals(line.getItem().getId()))
                    .findFirst()
                    .map(ConfirmLineRequest::receivedQuantity)
                    .orElse(BigDecimal.ZERO);

            if (miv != null) {
                miv.getLines().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(mivLine -> mivLine.getItem() == line.getItem())
                        .findFirst()
                        .ifPresent(mivLine -> mivLine.addReturn(expectedQuantity));
            }

            if (receivedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                inventory.completeTransit(siteStore, line.getItem(), receivedQuantity);
                if (line.getCondition() == ReturnCondition.SERVICEABLE) {
                    inventory.receive(centralStore, line.getItem(), receivedQuantity);
                } else if (line.getCondition() == ReturnCondition.UNSERVICEABLE) {
                    inventory.receiveDamaged(centralStore, line.getItem(), receivedQuantity);
                }
            }

            if (expectedQuantity.compareTo(receivedQuantity) > 0) {
                BigDecimal variance = expectedQuantity.subtract(receivedQuantity);
                inventory.freezeTransitVariance(siteStore, line.getItem(), variance);
                discrepancies.save(new Discrepancy(stockReturn, line.getItem(), expectedQuantity, receivedQuantity));
            }
        });

        if (miv != null) miv.markPartiallyReturned();
        return returns.save(stockReturn);
    }
}
