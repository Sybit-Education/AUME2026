package de.seezeit.aume.inspection;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import de.seezeit.aume.inspection.FindingEntity.Rating;
import de.seezeit.aume.inspection.InspectionService.CriterionView;
import de.seezeit.aume.inspection.InspectionService.FindingView;
import de.seezeit.aume.inspection.InspectionService.InspectionView;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protokoll einer abgeschlossenen Kontrolle als PDF (#11). Zugriff wie bei der Detailansicht: nur
 * Kontrollen der eigenen Wohnanlage, fremde verhalten sich wie nicht vorhanden (404).
 */
@Service
public class ProtocolPdfService {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Color MUTED = new Color(0x55, 0x5f, 0x6d);
    private static final Color HEADER = new Color(0xe9, 0xee, 0xf4);
    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TEXT = new Font(Font.HELVETICA, 9.5f);
    private static final Font BOLD = new Font(Font.HELVETICA, 9.5f, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);

    private final InspectionService inspections;

    public ProtocolPdfService(InspectionService inspections) {
        this.inspections = inspections;
    }

    public Protocol render(String userKey, Long id, Locale locale) {
        var inspection = inspections.view(userKey, id);
        if (!InspectionHistoryService.DONE.contains(inspection.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inspection is not finished yet");
        }
        var labels = Labels.of(locale);
        return new Protocol(fileName(inspection), document(inspection, labels));
    }

    private byte[] document(InspectionView inspection, Labels labels) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, out);
        document.addTitle(labels.title + " " + inspection.flatLabel());
        document.open();

        document.add(new Paragraph(labels.title, TITLE));
        document.add(spaced(new Paragraph(inspection.residenceName() + " · " + labels.flat + " "
            + inspection.flatLabel(), TEXT), 12));
        document.add(meta(inspection, labels));

        var findings = inspection.findings().stream()
            .collect(Collectors.toMap(FindingView::criterionId, Function.identity()));
        for (var area : byArea(inspection.criteria()).entrySet()) {
            document.add(spaced(new Paragraph(area.getKey(), HEADING), 6, 14));
            document.add(criteria(area.getValue(), findings, labels));
        }

        document.add(spaced(new Paragraph(labels.created + " " + format(Instant.now(), labels), SMALL), 0, 18));
        document.close();
        return out.toByteArray();
    }

    private PdfPTable meta(InspectionView inspection, Labels labels) {
        var table = new PdfPTable(new float[]{1, 2.6f});
        table.setWidthPercentage(100);
        row(table, labels.kind, inspection.round() == 0 ? labels.firstInspection
            : labels.followUp + " " + inspection.round());
        row(table, labels.inspectedOn, inspection.inspectedAt() == null ? "–"
            : labels.date.format(inspection.inspectedAt().atZone(BERLIN)));
        row(table, labels.inspectedBy, inspection.inspectorName() == null ? "–" : inspection.inspectorName());
        long defects = inspection.findings().stream().filter(f -> f.rating() == Rating.DEFICIENT).count();
        row(table, labels.defects, String.valueOf(defects));
        row(table, labels.resultLabel, inspection.overallResult() == null ? "–" : labels.result(inspection.overallResult()));
        return table;
    }

    private static void row(PdfPTable table, String label, String value) {
        table.addCell(cell(label, BOLD, null));
        table.addCell(cell(value, TEXT, null));
    }

    private PdfPTable criteria(List<CriterionView> criteria, Map<Long, FindingView> findings, Labels labels) {
        var table = new PdfPTable(new float[]{2.2f, 1.2f, 0.9f, 3.2f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (var header : List.of(labels.criterion, labels.rating, labels.severity, labels.note)) {
            table.addCell(cell(header, BOLD, HEADER));
        }
        for (var criterion : criteria) {
            var finding = findings.get(criterion.id());
            table.addCell(cell(criterion.title(), TEXT, null));
            table.addCell(cell(finding == null ? "–" : labels.rating(finding.rating()), TEXT, null));
            table.addCell(cell(finding == null || finding.severity() == null ? "" : labels.severity(finding.severity()),
                TEXT, null));
            var note = finding == null || finding.note() == null ? "" : finding.note();
            if (finding != null && finding.buildingCauseSuspected()) {
                note = note.isEmpty() ? labels.buildingCause : note + "\n" + labels.buildingCause;
            }
            table.addCell(cell(note, TEXT, null));
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font, Color background) {
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorderColor(new Color(0xd0, 0xd7, 0xe0));
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        if (background != null) cell.setBackgroundColor(background);
        return cell;
    }

    private static Paragraph spaced(Paragraph paragraph, float after) {
        return spaced(paragraph, after, 0);
    }

    private static Paragraph spaced(Paragraph paragraph, float after, float before) {
        paragraph.setSpacingAfter(after);
        paragraph.setSpacingBefore(before);
        return paragraph;
    }

    /** Bereiche in der Reihenfolge der Checkliste. */
    private static Map<String, List<CriterionView>> byArea(List<CriterionView> criteria) {
        return criteria.stream()
            .sorted((a, b) -> Integer.compare(a.position(), b.position()))
            .collect(Collectors.groupingBy(CriterionView::area, LinkedHashMap::new, Collectors.toList()));
    }

    private static String format(Instant instant, Labels labels) {
        return labels.dateTime.format(instant.atZone(BERLIN));
    }

    /** z. B. Kontrollprotokoll_S-01_2026-10-25.pdf; nur sichere Zeichen im Dateinamen. */
    static String fileName(InspectionView inspection) {
        var flat = inspection.flatLabel().replaceAll("[^A-Za-z0-9._-]", "_");
        var date = inspection.inspectedAt() == null ? ""
            : "_" + DateTimeFormatter.ISO_LOCAL_DATE.format(inspection.inspectedAt().atZone(BERLIN));
        return "Kontrollprotokoll_" + flat + date + ".pdf";
    }

    public record Protocol(String fileName, byte[] content) {}

    /** Beschriftungen wie im Frontend (de/en). */
    private record Labels(String title, String flat, String kind, String firstInspection, String followUp,
            String inspectedOn, String inspectedBy, String defects, String resultLabel,
            String criterion, String rating, String severity, String note, String buildingCause, String created,
            Map<String, String> values, DateTimeFormatter date, DateTimeFormatter dateTime) {

        static Labels of(Locale locale) {
            if ("en".equals(locale == null ? null : locale.getLanguage())) {
                return new Labels("Inspection report", "Flat", "Type", "First inspection", "Follow-up",
                    "Inspected on", "Inspected by", "Defects", "Overall result",
                    "Criterion", "Rating", "Severity", "Note", "Building cause suspected", "Created on",
                    Map.of("OK", "In order", "DEFICIENT", "Defect", "RESOLVED_ON_SITE", "Resolved on site",
                        "LIGHT", "Normal", "SEVERE", "Severe", "PASSED", "Passed", "FAILED", "Follow-up required"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm", Locale.ENGLISH));
            }
            return new Labels("Kontrollprotokoll", "WG", "Art", "Erstkontrolle", "Nachkontrolle",
                "Geprüft am", "Geprüft von", "Mängel", "Gesamtergebnis",
                "Kriterium", "Bewertung", "Schwere", "Notiz", "Verdacht auf Gebäudeursache", "Erstellt am",
                Map.of("OK", "In Ordnung", "DEFICIENT", "Mangel", "RESOLVED_ON_SITE", "Vor Ort geklärt",
                    "LIGHT", "Normal", "SEVERE", "Schwer", "PASSED", "Bestanden", "FAILED", "Nachkontrolle erforderlich"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN),
                DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN));
        }

        String result(Enum<?> value) { return values.get(value.name()); }
        String rating(Enum<?> value) { return values.get(value.name()); }
        String severity(Enum<?> value) { return values.get(value.name()); }
    }
}
