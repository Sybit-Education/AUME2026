package de.seezeit.aume.standard;

import de.seezeit.aume.standard.ResidentChecklistService.AreaView;
import de.seezeit.aume.standard.ResidentChecklistService.ChecklistView;
import de.seezeit.aume.standard.ResidentChecklistService.CriterionView;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Optional;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

/** Erzeugt die druckbare Lesesicht einer veröffentlichten Checklistenfassung. */
@Service
public class ChecklistPdfService {
    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font CRITERION = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font TEXT = new Font(Font.HELVETICA, 11);

    private final ResidentChecklistService checklists;

    ChecklistPdfService(ResidentChecklistService checklists) {
        this.checklists = checklists;
    }

    public boolean isAvailable(ChecklistVersionEntity version) {
        return ResidentChecklistService.isPublishedVersion(version);
    }

    public Optional<ChecklistPdf> render(ChecklistVersionEntity version, Locale locale) {
        var labels = Labels.of(locale);
        return checklists.forPublishedVersion(version, labels.language())
            .map(checklist -> new ChecklistPdf(labels.fileName(), labels.language(), document(checklist, labels)));
    }

    private static byte[] document(ChecklistView checklist, Labels labels) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 48);
        var writer = PdfWriter.getInstance(document, out);
        document.addTitle(labels.title());
        document.open();
        try {
            var checkbox = checkboxImage(writer);
            document.add(new Paragraph(labels.title(), TITLE));
            document.add(new Paragraph(labels.version() + " " + checklist.version(), TEXT));
            for (var area : checklist.areas()) {
                document.add(areaTable(area, checkbox));
            }
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static PdfPTable areaTable(AreaView area, Image checkbox) {
        var table = new PdfPTable(new float[] {1, 24});
        table.setWidthPercentage(100);
        table.setSpacingBefore(12);
        table.setHeaderRows(1);
        table.setSplitLate(true);

        var heading = new PdfPCell();
        heading.setColspan(2);
        heading.setBorder(PdfPCell.NO_BORDER);
        heading.setPaddingBottom(6);
        heading.addElement(new Paragraph(area.name(), HEADING));
        table.addCell(heading);
        for (var criterion : area.criteria()) {
            var box = new PdfPCell(checkbox);
            box.setBorder(PdfPCell.NO_BORDER);
            box.setPaddingTop(2);
            table.addCell(box);
            table.addCell(criterionText(criterion));
        }
        return table;
    }

    private static Image checkboxImage(PdfWriter writer) {
        // Draw a square so the printable checkbox does not depend on a font glyph.
        var box = writer.getDirectContent().createTemplate(10, 10);
        box.setLineWidth(0.75f);
        box.rectangle(0.5f, 0.5f, 9, 9);
        box.stroke();
        return Image.getInstance(box);
    }

    private static PdfPCell criterionText(CriterionView criterion) {
        var cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingTop(0);
        cell.setPaddingBottom(7);
        cell.addElement(new Paragraph(criterion.title(), CRITERION));
        cell.addElement(new Paragraph(criterion.description(), TEXT));
        return cell;
    }

    public record ChecklistPdf(String fileName, Locale language, byte[] content) {}

    private record Labels(Locale language, String title, String version, String fileName) {
        static Labels of(Locale locale) {
            if (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
                return new Labels(Locale.ENGLISH, "Cleanliness checklist", "Version", "Cleanliness_Checklist.pdf");
            }
            return new Labels(Locale.GERMAN, "Sauberkeitscheckliste", "Fassung", "Checkliste_Sauberkeit.pdf");
        }
    }
}
