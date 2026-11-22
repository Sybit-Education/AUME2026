package de.seezeit.aume.inspection;

import java.security.Principal;
import java.time.LocalDate;
import de.seezeit.aume.inspection.InspectionEntity.Result;
import de.seezeit.aume.inspection.InspectionHistoryService.HistoryEntry;
import de.seezeit.aume.inspection.InspectionHistoryService.HistoryFilter;
import de.seezeit.aume.inspection.InspectionHistoryService.HistoryOrder;
import de.seezeit.aume.inspection.InspectionHistoryService.OrderBy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Kontroll-Historie der Objektbetreuung (#40, #42). Details über {@code GET /api/inspections/{id}} (#43). */
@RestController
@RequestMapping("/api/inspections/history")
@PreAuthorize("hasAuthority('INSPECTION_PERFORM')")
public class InspectionHistoryController {
    private final InspectionHistoryService history;

    public InspectionHistoryController(InspectionHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public PagedModel<HistoryEntry> list(Principal principal, Pageable pageable,
            @RequestParam(required = false) String flat,
            @RequestParam(required = false) Result result,
            @RequestParam(required = false) Boolean followUp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OrderBy orderBy,
            @RequestParam(required = false) Sort.Direction direction) {
        var filter = new HistoryFilter(flat, result, followUp, from, to);
        var order = new HistoryOrder(orderBy, direction);
        return new PagedModel<>(history.history(principal.getName(), filter, order, pageable));
    }
}
