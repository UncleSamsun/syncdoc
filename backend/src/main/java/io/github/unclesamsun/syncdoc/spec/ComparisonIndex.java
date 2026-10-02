package io.github.unclesamsun.syncdoc.spec;
import java.util.List;
public record ComparisonIndex(int schemaVersion,String fingerprintAlgorithm,String indexStatus,
 List<DocumentState> documents,List<Item> items,List<SpecTraceability.Finding> findings) {
 public static final String ALGORITHM="markdown-section-lf-v1";
 public record DocumentState(String documentId,String status) {}
 public record Item(String kind,String itemId,String documentId,String title,String anchor,int line,String sectionHash) {}
}
