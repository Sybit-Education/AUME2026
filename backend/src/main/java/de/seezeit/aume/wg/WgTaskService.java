package de.seezeit.aume.wg;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import de.seezeit.aume.residence.MembershipService;
import de.seezeit.aume.residence.MembershipService.ActiveMembership;
import de.seezeit.aume.standard.CriterionEntity;
import de.seezeit.aume.standard.ResidentChecklistService;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Manages the cleaning plan for the user's active flat. */
@Service
@Transactional(readOnly = true)
public class WgTaskService {
    public static final String INVALID_TITLE = "invalid_title";
    public static final String INVALID_RHYTHM = "invalid_rhythm";
    public static final String INVALID_ASSIGNEE = "invalid_assignee";
    public static final String INVALID_CRITERION = "invalid_criterion";
    public static final String NOT_ASSIGNEE = "not_assignee";
    public static final String NOT_DUE = "not_due";

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int MAX_TITLE_LENGTH = 120;

    private final WgTaskRepository tasks;
    private final WgTaskAssignmentRepository completions;
    private final MembershipService memberships;
    private final ResidentChecklistService checklists;
    private final EntityManager entities;

    public WgTaskService(WgTaskRepository tasks, WgTaskAssignmentRepository completions,
                         MembershipService memberships, ResidentChecklistService checklists,
                         EntityManager entities) {
        this.tasks = tasks;
        this.completions = completions;
        this.memberships = memberships;
        this.checklists = checklists;
        this.entities = entities;
    }

    public WgPlanView plan(String userKey, LocalDate requestedWeek, Locale locale) {
        var active = memberships.requireActive(userKey);
        var current = currentWeek();
        var week = requestedWeek == null ? current : monday(requestedWeek);
        var all = tasks.findActive(active.flat().getId());
        var due = all.stream().filter(task -> isDue(task, week)).toList();
        var done = due.isEmpty() ? Map.<Long, WgTaskAssignmentEntity>of()
            : completions.findDone(due.stream().map(WgTaskEntity::getId).toList(), week).stream()
                .collect(Collectors.toMap(completion -> completion.getTask().getId(), Function.identity()));
        var names = active.members().stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
        var me = active.user().getId();

        var views = due.stream().map(task -> {
            var completion = done.get(task.getId());
            var assigneeId = names.containsKey(task.getAssignee().getId()) ? task.getAssignee().getId() : null;
            var mine = me.equals(assigneeId);
            var doneByMe = completion != null && me.equals(completion.getUser().getId());
            var checkable = week.equals(current) && (completion == null ? mine : doneByMe);
            return new WgPlanView.Task(task.getId(), task.getTitle(), task.getRhythm(), task.getStartWeek(),
                assigneeId, assigneeId == null ? null : names.get(assigneeId), mine, completion != null,
                completion == null ? null : names.get(completion.getUser().getId()), checkable);
        }).toList();
        var members = active.members().stream()
            .map(member -> new WgPlanView.Member(member.getId(), member.getName())).toList();
        return new WgPlanView(week, current, me, members, views, all.stream().map(WgTaskEntity::getTitle).toList(),
            suggestions(all, locale));
    }

    /** Published checklist criteria without a task in this flat. */
    private List<WgPlanView.Suggestion> suggestions(List<WgTaskEntity> all, Locale locale) {
        var linked = all.stream().map(WgTaskEntity::getCriterion).filter(Objects::nonNull)
            .map(CriterionEntity::getId).collect(Collectors.toSet());
        return checklists.current(locale).stream()
            .flatMap(checklist -> checklist.areas().stream())
            .flatMap(area -> area.criteria().stream()
                .filter(criterion -> !linked.contains(criterion.id()))
                .map(criterion -> new WgPlanView.Suggestion(criterion.id(), area.name(), criterion.title())))
            .toList();
    }

