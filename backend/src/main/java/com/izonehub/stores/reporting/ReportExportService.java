package com.izonehub.stores.reporting;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ReportExportService {
    public byte[] currentStockCsv(List<CurrentStockRow> rows) {
        StringBuilder csv = new StringBuilder("store,item_code,item_name,on_hand,in_transit,frozen,damaged\n");
        rows.forEach(row -> csv.append(escape(row.storeName())).append(',')
                .append(escape(row.itemCode())).append(',')
                .append(escape(row.itemName())).append(',')
                .append(row.onHand()).append(',')
                .append(row.inTransit()).append(',')
                .append(row.frozen()).append(',')
                .append(row.damaged()).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
