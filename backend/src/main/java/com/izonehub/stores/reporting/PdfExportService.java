package com.izonehub.stores.reporting;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PdfExportService {

    public byte[] generatePdfReport(String title, String dateRangeStr, List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font dateFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font rowFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(10);
            doc.add(titlePara);

            if (dateRangeStr != null && !dateRangeStr.isBlank()) {
                Paragraph datePara = new Paragraph("Date Range: " + dateRangeStr, dateFont);
                datePara.setAlignment(Element.ALIGN_CENTER);
                datePara.setSpacingAfter(20);
                doc.add(datePara);
            } else {
                doc.add(new Paragraph(" "));
            }

            if (headers != null && !headers.isEmpty()) {
                PdfPTable table = new PdfPTable(headers.size());
                table.setWidthPercentage(100);

                for (String header : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(5);
                    table.addCell(cell);
                }

                for (List<String> row : rows) {
                    for (String cellStr : row) {
                        PdfPCell cell = new PdfPCell(new Phrase(cellStr != null ? cellStr : "", rowFont));
                        cell.setPadding(5);
                        table.addCell(cell);
                    }
                }
                doc.add(table);
            }

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
        return out.toByteArray();
    }
}
