package de.seezeit.aume.wg;

import java.time.LocalDate;
import java.util.List;

/**
 * Cleaning plan for one week.
 * {@code assigneeId} and names are {@code null} if the person moved out.
 * {@code allTitles} includes tasks not due this week.
 * {@code suggestions} are unused checklist criteria.
 */
public record WgPlanView(LocalDate week, LocalDate currentWeek, Long me, List<Member> members, List<Task> tasks,
                         List<String> allTitles, List<Suggestion> suggestions) {

    public record Member(Long id, String name) {}

    public record Suggestion(Long criterionId, String area, String title) {}

    public record Task(Long id, String title, WgTaskRhythm rhythm, LocalDate startWeek, Long assigneeId,
                       String assigneeName, boolean mine, boolean done, String doneBy, boolean checkable) {}
}
