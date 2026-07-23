package com.izonehub.stores.reporting;

import com.izonehub.stores.config.CompanyProperties;
import com.izonehub.stores.count.StockCount;
import com.izonehub.stores.count.StockCountLine;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class StockCountPdfService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final CompanyProperties company;
    private final com.izonehub.stores.audit.AuditLogRepository auditLogRepo;

    public StockCountPdfService(CompanyProperties company, com.izonehub.stores.audit.AuditLogRepository auditLogRepo) {
        this.company = company;
        this.auditLogRepo = auditLogRepo;
    }

    public byte[] generate(StockCount count) {
        Color brand  = brandColor();
        Color gray   = new Color(110, 110, 110);
        Color rowAlt = new Color(247, 247, 247);

        Font titleFont        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 30, 30));
        Font refFont          = FontFactory.getFont(FontFactory.HELVETICA, 9, gray);
        Font labelFont        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, gray);
        Font valueFont        = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 30, 30));
        Font tableHeaderFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tableCellFont    = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(40, 40, 40));
        Font tableCellBoldFont= FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(40, 40, 40));

        float topMargin = PdfLetterheadFooterEvent.HEADER_HEIGHT + 10f;
        float botMargin = PdfLetterheadFooterEvent.FOOTER_HEIGHT + 10f;

        Document doc = new Document(PageSize.A4, 40, 40, topMargin, botMargin);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfLetterheadFooterEvent(company));
            doc.open();

            // ── Title + reference ───────────────────────────────────────
            PdfPTable titleRow = new PdfPTable(2);
            titleRow.setWidthPercentage(100);
            titleRow.setWidths(new float[]{1.5f, 1f});

            PdfPCell titleCell = borderless();
            titleCell.addElement(new Paragraph("STOCK COUNT AUDIT REPORT", titleFont));
            titleRow.addCell(titleCell);

            PdfPCell refCell = borderless();
            refCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            refCell.addElement(rightAligned("Audit No.  AC-" + shortId(count.getId()), refFont));
            refCell.addElement(rightAligned("Date  " + DATE_FMT.format(count.getCreatedAt()), refFont));
            titleRow.addCell(refCell);
            doc.add(titleRow);
            doc.add(spacer(14));

            // ── Store / Status meta ────────────────────────────────
            PdfPTable meta = new PdfPTable(3);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1f, 1f, 1f});
            meta.addCell(metaCell("STORE",        count.getStore().getName(),              labelFont, valueFont));
            meta.addCell(metaCell("INITIATED BY", count.getInitiatedBy().getFullName(),    labelFont, valueFont));
            meta.addCell(metaCell("STATUS",       count.getStatus().name(),                labelFont, valueFont));
            doc.add(meta);
            doc.add(spacer(16));

            // ── Item table ───────────────────────────────────────────────
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.5f, 3.2f, 1f, 1f, 1f});

            table.addCell(headerCell("#",            tableHeaderFont, brand, Element.ALIGN_CENTER));
            table.addCell(headerCell("ITEM",         tableHeaderFont, brand, Element.ALIGN_LEFT));
            table.addCell(headerCell("SYSTEM QTY",   tableHeaderFont, brand, Element.ALIGN_RIGHT));
            table.addCell(headerCell("PHYSICAL QTY", tableHeaderFont, brand, Element.ALIGN_RIGHT));
            table.addCell(headerCell("VARIANCE",     tableHeaderFont, brand, Element.ALIGN_RIGHT));

            List<StockCountLine> lines = count.getLines();
            for (int i = 0; i < lines.size(); i++) {
                StockCountLine line = lines.get(i);
                Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;
                table.addCell(bodyCell(String.valueOf(i + 1), tableCellFont, bg, Element.ALIGN_CENTER));
                table.addCell(bodyCell(line.getItem().getName(), tableCellFont, bg, Element.ALIGN_LEFT));
                table.addCell(bodyCell(
                        line.getSystemQuantitySnapshot().stripTrailingZeros().toPlainString(),
                        tableCellFont, bg, Element.ALIGN_RIGHT));

                String physQty = line.getPhysicalQuantity() != null
                        ? line.getPhysicalQuantity().stripTrailingZeros().toPlainString()
                        : "—";
                table.addCell(bodyCell(physQty, tableCellFont, bg, Element.ALIGN_RIGHT));

                BigDecimal variance   = line.getVarianceQuantity();
                String varianceStr    = variance != null ? variance.stripTrailingZeros().toPlainString() : "—";
                Font   varFont        = (variance != null && variance.compareTo(BigDecimal.ZERO) != 0)
                        ? tableCellBoldFont : tableCellFont;
                table.addCell(bodyCell(varianceStr, varFont, bg, Element.ALIGN_RIGHT));
            }
            doc.add(table);
            doc.add(spacer(30));

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate stock count audit PDF", e);
        }

        return out.toByteArray();
    }

    public byte[] generateFullAudit(StockCount count) {
        Color brand = brandColor();
        Color gray  = new Color(110, 110, 110);
        Color rowAlt= new Color(247, 247, 247);

        Font titleFont      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 30, 30));
        Font refFont        = FontFactory.getFont(FontFactory.HELVETICA, 9, gray);
        Font tableHeaderFont= FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tableCellFont  = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(40, 40, 40));

        float topMargin = PdfLetterheadFooterEvent.HEADER_HEIGHT + 10f;
        float botMargin = PdfLetterheadFooterEvent.FOOTER_HEIGHT + 10f;

        Document doc = new Document(PageSize.A4, 40, 40, topMargin, botMargin);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfLetterheadFooterEvent(company));
            doc.open();

            Paragraph title = new Paragraph("COMPREHENSIVE SKU AUDIT REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            doc.add(title);

            Paragraph subtitle = new Paragraph(
                    "Stock Count Ref: AC-" + shortId(count.getId()) + " | Store: " + count.getStore().getName(),
                    refFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            List<com.izonehub.stores.audit.AuditLog> storeLogs =
                    auditLogRepo.findByEntityTypeAndEntityIdOrderByPerformedAtDesc(
                            "INVENTORY", count.getStore().getId().toString());

            for (StockCountLine line : count.getLines()) {
                doc.add(new Paragraph(
                        "SKU: " + line.getItem().getName() + " (" + line.getItem().getCode() + ")",
                        titleFont));
                doc.add(spacer(10));

                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1f, 3f, 1f});
                table.addCell(headerCell("Date",              tableHeaderFont, brand, Element.ALIGN_LEFT));
                table.addCell(headerCell("Event Description", tableHeaderFont, brand, Element.ALIGN_LEFT));
                table.addCell(headerCell("Performed By",      tableHeaderFont, brand, Element.ALIGN_LEFT));

                List<com.izonehub.stores.audit.AuditLog> skuLogs = storeLogs.stream()
                        .filter(l -> l.getDescription() != null
                                && l.getDescription().contains(line.getItem().getName()))
                        .toList();

                if (skuLogs.isEmpty()) {
                    PdfPCell empty = bodyCell("No recent history found.", tableCellFont, Color.WHITE, Element.ALIGN_CENTER);
                    empty.setColspan(3);
                    table.addCell(empty);
                } else {
                    for (int i = 0; i < skuLogs.size(); i++) {
                        com.izonehub.stores.audit.AuditLog lg = skuLogs.get(i);
                        Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;
                        table.addCell(bodyCell(DATE_FMT.format(lg.getPerformedAt()), tableCellFont, bg, Element.ALIGN_LEFT));
                        table.addCell(bodyCell(lg.getDescription(),   tableCellFont, bg, Element.ALIGN_LEFT));
                        table.addCell(bodyCell(lg.getPerformedBy(),   tableCellFont, bg, Element.ALIGN_LEFT));
                    }
                }
                doc.add(table);
                doc.add(spacer(25));
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate audit PDF", e);
        }
        return out.toByteArray();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Color brandColor() {
        try {
            return Color.decode(company.getBrandColor());
        } catch (Exception e) {
            return new Color(31, 122, 61);
        }
    }

    private String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    private Paragraph rightAligned(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private PdfPCell borderless() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private PdfPTable spacer(float height) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(height);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
        return t;
    }

    private PdfPCell metaCell(String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(10);
        Paragraph l = new Paragraph(label, labelFont);
        l.setSpacingAfter(2);
        cell.addElement(l);
        cell.addElement(new Paragraph(value, valueFont));
        return cell;
    }

    private PdfPCell headerCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private PdfPCell bodyCell(String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBorderColor(new Color(230, 230, 230));
        cell.setBorderWidthTop(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setBorderWidthBottom(0.5f);
        return cell;
    }
}
