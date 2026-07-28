package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreType;
import com.izonehub.stores.store.StoreRepository;
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
    private final StoreRepository storeRepo;

    public ReturnCommandService(StockReturnRepository returns, InventoryCommandService inventory,
                                DiscrepancyRepository discrepancies, StoreRepository storeRepo) {
        this.returns = returns;
        this.inventory = inventory;
        this.discrepancies = discrepancies;
        this.storeRepo = storeRepo;
    }

    @Transactional
    public StockReturn createPendingReturn(MaterialIssueVoucher miv, StockReturn stockReturn) {
        stockReturn.setStatus(ReturnStatus.PENDING_CONFIRMATION);
        Store siteStore = (miv != null && miv.getProject() != null) ? miv.getProject().getSiteStore() : stockReturn.getStore();
        if (siteStore != null) {
            stockReturn.getLines().forEach(line -> {
                if (line != null) inventory.dispatch(siteStore, line.getItem(), line.getQuantity());
            });
        }
        return returns.save(stockReturn);
    }

    @Transactional
    public StockReturn confirm(StockReturn stockReturn, ConfirmReturnRequest req) {
        stockReturn.setStatus(ReturnStatus.CONFIRMED);
        MaterialIssueVoucher miv = stockReturn.getMiv();

        Store centralStore = miv != null ? miv.getStore() : storeRepo.findByType(StoreType.CENTRAL).stream().findFirst().orElse(stockReturn.getStore());
        Store siteStore = miv != null ? miv.getProject().getSiteStore() : stockReturn.getStore();

        stockReturn.getLines().forEach(line -> {
            if (line == null || line.getItem() == null) return;
            BigDecimal expectedQuantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            
            BigDecimal receivedQuantity;
            if (req != null && req.lines() != null && !req.lines().isEmpty()) {
                receivedQuantity = req.lines().stream()
                        .filter(cl -> cl != null && cl.itemId() != null && cl.itemId().equals(line.getItem().getId()))
                        .findFirst()
                        .map(cl -> cl.receivedQuantity() != null ? cl.receivedQuantity() : expectedQuantity)
                        .orElse(expectedQuantity);
            } else {
                receivedQuantity = expectedQuantity;
            }

            if (miv != null) {
                miv.getLines().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(mivLine -> mivLine.getItem() == line.getItem())
                        .findFirst()
                        .ifPresent(mivLine -> mivLine.addReturn(expectedQuantity));
            }

            if (receivedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                if (siteStore != null) {
                    inventory.completeTransit(siteStore, line.getItem(), receivedQuantity);
                }
                if (centralStore != null) {
                    if (line.getCondition() == ReturnCondition.SERVICEABLE) {
                        inventory.receive(centralStore, line.getItem(), receivedQuantity);
                    } else if (line.getCondition() == ReturnCondition.UNSERVICEABLE) {
                        inventory.receiveDamaged(centralStore, line.getItem(), receivedQuantity);
                    }
                }
            }

            if (expectedQuantity.compareTo(receivedQuantity) > 0) {
                BigDecimal variance = expectedQuantity.subtract(receivedQuantity);
                if (siteStore != null) {
                    inventory.freezeTransitVariance(siteStore, line.getItem(), variance);
                }
                discrepancies.save(new Discrepancy(stockReturn, line.getItem(), expectedQuantity, receivedQuantity));
            }
        });

        if (miv != null) miv.markPartiallyReturned();
        return returns.save(stockReturn);
    }
}
