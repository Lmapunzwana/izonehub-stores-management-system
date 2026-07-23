package com.izonehub.stores.reporting;

import com.izonehub.stores.config.CompanyProperties;
import com.izonehub.stores.receipt.GoodsReceivedNote;
import com.izonehub.stores.receipt.ExpectedReceipt;
import com.izonehub.stores.receipt.ExpectedReceiptLine;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class GrnNoteService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final CompanyProperties company;

    public GrnNoteService(CompanyProperties company) {
        this.company = company;
    }

    public byte[] generate(GoodsReceivedNote grn) {
        ExpectedReceipt request = grn.getExpectedReceipt();
        Color brand  = brandColor();
        Color gray   = new Color(110, 110, 110);
        Color rowAlt = new Color(247, 247, 247);

        Font titleFont       = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 30, 30));
        Font refFont         = FontFactory.getFont(FontFactory.HELVETICA, 9, gray);
        Font labelFont       = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, gray);
        Font valueFont       = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 30, 30));
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tableCellFont   = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(40, 40, 40));
        Font sigLabelFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, gray);
        Font sigValueFont    = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 30, 30));

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
            titleCell.addElement(new Paragraph("GOODS RECEIVED NOTE", titleFont));
            titleRow.addCell(titleCell);

            PdfPCell refCell = borderless();
            refCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            refCell.addElement(rightAligned("GRN No.  " + grn.getReferenceNumber(), refFont));
            refCell.addElement(rightAligned("Date  " + DATE_FMT.format(grn.getReceivedAt()), refFont));
            titleRow.addCell(refCell);
            doc.add(titleRow);
            doc.add(spacer(14));

            // ── From / To / Meta ────────────────────────────────
            PdfPTable meta = new PdfPTable(3);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1f, 1f, 1f});
            meta.addCell(metaCell("SUPPLIER", request.getSupplierName(), labelFont, valueFont));
            meta.addCell(metaCell("RECEIVING STORE", grn.getStore().getName(), labelFont, valueFont));
            meta.addCell(metaCell("STATUS", grn.getStatus().name(), labelFont, valueFont));
            doc.add(meta);
            doc.add(spacer(16));

            // ── Item table ───────────────────────────────────────────────
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.5f, 2.5f, 0.9f, 1f, 1.2f});

            table.addCell(headerCell("#",           tableHeaderFont, brand, Element.ALIGN_CENTER));
            table.addCell(headerCell("DESCRIPTION", tableHeaderFont, brand, Element.ALIGN_LEFT));
            table.addCell(headerCell("UOM",         tableHeaderFont, brand, Element.ALIGN_CENTER));
            table.addCell(headerCell("QTY RCVD",   tableHeaderFont, brand, Element.ALIGN_RIGHT));
            table.addCell(headerCell("CONDITION",   tableHeaderFont, brand, Element.ALIGN_CENTER));

            List<ExpectedReceiptLine> lines = request.getLines();
            for (int i = 0; i < lines.size(); i++) {
                ExpectedReceiptLine line = lines.get(i);
                Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;
                table.addCell(bodyCell(String.valueOf(i + 1), tableCellFont, bg, Element.ALIGN_CENTER));
                table.addCell(bodyCell(line.getItem().getName(), tableCellFont, bg, Element.ALIGN_LEFT));
                table.addCell(bodyCell(line.getItem().getUnitOfMeasure(), tableCellFont, bg, Element.ALIGN_CENTER));
                table.addCell(bodyCell(
                        line.getReceivedQuantity() != null
                                ? line.getReceivedQuantity().stripTrailingZeros().toPlainString()
                                : "0",
                        tableCellFont, bg, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(
                        line.getCondition() != null ? line.getCondition().name() : "-",
                        tableCellFont, bg, Element.ALIGN_CENTER));
            }
            doc.add(table);
            doc.add(spacer(30));

            // ── Signatures ───────────────────────────────────────────────
            PdfPTable sig = new PdfPTable(2);
            sig.setWidthPercentage(100);
            sig.setWidths(new float[]{1f, 1f});
            sig.addCell(signatureCell("RECEIVED BY", grn.getReceivedBy().getFullName(), sigLabelFont, sigValueFont));
            sig.addCell(signatureCell("SUPPLIER REP", "____________________", sigLabelFont, sigValueFont));
            doc.add(sig);

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate GRN note PDF", e);
        }

        return out.toByteArray();
    }

    private Color brandColor() {
        try {
            return Color.decode(company.getBrandColor());
        } catch (Exception e) {
            return new Color(31, 122, 61);
        }
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

    private PdfPCell signatureCell(String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingRight(14);
        Paragraph l = new Paragraph(label, labelFont);
        l.setSpacingAfter(18);
        cell.addElement(l);
        cell.addElement(new Paragraph(value, valueFont));
        return cell;
    }
}
