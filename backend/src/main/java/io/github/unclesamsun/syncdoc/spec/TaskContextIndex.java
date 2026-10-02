package io.github.unclesamsun.syncdoc.spec;
import java.util.*;
public record TaskContextIndex(Integer schemaVersion, String indexStatus, boolean definitionsComplete, List<TaskBlock> tasks, List<RulePin> rules) {
    public record TaskBlock(String taskId, String documentId, Map<String,String> fields, boolean truncated, List<String> warnings) {}
    public record RulePin(String path, boolean available, String sourceHash) {}
}
