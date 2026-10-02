package io.github.unclesamsun.syncdoc.spec;

import io.github.unclesamsun.syncdoc.document.SpecMetadataParser;
import java.util.*;
import java.util.regex.Pattern;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/** Reads explicit labels and authoritative table cells; never interprets arbitrary prose as execution. */
@Component
public class RelationAnalyzer {
    private static final Pattern UI = Pattern.compile("^UI-(\\d{3})(?:\\s+(.+))?$");
    private static final Pattern API = Pattern.compile("^API-(?!000)(\\d{3})(?:\\s+(.+))?$");
    private static final Pattern REFS = Pattern.compile("(?<![A-Za-z0-9_-])(?:[A-Za-z]+-[A-Za-z0-9]+\\s*[~～]\\s*[^\\s,;·)\\]<>.]*|(?:REQ|UI|API|TASK)-[A-Za-z0-9]+)(?![A-Za-z0-9_-])");
    private final Parser parser = Parser.builder().extensions(List.of(YamlFrontMatterExtension.create(), TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private final SpecMetadataParser metadata = new SpecMetadataParser();
    private record Pending(String kind, String id, String path, String documentId, int line,
                           String targetKind, String relation, TraceabilityAnalyzer.Evidence payload) {}

    public SpecRelations analyze(List<TraceabilityAnalyzer.Source> sources, SpecTraceability trace) {
        List<SpecRelations.Item> nodes = new ArrayList<>();
        trace.requirements().forEach(i -> nodes.add(item("req", i)));
        trace.tasks().forEach(i -> nodes.add(item("task", i)));
        List<SpecTraceability.Finding> findings = new ArrayList<>(trace.findings());
        List<Pending> pending = new ArrayList<>();
        for (var source : sources.stream().sorted(Comparator.comparing(TraceabilityAnalyzer.Source::path)).toList()) {
            var root = parser.parse(source.markdown()); var meta = metadata.parse(root);
            if (!Set.of("ui-screens", "tech-interface", "tasks").contains(Objects.toString(meta.kind(), ""))) continue;
            if (!Set.of("초안", "검토", "확정", "폐기").contains(Objects.toString(meta.status(), ""))) {
                findings.add(finding("INVALID_METADATA", source.documentId(), source.path(), 1, null, null)); continue;
            }
            if (!"확정".equals(meta.status())) continue;
            Map<Node, String> anchors = anchors(root, source);
            Map<String, String> apiAnchors = new HashMap<>();
            for (Node n = root.getFirstChild(); n != null; n = n.getNext()) if (n instanceof Heading h && h.getLevel() == 2) {
                var match = API.matcher(text(h)); if (match.matches()) apiAnchors.putIfAbsent("API-" + match.group(1), anchors.get(h));
            }
            String section = "", sectionAnchor = null;
            int validDefinitions = 0;
            for (Node n = root.getFirstChild(); n != null; n = n.getNext()) {
                if (n instanceof Heading h && h.getLevel() <= 2) {
                    section = text(h); sectionAnchor = anchors.get(h);
                    var match = UI.matcher(section);
                    if (meta.kind().equals("ui-screens") && h.getLevel() == 2 && match.matches()) {
                        String id = "UI-" + match.group(1); validDefinitions++;
                        nodes.add(new SpecRelations.Item("ui", id, Objects.toString(match.group(2), ""), source.documentId(), source.path(), sectionAnchor, TraceabilityAnalyzer.line(n)));
                        labels(source, n, "ui", id, Map.of("연결 요구:", "req", "관련 작업:", "task"), pending);
                    } else if (meta.kind().equals("ui-screens") && h.getLevel() == 2 && section.startsWith("UI-")) {
                        findings.add(finding("INVALID_DEFINITION", source.documentId(), source.path(), TraceabilityAnalyzer.line(n), section, null));
                    }
                    if (meta.kind().equals("tasks") && h.getLevel() == 2 && section.matches("TASK-(?!000)\\d{3}(?:\\s+.*)?")) {
                        String id = section.substring(0, 8);
                        labels(source, n, "task", id, Map.of("근거:", "ui", "선행:", "task"), pending);
                        labels(source, n, "task", id, Map.of("근거:", "api"), pending);
                    }
                }
                if (!(n instanceof TableBlock table)) continue;
                var rows = rows(table); if (rows.isEmpty()) continue;
                List<String> header = cells(rows.getFirst()).stream().map(RelationAnalyzer::text).toList();
                if (meta.kind().equals("tech-interface") && section.equals("계약 일람")) {
                    int idColumn = header.indexOf("ID"), reqColumn = header.indexOf("연결 요구");
                    if (idColumn < 0 || reqColumn < 0) { findings.add(finding("INVALID_CONTRACT_TABLE", source.documentId(), source.path(), TraceabilityAnalyzer.line(n), null, null)); continue; }
                    for (TableRow row : rows.subList(1, rows.size())) {
                        var values = cells(row); int line = TraceabilityAnalyzer.line(row);
                        if (values.size() <= Math.max(idColumn, reqColumn)) { findings.add(finding("INVALID_CONTRACT_TABLE", source.documentId(), source.path(), line, null, null)); continue; }
                        String id = text(values.get(idColumn));
                        if (!id.matches("API-(?!000)\\d{3}")) { findings.add(finding("INVALID_DEFINITION", source.documentId(), source.path(), line, id, null)); continue; }
                        validDefinitions++;
                        nodes.add(new SpecRelations.Item("api", id, id + " 계약", source.documentId(), source.path(), apiAnchors.getOrDefault(id, sectionAnchor), line));
                        pending.add(new Pending("api", id, source.path(), source.documentId(), line, "req", "requires", TraceabilityAnalyzer.visible(values.get(reqColumn))));
                    }
                }
                if (meta.kind().equals("ui-screens") && section.equals("화면과 계약")) {
                    int uiColumn = header.indexOf("화면"), apiColumn = header.indexOf("부르는 계약");
                    if (uiColumn < 0 || apiColumn < 0) { findings.add(finding("INVALID_UI_API_TABLE", source.documentId(), source.path(), TraceabilityAnalyzer.line(n), null, null)); continue; }
                    for (TableRow row : rows.subList(1, rows.size())) {
                        var values = cells(row); if (values.size() <= Math.max(uiColumn, apiColumn)) continue;
                        var match = UI.matcher(text(values.get(uiColumn)));
                        if (match.matches()) pending.add(new Pending("ui", "UI-" + match.group(1), source.path(), source.documentId(), TraceabilityAnalyzer.line(row), "api", "uses", TraceabilityAnalyzer.visible(values.get(apiColumn))));
                        else findings.add(finding("INVALID_DEFINITION", source.documentId(), source.path(), TraceabilityAnalyzer.line(row), text(values.get(uiColumn)), null));
                    }
                }
            }
            if (!meta.kind().equals("tasks") && validDefinitions == 0) findings.add(finding("INVALID_DEFINITION", source.documentId(), source.path(), 1, null, null));
        }
        Map<String, List<SpecRelations.Item>> index = new TreeMap<>();
        nodes.forEach(n -> index.computeIfAbsent(n.key(), k -> new ArrayList<>()).add(n));
        index.values().stream().filter(list -> list.size() > 1).forEach(list -> list.forEach(n -> findings.add(finding("DUPLICATE_ITEM_ID", n.documentId(), n.path(), n.line(), n.itemId(), n.itemId()))));
        Map<String, SpecRelations.Edge> edges = new TreeMap<>();
        trace.edges().forEach(e -> addEdge(edges, new SpecRelations.Edge("task", e.taskId(), "req", e.requirementId(), "requires", e.sourceLocation())));
        for (Pending p : pending) {
            var sourceDefinitions = index.getOrDefault(p.kind() + ":" + p.id(), List.of());
            if (sourceDefinitions.isEmpty()) { findings.add(finding("MISSING_SOURCE_DEFINITION", p.documentId(), p.path(), p.line(), p.id(), null)); continue; }
            if (sourceDefinitions.size() != 1) continue;
            var matches = REFS.matcher(p.payload().text());
            while (matches.find()) {
                String token = matches.group(); if (!token.contains(p.targetKind().toUpperCase(Locale.ROOT) + "-")) continue;
                int line = p.line() + (int) p.payload().text().substring(0, matches.start()).chars().filter(c -> c == '\n').count();
                var ids = expand(token, p.targetKind());
                if (ids.isEmpty()) { findings.add(finding("UNSUPPORTED_REFERENCE", p.documentId(), p.path(), line, p.id(), token)); continue; }
                String destination = p.payload().links().stream().filter(l -> matches.start() >= l.start() && matches.start() < l.end()).map(TraceabilityAnalyzer.LinkSpan::target).findFirst().orElse(null);
                for (String id : ids) {
                    var targets = index.getOrDefault(p.targetKind() + ":" + id, List.of());
                    if (targets.isEmpty()) { findings.add(finding("MISSING_REFERENCE", p.documentId(), p.path(), line, p.id(), id)); continue; }
                    if (targets.size() != 1) continue;
                    if (destination != null && !TraceabilityAnalyzer.targetPath(p.path(), destination).map(targets.getFirst().path()::equals).orElse(true)) {
                        findings.add(finding("REFERENCE_TARGET_MISMATCH", p.documentId(), p.path(), line, p.id(), id)); continue;
                    }
                    addEdge(edges, new SpecRelations.Edge(p.kind(), p.id(), p.targetKind(), id, p.relation(), new SpecTraceability.Location(p.documentId(), p.path(), line)));
                }
            }
        }
        // Existing v1 edges can only enter when both companion definitions are unique.
        edges.values().removeIf(e -> index.getOrDefault(e.sourceKind() + ":" + e.sourceId(), List.of()).size() != 1 || index.getOrDefault(e.targetKind() + ":" + e.targetId(), List.of()).size() != 1);
        cycles(index, edges.values(), findings);
        boolean truncated = nodes.size() > 2000 || edges.size() > 10000;
        if (truncated) findings.add(new SpecTraceability.Finding("ANALYSIS_LIMIT", "error", null, "", 1, null, null, "관계 분석 한도를 초과해 일부 자료를 제공하지 않습니다."));
        var limitedNodes = nodes.stream().sorted(Comparator.comparing(SpecRelations.Item::key).thenComparing(SpecRelations.Item::path).thenComparingInt(SpecRelations.Item::line)).limit(2000).toList();
        Set<String> retained = new HashSet<>(); limitedNodes.forEach(n -> retained.add(n.key()));
        var limitedEdges = edges.values().stream().filter(e -> retained.contains(e.sourceKind() + ":" + e.sourceId()) && retained.contains(e.targetKind() + ":" + e.targetId())).limit(10000).toList();
        boolean partial = trace.analysisStatus().equals("partial") || findings.stream().anyMatch(f -> f.severity().equals("error"));
        return new SpecRelations(1, partial ? "partial" : nodes.isEmpty() ? "unchecked" : "complete", nodes.isEmpty() ? "NO_CONFIRMED_DEFINITIONS" : null, limitedNodes, limitedEdges,
                findings.stream().distinct().sorted(Comparator.comparing(SpecTraceability.Finding::path).thenComparingInt(SpecTraceability.Finding::line).thenComparing(SpecTraceability.Finding::code)).toList());
    }

    private static void labels(TraceabilityAnalyzer.Source source, Node heading, String kind, String id, Map<String, String> labels, List<Pending> pending) {
        for (Node n = heading.getNext(); n != null; n = n.getNext()) {
            if (n instanceof Heading h && h.getLevel() <= 2) break;
            n.accept(new AbstractVisitor() { @Override public void visit(Paragraph paragraph) {
                if (!(paragraph.getFirstChild() instanceof StrongEmphasis)) return;
                for (var field : TraceabilityAnalyzer.labels(paragraph)) {
                    String label = field.label(), target = labels.get(label); if (target == null) continue;
                    pending.add(new Pending(kind, id, source.path(), source.documentId(), TraceabilityAnalyzer.line(paragraph), target,
                            label.equals("선행:") ? "depends_on" : label.equals("관련 작업:") ? "related_task" : "requires",
                            field.payload()));
                }
            }});
        }
    }
    private static List<String> expand(String token, String kind) {
        String prefix = kind.toUpperCase(Locale.ROOT); String[] ids = token.split("\\s*[~～]\\s*", -1);
        if (ids.length > 2 || Arrays.stream(ids).anyMatch(id -> !id.matches(prefix + "-" + (kind.equals("ui") ? "" : "(?!000)") + "\\d{3}"))) return List.of();
        if (ids.length == 1) return List.of(ids[0]);
        int start = Integer.parseInt(ids[0].substring(prefix.length() + 1)), end = Integer.parseInt(ids[1].substring(prefix.length() + 1));
        if (start > end) return List.of();
        return java.util.stream.IntStream.rangeClosed(start, end).mapToObj(i -> prefix + "-%03d".formatted(i)).toList();
    }
    private static Map<Node, String> anchors(Node root, TraceabilityAnalyzer.Source source) {
        List<Heading> headings = new ArrayList<>(); root.accept(new AbstractVisitor() { @Override public void visit(Heading h) { headings.add(h); visitChildren(h); }});
        Map<Node, String> result = new IdentityHashMap<>();
        for (int i = 0; i < Math.min(headings.size(), source.headings().size()); i++) result.put(headings.get(i), source.headings().get(i).id());
        return result;
    }
    private static List<TableRow> rows(TableBlock table) { List<TableRow> result = new ArrayList<>(); for (Node group = table.getFirstChild(); group != null; group = group.getNext()) for (Node row = group.getFirstChild(); row != null; row = row.getNext()) if (row instanceof TableRow r) result.add(r); return result; }
    private static List<TableCell> cells(TableRow row) { List<TableCell> result = new ArrayList<>(); for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) if (cell instanceof TableCell c) result.add(c); return result; }
    private static String text(Node n) { return TraceabilityAnalyzer.visible(n).text().trim(); }
    private static SpecRelations.Item item(String kind, SpecTraceability.Item i) { return new SpecRelations.Item(kind, i.itemId(), i.title(), i.documentId(), i.path(), i.anchor(), i.line()); }
    private static void addEdge(Map<String, SpecRelations.Edge> edges, SpecRelations.Edge e) { edges.putIfAbsent(e.sourceKind() + ":" + e.sourceId() + ":" + e.relation() + ":" + e.targetKind() + ":" + e.targetId(), e); }
    private static SpecTraceability.Finding finding(String code, String doc, String path, int line, String id, String target) { return new SpecTraceability.Finding(code, "error", doc, path, line, id, target, switch (code) { case "MISSING_REFERENCE" -> "참조한 확정 정의가 없습니다."; case "REFERENCE_TARGET_MISMATCH" -> "링크 경로와 항목 정의가 일치하지 않습니다."; case "DEPENDENCY_CYCLE" -> "선행 작업에 순환이 있습니다."; case "UNSUPPORTED_REFERENCE" -> "ID 또는 범위를 해석할 수 없습니다."; case "DUPLICATE_ITEM_ID" -> "항목 ID가 중복되어 관계를 선택하지 않습니다."; default -> "관계 분석에 필요한 정의나 메타데이터를 확인하세요."; }); }
    private static void cycles(Map<String, List<SpecRelations.Item>> index, Collection<SpecRelations.Edge> edges, List<SpecTraceability.Finding> findings) {
        Map<String, List<String>> next = new TreeMap<>(); for (var e : edges) if (e.relation().equals("depends_on")) next.computeIfAbsent(e.sourceId(), k -> new ArrayList<>()).add(e.targetId());
        Set<String> complete = new HashSet<>(), cyclic = new TreeSet<>(); List<String> stack = new ArrayList<>();
        for (String id : next.keySet()) visit(id, next, complete, stack, cyclic);
        for (String id : cyclic) { var n = index.get("task:" + id).getFirst(); findings.add(finding("DEPENDENCY_CYCLE", n.documentId(), n.path(), n.line(), id, id)); }
    }
    private static void visit(String id, Map<String, List<String>> next, Set<String> complete, List<String> stack, Set<String> cyclic) {
        int position = stack.indexOf(id); if (position >= 0) { cyclic.addAll(stack.subList(position, stack.size())); return; }
        if (!complete.add(id)) return; stack.add(id); for (String target : next.getOrDefault(id, List.of())) visit(target, next, complete, stack, cyclic); stack.removeLast();
    }
}
