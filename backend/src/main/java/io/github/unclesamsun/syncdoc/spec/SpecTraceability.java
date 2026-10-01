package io.github.unclesamsun.syncdoc.spec;

import java.util.List;

/** Revision-bound derived report; execution state is deliberately not persisted here. */
public record SpecTraceability(Integer schemaVersion, String analysisStatus, String uncheckedReason,
        List<Item> requirements, List<Item> tasks, List<Edge> edges, List<Finding> findings) {
    public record Item(String itemId, String title, String documentId, String path, String anchor, int line) {}
    public record Location(String documentId, String path, int line) {}
    public record Edge(String taskId, String requirementId, Location sourceLocation) {}
    public record Finding(String code, String severity, String documentId, String path, int line,
                          String itemId, String targetId, String message) {}
    public static SpecTraceability notComputed() {
        return new SpecTraceability(null, "unchecked", "NOT_COMPUTED", List.of(), List.of(), List.of(), List.of());
    }
}
