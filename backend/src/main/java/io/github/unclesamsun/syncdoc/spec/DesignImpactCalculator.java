package io.github.unclesamsun.syncdoc.spec;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class DesignImpactCalculator {
    public record Impact(String requirementId, String requirementChange, String kind, String itemId,
            ComparisonEngine.Ref before, ComparisonEngine.Ref after, String presence) {}
    public List<Impact> compute(List<ComparisonEngine.Row> changes, SpecRelations old, SpecRelations now) {
        Map<String, SpecRelations.Item> before = unique(old), after = unique(now);
        Set<String> afterDefined = new HashSet<>(); now.nodes().forEach(n -> afterDefined.add(n.key()));
        List<Impact> result = new ArrayList<>();
        for (var change : changes) {
            if (!change.kind().equals("req") || !Set.of("added", "removed", "modified", "moved_modified").contains(change.change())) continue;
            String req = change.key().substring(4); Set<String> designs = new TreeSet<>();
            relatedDesigns(old, req).forEach(n -> designs.add(n.key())); relatedDesigns(now, req).forEach(n -> designs.add(n.key()));
            for (String key : designs) {
                var left = before.get(key); var right = after.get(key); var item = right != null ? right : left;
                String presence = right != null ? "present" : afterDefined.contains(key) || !now.analysisStatus().equals("complete") ? "unknown" : "removed";
                result.add(new Impact(req, change.change(), item.kind(), item.itemId(), ref(left), ref(right), presence));
            }
        }
        return List.copyOf(result);
    }
    static List<SpecRelations.Item> relatedDesigns(SpecRelations graph, String requirementId) {
        var index = unique(graph); Set<String> reached = new HashSet<>(Set.of("req:" + requirementId)); boolean changed;
        do { changed = false; for (var e : graph.edges()) {
            String source = e.sourceKind() + ":" + e.sourceId(), target = e.targetKind() + ":" + e.targetId();
            if (Set.of("ui", "api").contains(e.sourceKind()) && Set.of("requires", "uses").contains(e.relation()) && index.containsKey(source) && reached.contains(target)) changed |= reached.add(source);
        }} while (changed);
        return reached.stream().map(index::get).filter(Objects::nonNull).filter(n -> Set.of("ui", "api").contains(n.kind())).sorted(Comparator.comparing(SpecRelations.Item::key)).toList();
    }
    static Map<String, SpecRelations.Item> unique(SpecRelations report) {
        Map<String, List<SpecRelations.Item>> grouped = new TreeMap<>(); report.nodes().forEach(n -> grouped.computeIfAbsent(n.key(), k -> new ArrayList<>()).add(n));
        Map<String, SpecRelations.Item> result = new TreeMap<>(); grouped.forEach((key, nodes) -> { if (nodes.size() == 1) result.put(key, nodes.getFirst()); }); return result;
    }
    private static ComparisonEngine.Ref ref(SpecRelations.Item n) { return n == null ? null : new ComparisonEngine.Ref(n.documentId(), null, n.path(), n.title(), n.kind(), n.itemId(), n.anchor(), n.line(), "확정"); }
}
