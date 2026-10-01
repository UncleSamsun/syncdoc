package io.github.unclesamsun.syncdoc.spec;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.dashboard.TaskMappingService;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpecTraceabilityService {
    private final ProjectService projects;
    private final DocumentService documents;
    private final TaskMappingService mapping;
    private final ObjectMapper json;
    public SpecTraceabilityService(ProjectService projects, DocumentService documents, TaskMappingService mapping, ObjectMapper json) {
        this.projects = projects; this.documents = documents; this.mapping = mapping; this.json = json;
    }
    public record Task(SpecTraceability.Item item, TaskMappingService.TaskView execution) {}
    public record Requirement(SpecTraceability.Item item, String coverage, List<Task> tasks) {}
    public record View(UUID snapshotId, String sourceRevision, Integer analysisVersion, String analysisStatus,
                       String uncheckedReason, List<Requirement> requirements, int totalElements, int page, int size) {}
    public record FindingsView(UUID snapshotId, String sourceRevision, Integer analysisVersion, String analysisStatus,
                               String uncheckedReason, List<SpecTraceability.Finding> findings, int totalElements, int page, int size) {}
    @Transactional(readOnly = true)
    public View view(CurrentUser user, UUID projectId, UUID requested, int page, int size, String filter) {
        validate(page, size);
        if (!Set.of("all", "linked", "unlinked", "unknown").contains(filter)) throw new IllegalArgumentException();
        DocumentSnapshotEntity snapshot = snapshot(user, projectId, requested);
        SpecTraceability report = read(snapshot);
        Map<String, TaskMappingService.TaskView> execution = new HashMap<>();
        mapping.map(projectId, snapshot.getId()).forEach(task -> execution.put(task.taskSpecId(), task));
        Map<String, SpecTraceability.Item> uniqueTasks = new HashMap<>();
        report.tasks().stream().collect(java.util.stream.Collectors.groupingBy(SpecTraceability.Item::itemId))
            .forEach((id, items) -> { if (items.size() == 1) uniqueTasks.put(id, items.getFirst()); });
        List<Requirement> rows = report.requirements().stream().map(requirement -> {
            List<Task> tasks = report.edges().stream().filter(edge -> edge.requirementId().equals(requirement.itemId()))
                .map(edge -> uniqueTasks.get(edge.taskId())).filter(Objects::nonNull)
                .map(item -> new Task(item, execution.get(item.itemId()))).toList();
            String coverage = !tasks.isEmpty() ? "linked" : "complete".equals(report.analysisStatus()) ? "unlinked" : "unknown";
            return new Requirement(requirement, coverage, tasks);
        }).filter(row -> filter.equals("all") || row.coverage().equals(filter)).toList();
        return new View(snapshot.getId(), snapshot.getSourceRevision(), report.schemaVersion(), report.analysisStatus(),
                report.uncheckedReason(), page(rows, page, size), rows.size(), page, size);
    }
    @Transactional(readOnly = true)
    public FindingsView findings(CurrentUser user, UUID projectId, UUID requested, int page, int size) {
        validate(page, size);
        DocumentSnapshotEntity snapshot = snapshot(user, projectId, requested);
        SpecTraceability report = read(snapshot);
        return new FindingsView(snapshot.getId(), snapshot.getSourceRevision(), report.schemaVersion(), report.analysisStatus(),
            report.uncheckedReason(), page(report.findings(), page, size), report.findings().size(), page, size);
    }
    private DocumentSnapshotEntity snapshot(CurrentUser user, UUID projectId, UUID requested) {
        var project = projects.view(user, projectId);
        return documents.snapshotFor(project, requested).orElseThrow(DocumentService.DocumentsNotReadyException::new);
    }
    private SpecTraceability read(DocumentSnapshotEntity snapshot) {
        return snapshot.getTraceabilityJson() == null ? SpecTraceability.notComputed()
            : json.readValue(snapshot.getTraceabilityJson(), SpecTraceability.class);
    }
    private static void validate(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException(); }
    private static <T> List<T> page(List<T> items, int page, int size) {
        int first = (int) Math.min(items.size(), (long) page * size);
        return List.copyOf(items.subList(first, (int) Math.min(items.size(), (long) first + size)));
    }
}
