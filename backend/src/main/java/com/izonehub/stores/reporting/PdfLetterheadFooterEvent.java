package com.izonehub.stores.reporting;

import com.izonehub.stores.config.CompanyProperties;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;

/**
 * PdfPageEventHelper that renders:
 *  - A branded letterhead (logo + contact details) at the top of every page.
 *  - A branded footer strip pinned to the absolute bottom of every page.
 *
 * Usage:
 *   PdfWriter writer = PdfWriter.getInstance(doc, out);
 *   writer.setPageEvent(new PdfLetterheadFooterEvent(company));
 *   doc.open();
 *   // ... add content (no need to add letterhead or footer manually) ...
 *   doc.close();
 */
public class PdfLetterheadFooterEvent extends PdfPageEventHelper {

    private final CompanyProperties company;
    private final Color brand;
    private final Color lightBrand;

    // Letterhead height reservation — the document top margin must be >= this
    // so that body content starts below the letterhead.
    static final float HEADER_HEIGHT = 80f;
    // Footer height reservation — the document bottom margin must be >= this.
    static final float FOOTER_HEIGHT = 30f;

    public PdfLetterheadFooterEvent(CompanyProperties company) {
        this.company = company;
        this.brand = brandColor();
        this.lightBrand = tint(brand, 0.88f);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContent();
        Rectangle page = document.getPageSize();

        // ── Letterhead (top of page) ──────────────────────────────────────
        drawLetterhead(canvas, page, writer);

        // ── Footer (bottom of page) ───────────────────────────────────────
        drawFooter(canvas, page);
    }

    private void drawLetterhead(PdfContentByte canvas, Rectangle page, PdfWriter writer) {
        float pageWidth  = page.getWidth();
        float pageHeight = page.getHeight();
        float margin     = 40f;

        // Brand rule — 1.6 pt line just below the letterhead area
        float ruleY = pageHeight - HEADER_HEIGHT + 4;
        canvas.saveState();
        canvas.setColorFill(brand);
        canvas.rectangle(margin, ruleY, pageWidth - 2 * margin, 1.6f);
        canvas.fill();
        canvas.restoreState();

        // Logo or wordmark on the left
        float logoY = pageHeight - HEADER_HEIGHT + 10;
        Image logo = tryLoadLogo();
        if (logo != null) {
            logo.scaleToFit(140, 46);
            logo.setAbsolutePosition(margin, logoY);
            try { canvas.addImage(logo); } catch (DocumentException ignored) {}
        } else {
            Font wordmarkFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brand);
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase(safe(company.getName(), "Company"), wordmarkFont),
                margin, logoY + 14, 0);
        }

        // Contact details on the right
        Font smallGray = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(110, 110, 110));
        float rightX = pageWidth - margin;
        float lineH  = 10f;
        float startY = pageHeight - 40f;
        float y = startY;

        ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
            new Phrase(safe(company.getAddressLine1(), ""), smallGray), rightX, y, 0);
        if (notBlank(company.getAddressLine2())) {
            y -= lineH;
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                new Phrase(company.getAddressLine2(), smallGray), rightX, y, 0);
        }
        if (notBlank(company.getPhone())) {
            y -= lineH;
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                new Phrase(company.getPhone(), smallGray), rightX, y, 0);
        }
        if (notBlank(company.getEmail())) {
            y -= lineH;
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                new Phrase(company.getEmail(), smallGray), rightX, y, 0);
        }
        if (notBlank(company.getWebsite())) {
            y -= lineH;
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                new Phrase(company.getWebsite(), smallGray), rightX, y, 0);
        }
    }

    private void drawFooter(PdfContentByte canvas, Rectangle page) {
        float pageWidth = page.getWidth();
        float margin    = 40f;
        float footerY   = 28f; // distance from very bottom of page

        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(110, 110, 110));

        // Thin brand-tinted rule above footer text
        canvas.saveState();
        canvas.setColorFill(lightBrand);
        canvas.rectangle(margin, footerY + 12f, pageWidth - 2 * margin, 0.8f);
        canvas.fill();
        canvas.restoreState();

        // Footer text centred
        StringBuilder footerText = new StringBuilder(safe(company.getName(), ""));
        if (notBlank(company.getAddressLine1()))
            footerText.append("   |   ").append(company.getAddressLine1());
        if (notBlank(company.getPhone()))
            footerText.append("   |   ").append(company.getPhone());

        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
            new Phrase(footerText.toString(), footerFont),
            pageWidth / 2f, footerY, 0);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Color brandColor() {
        try {
            return Color.decode(company.getBrandColor());
        } catch (Exception e) {
            return new Color(31, 122, 61);
        }
    }

    private Color tint(Color base, float towardWhite) {
        int r = (int) (base.getRed()   + (255 - base.getRed())   * towardWhite);
        int g = (int) (base.getGreen() + (255 - base.getGreen()) * towardWhite);
        int b = (int) (base.getBlue()  + (255 - base.getBlue())  * towardWhite);
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

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }
}
