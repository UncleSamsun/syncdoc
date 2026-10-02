package io.github.unclesamsun.syncdoc.spec;

import java.util.List;

/** Companion report: never rewrites historical traceability or comparison reports. */
public record SpecRelations(Integer schemaVersion, String analysisStatus, String uncheckedReason,
        List<Item> nodes, List<Edge> edges, List<SpecTraceability.Finding> findings) {
    public record Item(String kind, String itemId, String title, String documentId, String path, String anchor, int line) {
        public String key() { return kind + ":" + itemId; }
    }
    public record Edge(String sourceKind, String sourceId, String targetKind, String targetId,
            String relation, SpecTraceability.Location sourceLocation) {}
    public static SpecRelations notComputed() {
        return new SpecRelations(null, "unchecked", "NOT_COMPUTED", List.of(), List.of(), List.of());
    }
}
