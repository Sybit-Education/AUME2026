package de.seezeit.aume;

import de.seezeit.aume.standard.InfoPageService;
import de.seezeit.aume.standard.InfoPageService.InfoPagesRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Beispieltexte aus dem Wireframe; die verbindlichen Texte legt Seezeit fest. */
@Component
@Profile("dev")
@Order(2)
public class InfoPageSampleData implements CommandLineRunner {
    private final InfoPageService infoPages;

    public InfoPageSampleData(InfoPageService infoPages) {
        this.infoPages = infoPages;
    }

    @Override
    public void run(String... args) {
        if (infoPages.read().updatedAt() != null) return;
        infoPages.update("clerk", new InfoPagesRequest(
            """
            1. Ihr bekommt eine Ankündigung mit dem Kontrollzeitraum.
            2. Die Objektbetreuung prüft die Gemeinschaftsflächen mit der Checkliste. Ihr könnt dabei anwesend sein.
            3. Ihr erhaltet das Ergebnis mit allen Mängeln in der App.
            4. Bei Mängeln folgt eine Nachkontrolle mit Frist.
            5. Wird ein Mangel nicht behoben, kann Seezeit weitere Maßnahmen einleiten.""",
            "Bei Mängeln gibt es eine Nachkontrolle mit Frist. Bleibt ein Mangel offen, kann Seezeit weitere "
                + "Maßnahmen einleiten, zum Beispiel eine Reinigung durch eine Reinigungsfirma. "
                + "Die Kosten richten sich nach den Regeln von Seezeit.",
            "Die Sauberkeitsstandards gelten für alle Wohnanlagen gleich. "
                + "Die Objektbetreuung nutzt exakt dieselbe Checkliste wie ihr.",
            """
            1. You will receive an announcement with the inspection period.
            2. The caretaker checks the common areas using the checklist. You are welcome to be present.
            3. You will receive the result with all defects in the app.
            4. If there are defects, a follow-up inspection with a deadline will take place.
            5. If a defect is not fixed, Seezeit may take further measures.""",
            "If there are defects, there will be a follow-up inspection with a deadline. If a defect remains, "
                + "Seezeit may take further measures, for example cleaning by a cleaning company. "
                + "The costs are based on Seezeit's rules.",
            "The cleanliness standards are the same for all residences. "
                + "The caretaker uses exactly the same checklist as you."));
    }
}
