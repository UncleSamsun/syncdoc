package io.github.unclesamsun.syncdoc.spec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;
@Component
public class TaskContextIndexer {
    public static final List<String> RULE_PATHS = List.of("AGENTS.md", "rules/project-harness.md", "rules/project-settings.md", "rules/spec-writing.md", "rules/identity-and-references.md", "rules/validation.md", "rules/sdd-workflow.md", "rules/github-collaboration.md", "rules/spec-format.json", "templates/README.md");
    public static final List<String> FIELDS = List.of("목적", "근거", "범위", "선행", "산출물", "검증", "완료");
    public record RuleInput(String path, String content) {}
    private record Field(String label, int start, int valueStart) {}
    private final Parser parser = Parser.builder().extensions(List.of(YamlFrontMatterExtension.create(), TablesExtension.create())).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();

    public TaskContextIndex index(List<TraceabilityAnalyzer.Source> sources, SpecTraceability trace, List<RuleInput> rules) {
        List<TaskContextIndex.TaskBlock> tasks = new ArrayList<>();
        Set<String> taskDocuments = new HashSet<>();
        for (var source : sources) {
            if ("tasks".equals(new io.github.unclesamsun.syncdoc.document.SpecMetadataParser().parse(parser.parse(source.markdown())).kind())) taskDocuments.add(source.documentId());
            var declared = trace.tasks().stream().filter(t -> t.documentId().equals(source.documentId())).toList(); if (declared.isEmpty()) continue;
            String markdown = source.markdown().replace("\r\n", "\n").replace('\r', '\n'); int[] offsets = offsets(markdown);
            Node root = parser.parse(markdown);
            for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
                if (!(node instanceof Heading h) || h.getLevel() != 2) continue;
                var definition = declared.stream().filter(t -> t.line() == TraceabilityAnalyzer.line(h)).findFirst().orElse(null); if (definition == null) continue;
                List<Field> fields = new ArrayList<>(); int sectionEnd = markdown.length();
                for (Node block = h.getNext(); block != null; block = block.getNext()) {
                    if (block instanceof Heading next && next.getLevel() <= 2) { sectionEnd = start(next, offsets); break; }
                    block.accept(new AbstractVisitor() { @Override public void visit(Paragraph p) {
                        if (!(p.getFirstChild() instanceof StrongEmphasis)) return;
                        for (var label : TraceabilityAnalyzer.labels(p)) {
                            int start = start(label.source(), offsets), end = end(label.source(), offsets);
                            if (start >= 0 && end >= start) fields.add(new Field(label.label().replaceFirst(":$", ""), start, end));
                        }
                    }});
                }
                fields.sort(Comparator.comparingInt(Field::start));
                Map<String,String> values = new LinkedHashMap<>(); Set<String> seen = new HashSet<>(); List<String> warnings = new ArrayList<>(); boolean truncated = false;
                for (int i = 0; i < fields.size(); i++) {
                    var f = fields.get(i); if (!FIELDS.contains(f.label())) continue;
                    if (!seen.add(f.label())) { values.remove(f.label()); warnings.add("DUPLICATE_FIELD:" + f.label()); continue; }
                    int to = i + 1 < fields.size() ? fields.get(i + 1).start() : sectionEnd;
                    if (to < f.valueStart() || to > markdown.length()) { warnings.add("FIELD_LOCATION_UNAVAILABLE:" + f.label()); continue; }
                    String value = markdown.substring(f.valueStart(), to).trim();
                    if (value.codePointCount(0, value.length()) > 4000) { value = value.substring(0, value.offsetByCodePoints(0, 4000)); truncated = true; warnings.add("TRUNCATED_FIELD:" + f.label()); }
                    if (value.isBlank()) { warnings.add("EMPTY_FIELD:" + f.label()); continue; }
                    values.put(f.label(), value);
                }
                for (String field : FIELDS) if (!values.containsKey(field)) warnings.add("MISSING_FIELD:" + field);
                tasks.add(new TaskContextIndex.TaskBlock(definition.itemId(), source.documentId(), Collections.unmodifiableMap(values), truncated, warnings.stream().distinct().toList()));
            }
        }
        var pins = rules.stream().map(r -> new TaskContextIndex.RulePin(r.path(), r.content() != null, r.content() == null ? null : hash(r.content()))).toList();
        boolean partial = trace.analysisStatus().equals("partial") || tasks.stream().anyMatch(t -> t.truncated() || !t.warnings().isEmpty()) || pins.stream().anyMatch(p -> !p.available());
        boolean definitionsComplete = trace.findings().stream().noneMatch(f -> taskDocuments.contains(f.documentId()) && Set.of("INVALID_METADATA", "INVALID_DEFINITION").contains(f.code()));
        return new TaskContextIndex(1, partial ? "partial" : "complete", definitionsComplete, List.copyOf(tasks), pins);
    }
    private static int[] offsets(String text) { List<Integer> result = new ArrayList<>(List.of(0)); for (int i=0;i<text.length();i++) if(text.charAt(i)=='\n')result.add(i+1);return result.stream().mapToInt(Integer::intValue).toArray(); }
    private static int start(Node n,int[] offsets){if(n.getSourceSpans().isEmpty())return -1;var span=n.getSourceSpans().getFirst();return offsets[span.getLineIndex()]+span.getColumnIndex();}
    private static int end(Node n,int[] offsets){if(n.getSourceSpans().isEmpty())return -1;var span=n.getSourceSpans().getLast();return offsets[span.getLineIndex()]+span.getColumnIndex()+span.getLength();}
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
