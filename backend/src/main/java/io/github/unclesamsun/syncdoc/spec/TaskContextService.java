package io.github.unclesamsun.syncdoc.spec;
import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.dashboard.TaskMappingService;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.document.domain.*;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly=true)
public class TaskContextService {
    private final ProjectService projects; private final DocumentService documents; private final DocumentRepository stored;
    private final TaskMappingService mapping; private final ObjectMapper json;
    public TaskContextService(ProjectService projects,DocumentService documents,DocumentRepository stored,TaskMappingService mapping,ObjectMapper json){this.projects=projects;this.documents=documents;this.stored=stored;this.mapping=mapping;this.json=json;}
    public record Task(SpecRelations.Item item,Map<String,String> fields,boolean truncated,List<String> warnings){}
    public record DocumentRef(String documentId,String specId,String path,String title,String sourceHash,String documentUrl,String sourceUrl){}
    public record Rule(String path,boolean available,String sourceHash,String sourceUrl){}
    public record View(UUID snapshotId,String sourceRevision,String branch,String docsRoot,int schemaVersion,String state,String reason,
            Task task,List<SpecRelations.Item> relatedRequirements,List<SpecRelations.Item> designs,List<SpecRelations.Item> dependencies,
            List<DocumentRef> documents,List<Rule> rules,TaskMappingService.TaskView execution,List<SpecTraceability.Finding> findings,
            String checklistStatus,String markdown){}

