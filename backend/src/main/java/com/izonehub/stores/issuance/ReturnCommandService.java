package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryCommandService;
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
    public StockReturn confirm(MaterialIssueVoucher miv, StockReturn stockReturn) {
        stockReturn.getLines().forEach(line -> {
            miv.getLines().stream()
                    .filter(mivLine -> mivLine.getItem() == line.getItem())
                    .findFirst()
                    .ifPresent(mivLine -> mivLine.addReturn(line.getQuantity()));
            if (line.getCondition() == ReturnCondition.SERVICEABLE) {
                inventory.receive(miv.getStore(), line.getItem(), line.getQuantity());
            }
        });
        miv.markPartiallyReturned();
        return returns.save(stockReturn);
    }
}
