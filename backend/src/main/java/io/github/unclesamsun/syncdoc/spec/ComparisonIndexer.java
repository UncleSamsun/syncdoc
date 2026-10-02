package io.github.unclesamsun.syncdoc.spec;
import io.github.unclesamsun.syncdoc.document.SpecMetadataParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.node.*;
import org.commonmark.parser.*;
import org.springframework.stereotype.Component;
@Component
public class ComparisonIndexer {
 private final Parser parser=Parser.builder().extensions(List.of(YamlFrontMatterExtension.create())).includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
 public ComparisonIndex index(List<TraceabilityAnalyzer.Source> sources) {return index(sources,new TraceabilityAnalyzer().analyze(sources));}
 public ComparisonIndex index(List<TraceabilityAnalyzer.Source> sources,SpecTraceability trace) {
  List<ComparisonIndex.DocumentState> states=new ArrayList<>(); List<ComparisonIndex.Item> items=new ArrayList<>();
  List<SpecTraceability.Finding> findings=new ArrayList<>(trace.findings().stream().filter(f->Set.of("INVALID_METADATA","INVALID_DEFINITION","DUPLICATE_ITEM_ID").contains(f.code())).toList());
  Map<String,List<SpecTraceability.Item>> definitions=new HashMap<>();
  for(var item:concat(trace.requirements(),trace.tasks())) definitions.computeIfAbsent(item.documentId(),k->new ArrayList<>()).add(item);
  for(var source:sources) {
   String normalized=source.markdown().replace("\r\n","\n").replace('\r','\n');
   String[] lines=normalized.split("\n",-1); Node root=parser.parse(normalized);
   states.add(new ComparisonIndex.DocumentState(source.documentId(),new SpecMetadataParser().parse(root).status()));
   Map<Integer,Integer> ends=new HashMap<>(); Integer previous=null;
   for(Node node=root.getFirstChild();node!=null;node=node.getNext()) if(node instanceof Heading heading && heading.getLevel()<=2 && !node.getSourceSpans().isEmpty()) {
    int start=node.getSourceSpans().getFirst().getLineIndex(); if(previous!=null) ends.put(previous,start);previous=start;
   }
   if(previous!=null)ends.put(previous,lines.length);
   for(var item:definitions.getOrDefault(source.documentId(),List.of())) {
    int start=item.line()-1;Integer end=ends.get(start);String hash=null;
    if(end!=null&&start>=0&&end<=lines.length)hash=hash(String.join("\n",Arrays.copyOfRange(lines,start,end))+(end<lines.length?"\n":""));
    else findings.add(new SpecTraceability.Finding("INDEX_SECTION_MISSING","error",source.documentId(),source.path(),item.line(),item.itemId(),null,"항목 원문 구간을 확인할 수 없습니다."));
    items.add(new ComparisonIndex.Item(item.itemId().startsWith("REQ-")?"req":"task",item.itemId(),item.documentId(),item.title(),item.anchor(),item.line(),hash));
   }
  }
  items.sort(Comparator.comparing(ComparisonIndex.Item::kind).thenComparing(ComparisonIndex.Item::itemId).thenComparing(ComparisonIndex.Item::documentId).thenComparingInt(ComparisonIndex.Item::line));
  states.sort(Comparator.comparing(ComparisonIndex.DocumentState::documentId));
  return new ComparisonIndex(1,ComparisonIndex.ALGORITHM,findings.isEmpty()?"complete":"partial",List.copyOf(states),List.copyOf(items),List.copyOf(findings));
 }
 private static List<SpecTraceability.Item> concat(List<SpecTraceability.Item> a,List<SpecTraceability.Item> b){var all=new ArrayList<>(a);all.addAll(b);return all;}
 private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
