package com.izonehub.stores.reporting;

import com.izonehub.stores.config.CompanyProperties;
import com.izonehub.stores.movement.Dispatch;
import com.izonehub.stores.movement.MaterialRequest;
import com.izonehub.stores.movement.MaterialRequestLine;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a branded, printable PDF dispatch note for a completed Dispatch.
 * Layout: letterhead -> title/reference block -> from/to + project meta ->
 * line-item table -> signature blocks -> footer strip.
 */
@Service
public class DispatchNoteService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final CompanyProperties company;

    public DispatchNoteService(CompanyProperties company) {
        this.company = company;
    }

    public byte[] generate(Dispatch dispatch) {
        MaterialRequest request = dispatch.getMaterialRequest();
        Color brand = brandColor();
        Color lightBrand = tint(brand, 0.88f);
        Color gray = new Color(110, 110, 110);
        Color rowAlt = new Color(247, 247, 247);

        Font wordmarkFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brand);
        Font smallGray = FontFactory.getFont(FontFactory.HELVETICA, 8, gray);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 30, 30));
        Font refFont = FontFactory.getFont(FontFactory.HELVETICA, 9, gray);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, gray);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 30, 30));
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tableCellFont = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(40, 40, 40));
        Font sigLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, gray);
        Font sigValueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 30, 30));
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, gray);

        Document doc = new Document(PageSize.A4, 40, 40, 34, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── Letterhead ──────────────────────────────────────────────
            PdfPTable letterhead = new PdfPTable(2);
            letterhead.setWidthPercentage(100);
            letterhead.setWidths(new float[]{1.3f, 1f});

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            Image logo = tryLoadLogo();
            if (logo != null) {
                logo.scaleToFit(140, 46);
                logoCell.addElement(logo);
            } else {
                Paragraph wordmark = new Paragraph(safe(company.getName(), "Company Name"), wordmarkFont);
                logoCell.addElement(wordmark);
            }
            letterhead.addCell(logoCell);

            PdfPCell contactCell = new PdfPCell();
            contactCell.setBorder(Rectangle.NO_BORDER);
            contactCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            contactCell.addElement(rightAligned(safe(company.getAddressLine1(), ""), smallGray));
            if (notBlank(company.getAddressLine2())) contactCell.addElement(rightAligned(company.getAddressLine2(), smallGray));
            if (notBlank(company.getPhone())) contactCell.addElement(rightAligned(company.getPhone(), smallGray));
            if (notBlank(company.getEmail())) contactCell.addElement(rightAligned(company.getEmail(), smallGray));
            if (notBlank(company.getWebsite())) contactCell.addElement(rightAligned(company.getWebsite(), smallGray));
            letterhead.addCell(contactCell);
            doc.add(letterhead);

            doc.add(rule(brand, 1.6f, 10, 14));

            // ── Title + reference ───────────────────────────────────────
            PdfPTable titleRow = new PdfPTable(2);
            titleRow.setWidthPercentage(100);
            titleRow.setWidths(new float[]{1.5f, 1f});

            PdfPCell titleCell = borderless();
            titleCell.addElement(new Paragraph("DISPATCH NOTE", titleFont));
            titleRow.addCell(titleCell);

            PdfPCell refCell = borderless();
            refCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            refCell.addElement(rightAligned("Dispatch No.  DN-" + shortId(dispatch), refFont));
            refCell.addElement(rightAligned("Date  " + DATE_FMT.format(dispatch.getDispatchedAt()), refFont));
            titleRow.addCell(refCell);
            doc.add(titleRow);
            doc.add(spacer(14));

            // ── From / To / Project meta ────────────────────────────────
            PdfPTable meta = new PdfPTable(3);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1f, 1f, 1f});
            meta.addCell(metaCell("FROM", request.getSourceStore().getName(), labelFont, valueFont));
            meta.addCell(metaCell("TO", request.getRequestingStore().getName(), labelFont, valueFont));
            meta.addCell(metaCell("PROJECT / REF", request.getProject() != null ? safe(request.getProject().getCode(), "—") : "—", labelFont, valueFont));
            doc.add(meta);
            doc.add(spacer(16));

            // ── Item table ───────────────────────────────────────────────
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.5f, 3.2f, 0.9f, 1f});

            table.addCell(headerCell("#", tableHeaderFont, brand, Element.ALIGN_CENTER));
            table.addCell(headerCell("DESCRIPTION", tableHeaderFont, brand, Element.ALIGN_LEFT));
            table.addCell(headerCell("UOM", tableHeaderFont, brand, Element.ALIGN_CENTER));
            table.addCell(headerCell("QUANTITY", tableHeaderFont, brand, Element.ALIGN_RIGHT));

            List<MaterialRequestLine> lines = request.getLines();
            for (int i = 0; i < lines.size(); i++) {
                MaterialRequestLine line = lines.get(i);
                Color bg = (i % 2 == 0) ? Color.WHITE : rowAlt;
                table.addCell(bodyCell(String.valueOf(i + 1), tableCellFont, bg, Element.ALIGN_CENTER));
                table.addCell(bodyCell(line.getItem().getName(), tableCellFont, bg, Element.ALIGN_LEFT));
                table.addCell(bodyCell(line.getItem().getUnitOfMeasure(), tableCellFont, bg, Element.ALIGN_CENTER));
                table.addCell(bodyCell(line.getDispatchedQuantity().stripTrailingZeros().toPlainString(),
                        tableCellFont, bg, Element.ALIGN_RIGHT));
            }
            doc.add(table);
            doc.add(spacer(30));

            // ── Signatures (Row 1): Requested By | Issued By | Dispatched By ──────
            PdfPTable sig1 = new PdfPTable(3);
            sig1.setWidthPercentage(100);
            sig1.setWidths(new float[]{1f, 1f, 1f});
            sig1.addCell(signatureCell("REQUESTED BY",
                    request.getRaisedBy() != null ? request.getRaisedBy().getFullName() : "—",
                    sigLabelFont, sigValueFont));
            sig1.addCell(signatureCell("ISSUED BY",
                    request.getApprovedBy() != null ? request.getApprovedBy().getFullName() : "—",
                    sigLabelFont, sigValueFont));
            sig1.addCell(signatureCell("DISPATCHED BY",
                    dispatch.getDispatchedBy() != null ? dispatch.getDispatchedBy().getFullName() : "—",
                    sigLabelFont, sigValueFont));
            doc.add(sig1);
            doc.add(spacer(16));

            // ── Signatures (Row 2): Collected By | Received By (blank) ───────────
            PdfPTable sig2 = new PdfPTable(2);
            sig2.setWidthPercentage(100);
            sig2.setWidths(new float[]{1f, 1f});
            sig2.addCell(signatureCell("COLLECTED BY",
                    dispatch.getCollectorName() + "  (ID: " + dispatch.getCollectorEmployeeId() + ")",
                    sigLabelFont, sigValueFont));
            sig2.addCell(signatureCellBlank("RECEIVED BY\n(Full Name & Signature)", sigLabelFont));
            doc.add(sig2);


            // ── Footer strip ─────────────────────────────────────────────
            doc.add(spacer(28));
            doc.add(rule(lightBrand, 0.8f, 0, 6));
            Paragraph footer = new Paragraph();
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.add(new Chunk(safe(company.getName(), ""), footerFont));
            if (notBlank(company.getAddressLine1())) footer.add(new Chunk("   |   " + company.getAddressLine1(), footerFont));
            if (notBlank(company.getPhone())) footer.add(new Chunk("   |   " + company.getPhone(), footerFont));
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate dispatch note PDF", e);
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

    private Color tint(Color base, float towardWhite) {
        int r = (int) (base.getRed() + (255 - base.getRed()) * towardWhite);
        int g = (int) (base.getGreen() + (255 - base.getGreen()) * towardWhite);
        int b = (int) (base.getBlue() + (255 - base.getBlue()) * towardWhite);
        return new Color(r, g, b);
    }

    private Image tryLoadLogo() {
        String path = company.getLogoClasspath();
        if (path == null || path.isBlank()) return null;
        try {
            byte[] bytes = new ClassPathResource(path).getInputStream().readAllBytes();
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String shortId(Dispatch dispatch) {
        return dispatch.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
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

    private PdfPTable rule(Color color, float thickness, float spaceBefore, float spaceAfter) {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(thickness);
        cell.setBackgroundColor(color);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        line.setSpacingBefore(spaceBefore);
        line.setSpacingAfter(spaceAfter);
        return line;
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

    /**
     * A blank signature cell — shows the label, then a horizontal line for
     * the recipient to write their full name and sign by hand.
     */
    private PdfPCell signatureCellBlank(String label, Font labelFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingRight(14);
        Paragraph l = new Paragraph(label, labelFont);
        l.setSpacingAfter(6);
        cell.addElement(l);
        // Draw a solid underline for the handwritten signature area
        PdfPTable underline = new PdfPTable(1);
        underline.setWidthPercentage(90);
        PdfPCell line = new PdfPCell();
        line.setFixedHeight(1f);
        line.setBackgroundColor(new Color(180, 180, 180));
        line.setBorder(Rectangle.NO_BORDER);
        underline.addCell(line);
        cell.addElement(underline);
        cell.addElement(new Paragraph(" ", labelFont)); // spacer
        return cell;
    }
}