    @Transactional
    public void create(String userKey, TaskRequest request) {
        var active = memberships.requireActive(userKey);
        var flatId = active.flat().getId();
        var task = new WgTaskEntity(active.flat(), title(request), (int) tasks.countByFlatId(flatId),
            assignee(active, request), rhythm(request),
            request.startWeek() == null ? currentWeek() : monday(request.startWeek()));
        task.setCriterion(criterion(request.criterionId()));
        tasks.save(task);
    }

    /** Only criteria from the published checklist can be linked. */
    private CriterionEntity criterion(Long id) {
        if (id == null) return null;
        var published = checklists.current(Locale.GERMAN).stream()
            .flatMap(checklist -> checklist.areas().stream())
            .flatMap(area -> area.criteria().stream())
            .anyMatch(criterion -> criterion.id().equals(id));
        if (!published) throw failure(HttpStatus.BAD_REQUEST, INVALID_CRITERION);
        return entities.getReference(CriterionEntity.class, id);
    }

    @Transactional
    public void update(String userKey, Long id, TaskRequest request) {
        var active = memberships.requireActive(userKey);
        var task = requireTask(active, id);
        task.setTitle(title(request));
        task.setAssignee(assignee(active, request));
        task.setRhythm(rhythm(request));
        if (request.startWeek() != null) task.setStartWeek(monday(request.startWeek()));
    }

    @Transactional
    public void delete(String userKey, Long id) {
        requireTask(memberships.requireActive(userKey), id).setArchivedAt(Instant.now());
    }

    /** Only the assignee can complete a task, in the current week. */
    @Transactional
    public void complete(String userKey, Long id) {
        var active = memberships.requireActive(userKey);
        var task = requireTask(active, id);
        var week = currentWeek();
        if (!isDue(task, week)) throw failure(HttpStatus.CONFLICT, NOT_DUE);
        if (!task.getAssignee().getId().equals(active.user().getId())) {
            throw failure(HttpStatus.CONFLICT, NOT_ASSIGNEE);
        }
        if (completions.findByTaskIdAndWeekStart(task.getId(), week).isPresent()) return;
        var completion = new WgTaskAssignmentEntity(task, active.user(), week);
        completion.setCompletedAt(Instant.now());
        completions.save(completion);
    }

    /** Only the person who completed a task can undo it. */
    @Transactional
    public void undo(String userKey, Long id) {
        var active = memberships.requireActive(userKey);
        var task = requireTask(active, id);
        completions.findByTaskIdAndWeekStart(task.getId(), currentWeek()).ifPresent(completion -> {
            if (!completion.getUser().getId().equals(active.user().getId())) {
                throw failure(HttpStatus.CONFLICT, NOT_ASSIGNEE);
            }
            completions.delete(completion);
        });
    }

    private WgTaskEntity requireTask(ActiveMembership active, Long id) {
        return tasks.findByIdAndFlatIdAndArchivedAtIsNull(id, active.flat().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean isDue(WgTaskEntity task, LocalDate week) {
        var weeks = ChronoUnit.WEEKS.between(task.getStartWeek(), week);
        return weeks >= 0 && weeks % task.getRhythm().weeks() == 0;
    }

    private static String title(TaskRequest request) {
        var title = request.title() == null ? "" : request.title().strip();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) throw failure(HttpStatus.BAD_REQUEST, INVALID_TITLE);
        return title;
    }

    private static WgTaskRhythm rhythm(TaskRequest request) {
        if (request.rhythm() == null) throw failure(HttpStatus.BAD_REQUEST, INVALID_RHYTHM);
        return request.rhythm();
    }

    private static UserEntity assignee(ActiveMembership active, TaskRequest request) {
        return active.members().stream().filter(member -> member.getId().equals(request.assigneeId())).findFirst()
            .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, INVALID_ASSIGNEE));
    }

    private static LocalDate currentWeek() {
        return monday(LocalDate.now(ZONE));
    }

    private static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static ResponseStatusException failure(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    /** {@code criterionId} is only used when creating a task. */
    public record TaskRequest(String title, Long assigneeId, WgTaskRhythm rhythm, LocalDate startWeek,
                              Long criterionId) {}
}
