package com.izonehub.stores.reporting;

import com.izonehub.stores.receipt.ExpectedReceiptLine;
import com.izonehub.stores.receipt.GoodsReceivedNote;
import com.izonehub.stores.receipt.GoodsReceivedNoteRepository;
import com.izonehub.stores.receipt.ReceiptLineCondition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes supplier performance directly from real receiving history
 * (GoodsReceivedNote -> ExpectedReceipt -> ExpectedReceiptLine) instead of
 * a hand-maintained score. There is currently no foreign key from
 * ExpectedReceipt to the Supplier master-data table — ExpectedReceipt only
 * stores supplierName as free text — so this groups by that name. If two
 * suppliers are ever entered with the same free-text name, or the same
 * supplier is entered with different spellings, this will under/over-count.
 * The right long-term fix is giving ExpectedReceipt a real supplierId FK;
 * this service works with what the schema currently has.
 */
@Service
public class SupplierPerformanceService {

    private final GoodsReceivedNoteRepository grns;

    public SupplierPerformanceService(GoodsReceivedNoteRepository grns) {
        this.grns = grns;
    }

    @Transactional(readOnly = true)
    public List<SupplierPerformanceRow> compute() {
        List<GoodsReceivedNote> all = grns.findAllWithReceiptAndLines();

        Map<String, List<GoodsReceivedNote>> bySupplier = all.stream()
                .filter(g -> g.getExpectedReceipt() != null && g.getExpectedReceipt().getSupplierName() != null)
                .collect(Collectors.groupingBy(g -> g.getExpectedReceipt().getSupplierName()));

        List<SupplierPerformanceRow> rows = new ArrayList<>();
        for (var entry : bySupplier.entrySet()) {
            String supplierName = entry.getKey();
            List<GoodsReceivedNote> supplierGrns = entry.getValue();

            List<ExpectedReceiptLine> lines = supplierGrns.stream()
                    .flatMap(g -> g.getExpectedReceipt().getLines().stream())
                    .toList();

            BigDecimal totalExpected = lines.stream()
                    .map(ExpectedReceiptLine::getExpectedQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalReceived = lines.stream()
                    .map(ExpectedReceiptLine::getReceivedQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalLines = lines.size();
            long accurateLines = lines.stream()
                    .filter(l -> l.getCondition() == ReceiptLineCondition.GOOD
                            && l.getReceivedQuantity().compareTo(l.getExpectedQuantity()) == 0)
                    .count();
            long defectiveLines = lines.stream()
                    .filter(l -> l.getCondition() == ReceiptLineCondition.DAMAGED)
                    .count();

            BigDecimal avgLeadTimeDays = supplierGrns.stream()
                    .filter(g -> g.getExpectedReceipt().getCreatedAt() != null && g.getReceivedAt() != null)
                    .map(g -> BigDecimal.valueOf(
                            Duration.between(g.getExpectedReceipt().getCreatedAt(), g.getReceivedAt()).toHours() / 24.0))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(supplierGrns.size(), 1)), 1, RoundingMode.HALF_UP);

            rows.add(new SupplierPerformanceRow(
                    supplierName,
                    supplierGrns.size(),
                    percent(accurateLines, totalLines),
                    percent(totalReceived, totalExpected),
                    percent(defectiveLines, totalLines),
                    avgLeadTimeDays,
                    monthlyAccuracyTrend(supplierGrns)
            ));
        }

        rows.sort(Comparator.comparing(SupplierPerformanceRow::supplierName));
        return rows;
    }

    private List<SupplierPerformanceRow.MonthlyAccuracy> monthlyAccuracyTrend(List<GoodsReceivedNote> supplierGrns) {
        Map<String, List<GoodsReceivedNote>> byMonth = supplierGrns.stream()
                .filter(g -> g.getReceivedAt() != null)
                .collect(Collectors.groupingBy(g -> {
                    var d = g.getReceivedAt().atZone(ZoneOffset.UTC);
                    return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
                }));

        return byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<ExpectedReceiptLine> monthLines = e.getValue().stream()
                            .flatMap(g -> g.getExpectedReceipt().getLines().stream())
                            .toList();
                    long total = monthLines.size();
                    long accurate = monthLines.stream()
                            .filter(l -> l.getCondition() == ReceiptLineCondition.GOOD
                                    && l.getReceivedQuantity().compareTo(l.getExpectedQuantity()) == 0)
                            .count();
                    String[] parts = e.getKey().split("-");
                    int monthValue = Integer.parseInt(parts[1]);
                    String label = java.time.Month.of(monthValue).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    return new SupplierPerformanceRow.MonthlyAccuracy(label, percent(accurate, total));
                })
                .toList();
    }

    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP);
    }
}
