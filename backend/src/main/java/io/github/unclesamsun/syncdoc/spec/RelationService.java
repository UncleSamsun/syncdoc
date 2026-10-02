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
@Transactional(readOnly = true)
public class RelationService {
    private final ProjectService projects; private final DocumentService documents; private final TaskMappingService mapping; private final ObjectMapper json;
    public RelationService(ProjectService projects, DocumentService documents, TaskMappingService mapping, ObjectMapper json) { this.projects = projects; this.documents = documents; this.mapping = mapping; this.json = json; }
    public record Task(SpecRelations.Item item, TaskMappingService.TaskView execution) {}
    public record Requirement(SpecRelations.Item requirement, String designCoverage, List<SpecRelations.Item> designs, List<Task> tasks) {}
    public record View(UUID snapshotId, String sourceRevision, Integer analysisVersion, String analysisStatus, String uncheckedReason,
                       List<Requirement> requirements, List<SpecTraceability.Finding> findings, int totalElements, int page, int size) {}
    public View view(CurrentUser user, UUID projectId, UUID requested, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException();
        var project = projects.view(user, projectId); var snapshot = documents.snapshotFor(project, requested).orElseThrow(DocumentService.DocumentsNotReadyException::new);
        var graph = read(snapshot, json); var nodes = DesignImpactCalculator.unique(graph);
        Map<String, TaskMappingService.TaskView> execution = new HashMap<>(); mapping.map(projectId, snapshot.getId()).forEach(t -> execution.put(t.taskSpecId(), t));
        List<Requirement> rows = graph.nodes().stream().filter(n -> n.kind().equals("req")).map(req -> {
            var designs = DesignImpactCalculator.relatedDesigns(graph, req.itemId());
            var tasks = graph.edges().stream().filter(e -> e.targetKind().equals("req") && e.targetId().equals(req.itemId()) && e.sourceKind().equals("task") && e.relation().equals("requires"))
                    .map(e -> nodes.get("task:" + e.sourceId())).filter(Objects::nonNull).map(n -> new Task(n, execution.get(n.itemId()))).toList();
            return new Requirement(req, !designs.isEmpty() ? "linked" : graph.analysisStatus().equals("complete") ? "no_definition" : "unknown", designs, tasks);
        }).toList();
        int start = (int) Math.min(rows.size(), (long) page * size);
        return new View(snapshot.getId(), snapshot.getSourceRevision(), graph.schemaVersion(), graph.analysisStatus(), graph.uncheckedReason(), rows.subList(start, (int) Math.min(rows.size(), (long) start + size)), graph.findings(), rows.size(), page, size);
    }
    static SpecRelations read(DocumentSnapshotEntity snapshot, ObjectMapper json) {
        if (snapshot.getRelationsJson() == null) return SpecRelations.notComputed();
        try { var graph = json.readValue(snapshot.getRelationsJson(), SpecRelations.class);
            if (!Objects.equals(graph.schemaVersion(), 1) || graph.nodes() == null || graph.edges() == null || graph.findings() == null || !Set.of("complete", "partial", "unchecked").contains(graph.analysisStatus())) return SpecRelations.notComputed();
            if (graph.nodes().stream().anyMatch(n -> n == null || n.kind() == null || !Set.of("req", "ui", "api", "task").contains(n.kind()) || n.itemId() == null || n.documentId() == null || n.path() == null || n.line() < 1)) return SpecRelations.notComputed();
            if (graph.edges().stream().anyMatch(e -> e == null || e.sourceKind() == null || e.sourceId() == null || e.targetKind() == null || e.targetId() == null || e.relation() == null || e.sourceLocation() == null)) return SpecRelations.notComputed();
            if (graph.findings().stream().anyMatch(Objects::isNull)) return SpecRelations.notComputed(); return graph;
        } catch (RuntimeException e) { return SpecRelations.notComputed(); }
    }
}