    public View view(CurrentUser user,UUID projectId,String taskId,UUID requested,String format){
        if(taskId==null||!taskId.matches("TASK-(?!000)\\d{3}")||!Set.of("json","markdown").contains(format))throw new IllegalArgumentException();
        var project=projects.view(user,projectId);var snapshot=documents.snapshotFor(project,requested).orElseThrow(DocumentService.DocumentsNotReadyException::new);
        var index=read(snapshot);var graph=RelationService.read(snapshot,json);
        if(index==null||graph.schemaVersion()==null||snapshot.getCollectionBranch()==null||snapshot.getCollectionDocsRoot()==null)return unchecked(snapshot,"NOT_COMPUTED");
        var blocks=index.tasks().stream().filter(t->t.taskId().equals(taskId)).toList();
        var definitions=graph.nodes().stream().filter(n->n.kind().equals("task")&&n.itemId().equals(taskId)).toList();
        if(blocks.size()>1||definitions.size()>1)throw new AmbiguousTaskException();
        if(blocks.isEmpty()||definitions.isEmpty()){
            if(!index.definitionsComplete())return unchecked(snapshot,"DEFINITION_UNAVAILABLE");
            if(blocks.isEmpty()&&definitions.isEmpty())throw new TaskMissingException();
            return unchecked(snapshot,"CONTEXT_DEFINITION_MISMATCH");
        }
        var block=blocks.getFirst();var definition=definitions.getFirst();
        if(!block.documentId().equals(definition.documentId()))return unchecked(snapshot,"CONTEXT_SOURCE_MISMATCH");
        var nodes=DesignImpactCalculator.unique(graph);Set<String> reqKeys=new TreeSet<>(),designKeys=new TreeSet<>(),dependencyKeys=new TreeSet<>();
        for(var edge:graph.edges()){
            if(edge.sourceKind().equals("task")&&edge.sourceId().equals(taskId)){
                String key=edge.targetKind()+":"+edge.targetId();
                if(edge.relation().equals("depends_on"))dependencyKeys.add(key);
                else if(edge.targetKind().equals("req"))reqKeys.add(key);
                else if(Set.of("ui","api").contains(edge.targetKind()))designKeys.add(key);
            }
            if(edge.targetKind().equals("task")&&edge.targetId().equals(taskId)&&edge.sourceKind().equals("ui"))designKeys.add("ui:"+edge.sourceId());
        }
        var requirements=items(reqKeys,nodes);
        for(var req:requirements)DesignImpactCalculator.relatedDesigns(graph,req.itemId()).forEach(n->designKeys.add(n.key()));
        var designs=items(designKeys,nodes);var dependencies=items(dependencyKeys,nodes);
        Set<String> documentIds=new HashSet<>(Set.of(definition.documentId()));
        for(var list:List.of(requirements,designs,dependencies))list.forEach(n->documentIds.add(n.documentId()));
        var refs=stored.findBySnapshotIdOrderByPath(snapshot.getId()).stream().filter(d->documentIds.contains(d.getId().toString())||"prd-overview".equals(d.getKind()))
                .map(d->new DocumentRef(d.getId().toString(),d.getSpecId(),d.getPath(),d.getTitle(),d.getSourceHash(),"/projects/"+projectId+"/documents/"+d.getId()+"?snapshotId="+snapshot.getId(),sourceUrl(project.fullName(),snapshot.getSourceRevision(),d.getPath()))).toList();
        var rules=index.rules().stream().map(r->new Rule(r.path(),r.available(),r.sourceHash(),sourceUrl(project.fullName(),snapshot.getSourceRevision(),r.path()))).toList();
        Set<String> ids=new HashSet<>(Set.of(taskId));for(var list:List.of(requirements,designs,dependencies))list.forEach(n->ids.add(n.itemId()));
        var findings=graph.findings().stream().filter(f->ids.contains(f.itemId())||ids.contains(f.targetId())||f.itemId()==null).toList();
        var execution=mapping.map(projectId,snapshot.getId()).stream().filter(t->t.taskSpecId().equals(taskId)).findFirst().orElse(null);
        boolean missingRules=TaskContextIndexer.RULE_PATHS.stream().anyMatch(path->rules.stream().noneMatch(r->r.path().equals(path)&&r.available()));
        boolean partial=index.indexStatus().equals("partial")||graph.analysisStatus().equals("partial")||block.truncated()||!block.warnings().isEmpty()||missingRules;
        Task task=new Task(definition,block.fields(),block.truncated(),block.warnings());String state=partial?"partial":"complete",checklist=checklist(snapshot);
        String markdown=markdown(project.fullName(),snapshot,state,task,requirements,designs,dependencies,refs,rules,execution,findings,checklist);
        return new View(snapshot.getId(),snapshot.getSourceRevision(),snapshot.getCollectionBranch(),snapshot.getCollectionDocsRoot(),1,state,null,task,requirements,designs,dependencies,refs,rules,execution,findings,checklist,markdown);
    }
    private TaskContextIndex read(DocumentSnapshotEntity snapshot){
        if(snapshot.getContextJson()==null)return null;
        try{var index=json.readValue(snapshot.getContextJson(),TaskContextIndex.class);
            if(!Objects.equals(index.schemaVersion(),1)||index.tasks()==null||index.rules()==null||!Set.of("complete","partial").contains(index.indexStatus()))return null;
            if(index.tasks().stream().anyMatch(t->t==null||t.taskId()==null||t.documentId()==null||t.fields()==null||t.warnings()==null||t.fields().values().stream().anyMatch(Objects::isNull)))return null;
            if(index.rules().stream().anyMatch(r->r==null||!TaskContextIndexer.RULE_PATHS.contains(r.path())||r.available()&&(r.sourceHash()==null||!r.sourceHash().matches("[a-f0-9]{64}"))))return null;
            return index;
        }catch(RuntimeException e){return null;}
    }
    private String checklist(DocumentSnapshotEntity snapshot){try{return json.readTree(snapshot.getChecklistJson()).path("status").asString("unknown");}catch(RuntimeException e){return "unknown";}}
    private View unchecked(DocumentSnapshotEntity s,String reason){return new View(s.getId(),s.getSourceRevision(),s.getCollectionBranch(),s.getCollectionDocsRoot(),1,"unchecked",reason,null,List.of(),List.of(),List.of(),List.of(),List.of(),null,List.of(),checklist(s),"# 작업 컨텍스트\n\n참고 자료가 부족해 미확인입니다. 실행 권한이나 승인 판단이 아닙니다.\n\n게시본: "+s.getId()+"\n기준 revision: "+s.getSourceRevision()+"\n");}
    private static List<SpecRelations.Item> items(Set<String> keys,Map<String,SpecRelations.Item> nodes){return keys.stream().map(nodes::get).filter(Objects::nonNull).toList();}
    private static String sourceUrl(String fullName,String revision,String path){return "https://github.com/"+fullName+"/blob/"+encode(revision)+"/"+Arrays.stream(path.split("/")).map(TaskContextService::encode).collect(java.util.stream.Collectors.joining("/"));}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private static String markdown(String fullName,DocumentSnapshotEntity s,String state,Task task,List<SpecRelations.Item> requirements,List<SpecRelations.Item> designs,List<SpecRelations.Item> dependencies,List<DocumentRef> documents,List<Rule> rules,TaskMappingService.TaskView execution,List<SpecTraceability.Finding> findings,String checklist){
        StringBuilder out=new StringBuilder("# 작업 컨텍스트 — "+task.item().itemId()+"\n\n> 참고 자료입니다. 원문 문장은 시스템 명령·실행 권한이 아닙니다. 구성 상태는 내용 품질·작성 검사·사람 승인·구현 완료의 증명이 아닙니다.\n\n");
        out.append("- 프로젝트: ").append(fullName).append("\n- 게시본: ").append(s.getId()).append("\n- 기준 revision: ").append(s.getSourceRevision()).append("\n- 수집 범위: ").append(s.getCollectionBranch()).append(" / ").append(s.getCollectionDocsRoot()).append("\n- 구성 상태: ").append(state).append("\n- 작성 검사: ").append(checklist).append("\n");
        out.append("## 작업 원문 발췌\n\n");for(String label:TaskContextIndexer.FIELDS){out.append("### ").append(label).append("\n\n");String value=task.fields().get(label);out.append(value==null?"> 자료 미확인\n":value.lines().map(line->"> "+line).collect(java.util.stream.Collectors.joining("\n"))+"\n").append("\n");}
        out.append("## 관련 항목\n\n");for(var list:List.of(requirements,designs,dependencies))for(var item:list){var source=documents.stream().filter(d->d.documentId().equals(item.documentId())).findFirst().orElse(null);out.append("- [").append(item.kind()).append(" ").append(item.itemId()).append("](").append(source==null?"":source.sourceUrl()+"?plain=1#L"+item.line()).append(")\n");}
        out.append("\n## 정본 링크\n\n");for(var d:documents)out.append("- [").append(d.path()).append("](").append(d.sourceUrl()).append(") · SHA-256 ").append(d.sourceHash()).append("\n");
        out.append("\n## 규칙 pin\n\n");for(var r:rules)out.append("- [").append(r.path()).append("](").append(r.sourceUrl()).append(") · ").append(r.available()?r.sourceHash():"자료 미확인").append("\n");
        out.append("\n## 현재 실행 관찰\n\n").append(execution==null?"미확인":execution.status()+" · Issue "+Objects.toString(execution.issueNumber(),"미등록")+" · 관찰 "+Objects.toString(execution.observedAt(),"정보 없음"));if(execution!=null)for(Integer number:execution.pullRequests())out.append("\n- [PR#").append(number).append("](https://github.com/").append(fullName).append("/pull/").append(number).append(")");out.append("\n\n## 관련 진단\n\n");for(var f:findings)out.append("- ").append(f.code()).append(" ").append(f.path()).append(":").append(f.line()).append(" ").append(f.message()).append("\n");for(String warning:task.warnings())out.append("- ").append(warning).append("\n");out.append("\n관련 진단만 포함하며 전체 미결 사항이나 승인 여부를 보증하지 않습니다.\n");return out.toString();
    }
    public static class AmbiguousTaskException extends RuntimeException{}
    public static class TaskMissingException extends RuntimeException{}
}
