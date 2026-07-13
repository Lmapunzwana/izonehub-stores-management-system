package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.store.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnCommandService {
    private final StockReturnRepository returns;
    private final InventoryCommandService inventory;

    public ReturnCommandService(StockReturnRepository returns, InventoryCommandService inventory) {
        this.returns = returns;
        this.inventory = inventory;
    }

    @Transactional
    public StockReturn createPendingReturn(MaterialIssueVoucher miv, StockReturn stockReturn) {
        stockReturn.setStatus(ReturnStatus.PENDING_CONFIRMATION);
        return returns.save(stockReturn);
    }

    @Transactional
    public StockReturn confirm(StockReturn stockReturn) {
        stockReturn.setStatus(ReturnStatus.CONFIRMED);
        MaterialIssueVoucher miv = stockReturn.getMiv();
        stockReturn.getLines().forEach(line -> {
            if (miv != null) {
                miv.getLines().stream()
                        .filter(mivLine -> mivLine.getItem() == line.getItem())
                        .findFirst()
                        .ifPresent(mivLine -> mivLine.addReturn(line.getQuantity()));
            }
            if (line.getCondition() == ReturnCondition.SERVICEABLE) {
                Store targetStore = miv != null ? miv.getStore() : stockReturn.getStore();
                inventory.receive(targetStore, line.getItem(), line.getQuantity());
            }
        });
        if (miv != null) miv.markPartiallyReturned();
        return returns.save(stockReturn);
    }
}
