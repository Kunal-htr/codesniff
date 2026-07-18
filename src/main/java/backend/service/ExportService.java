package backend.service;

import backend.dto.ReportResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;

@Service
public class ExportService {

    public String generateCsv(ReportResponse r) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("File A,File B,Jaccard,Coverage,LCS,AST,Hybrid,Verdict,Operator Divergent,Divergent Operators\n");
        // Row
        sb.append(escapeCsv(r.nameA())).append(",");
        sb.append(escapeCsv(r.nameB())).append(",");
        sb.append(String.format("%.2f", r.jaccard())).append(",");
        sb.append(String.format("%.2f", r.coverage())).append(",");
        sb.append(String.format("%.2f", r.lcs())).append(",");
        sb.append(String.format("%.2f", r.ast())).append(",");
        sb.append(String.format("%.2f", r.hybrid())).append(",");
        sb.append(escapeCsv(r.verdict())).append(",");
        sb.append(r.operatorDivergent()).append(",");
        
        String ops = r.divergentOperators() != null ? String.join("; ", r.divergentOperators()) : "";
        sb.append(escapeCsv(ops)).append("\n");
        
        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public String generateHtml(ReportResponse r) {
        String ops = r.divergentOperators() != null ? String.join(", ", r.divergentOperators()) : "None";
        String opsWarning = "";
        if (r.operatorDivergent()) {
            opsWarning = "<div class='warning'><strong>Warning:</strong> High similarity is largely driven by operator differences (" + ops + ").</div>";
        }
        
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>CodeSniff Report</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 40px; color: #333; max-width: 800px; margin: 0 auto; }\n" +
                "        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }\n" +
                "        .files { background: #f8f9fa; padding: 20px; border-radius: 8px; margin-bottom: 30px; }\n" +
                "        .files strong { color: #555; }\n" +
                "        table { width: 100%; border-collapse: collapse; margin-bottom: 30px; }\n" +
                "        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }\n" +
                "        th { background-color: #f8f9fa; font-weight: 600; }\n" +
                "        .verdict { padding: 20px; border-radius: 8px; font-weight: bold; font-size: 1.2em; text-align: center; }\n" +
                "        .verdict.Clean { background: #d4edda; color: #155724; }\n" +
                "        .verdict.Review { background: #fff3cd; color: #856404; }\n" +
                "        .verdict.Suspicious { background: #f8d7da; color: #721c24; }\n" +
                "        .verdict.High { background: #f5c6cb; color: #721c24; }\n" +
                "        .warning { background: #fff3cd; color: #856404; padding: 15px; border-radius: 5px; margin-top: 20px; border-left: 4px solid #ffeeba; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>CodeSniff Pairwise Report</h1>\n" +
                "    <div class='files'>\n" +
                "        <p><strong>File A:</strong> " + escapeHtml(r.nameA()) + "</p>\n" +
                "        <p><strong>File B:</strong> " + escapeHtml(r.nameB()) + "</p>\n" +
                "    </div>\n" +
                "    \n" +
                "    <table>\n" +
                "        <tr><th>Metric</th><th>Score</th></tr>\n" +
                "        <tr><td>Hybrid Score</td><td><strong>" + String.format("%.1f", r.hybrid() * 100) + "%</strong></td></tr>\n" +
                "        <tr><td>Jaccard (Fingerprint)</td><td>" + String.format("%.1f", r.jaccard() * 100) + "%</td></tr>\n" +
                "        <tr><td>Coverage (Fingerprint)</td><td>" + String.format("%.1f", r.coverage() * 100) + "%</td></tr>\n" +
                "        <tr><td>LCS (Statement)</td><td>" + String.format("%.1f", r.lcs() * 100) + "%</td></tr>\n" +
                "        <tr><td>AST (Structural)</td><td>" + String.format("%.1f", r.ast() * 100) + "%</td></tr>\n" +
                "    </table>\n" +
                "    \n" +
                "    <div class='verdict " + escapeHtml(r.verdict()) + "'>\n" +
                "        Verdict: " + escapeHtml(r.verdict()) + "<br>\n" +
                "        <small style='font-weight: normal; font-size: 0.8em;'>" + escapeHtml(r.verdictDescription()) + "</small>\n" +
                "    </div>\n" +
                "    \n" +
                opsWarning +
                "</body>\n" +
                "</html>";
    }

    private String escapeHtml(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public byte[] generatePdf(ReportResponse r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font warningFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 12);

            Paragraph title = new Paragraph("CodeSniff Pairwise Report", titleFont);
            title.setSpacingAfter(20);
            document.add(title);

            document.add(new Paragraph("File A: " + r.nameA(), bodyFont));
            Paragraph fileB = new Paragraph("File B: " + r.nameB(), bodyFont);
            fileB.setSpacingAfter(20);
            document.add(fileB);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(20f);

            table.addCell(new PdfPCell(new Phrase("Metric", headerFont)));
            table.addCell(new PdfPCell(new Phrase("Score", headerFont)));

            addTableRow(table, "Hybrid Score", String.format("%.1f%%", r.hybrid() * 100), bodyFont);
            addTableRow(table, "Jaccard", String.format("%.1f%%", r.jaccard() * 100), bodyFont);
            addTableRow(table, "Coverage", String.format("%.1f%%", r.coverage() * 100), bodyFont);
            addTableRow(table, "LCS", String.format("%.1f%%", r.lcs() * 100), bodyFont);
            addTableRow(table, "AST", String.format("%.1f%%", r.ast() * 100), bodyFont);
            document.add(table);

            Paragraph verdict = new Paragraph("Verdict: " + r.verdict(), headerFont);
            document.add(verdict);
            
            Paragraph verdictDesc = new Paragraph(r.verdictDescription(), bodyFont);
            verdictDesc.setSpacingAfter(15);
            document.add(verdictDesc);

            if (r.operatorDivergent()) {
                String ops = r.divergentOperators() != null ? String.join(", ", r.divergentOperators()) : "None";
                Paragraph warning = new Paragraph("Warning: High similarity is largely driven by operator differences (" + ops + ").", warningFont);
                document.add(warning);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private void addTableRow(PdfPTable table, String metric, String score, Font font) {
        table.addCell(new Phrase(metric, font));
        table.addCell(new Phrase(score, font));
    }
}
