package io.github.unclesamsun.syncdoc.spec;
import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.dashboard.TaskMappingService;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.document.domain.*;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
@Service
@Transactional(readOnly=true)
public class SnapshotComparisonService {
 private final ProjectService projects;private final DocumentService documents;private final DocumentSnapshotRepository snapshots;private final DocumentRepository stored;private final TaskMappingService mapping;private final ObjectMapper json;private final ComparisonEngine engine;
 public SnapshotComparisonService(ProjectService projects,DocumentService documents,DocumentSnapshotRepository snapshots,DocumentRepository stored,TaskMappingService mapping,ObjectMapper json,ComparisonEngine engine){this.projects=projects;this.documents=documents;this.snapshots=snapshots;this.stored=stored;this.mapping=mapping;this.json=json;this.engine=engine;}
 public record SnapshotInfo(UUID snapshotId,String sourceRevision,Instant createdAt,String rendererVersion,String policyVersion,String branch,String docsRoot,boolean current,String comparisonReadiness){}
 public record SnapshotPage(List<SnapshotInfo> items,int totalElements,int page,int size){}
 public record Summary(SnapshotInfo from,SnapshotInfo to,String status,String reason,Map<String,Map<String,Long>> counts,String coverage,List<String> findings){}
 public record ResultPage<T>(SnapshotInfo from,SnapshotInfo to,String status,String reason,String coverage,List<String> findings,List<T> items,int totalElements,int page,int size){}
 public record ImpactView(String requirementId,String taskId,String requirementChange,ComparisonEngine.Ref beforeRequirement,ComparisonEngine.Ref afterRequirement,ComparisonEngine.Ref beforeTask,ComparisonEngine.Ref afterTask,String taskPresence,TaskMappingService.TaskView execution){}
 private record Evaluation(SnapshotInfo from,SnapshotInfo to,String reason,ComparisonEngine.Result result){}
 private static final Set<String> CHANGES=Set.of("all","added","removed","modified","moved","moved_modified","unchanged","unknown");
 public SnapshotPage list(CurrentUser user,UUID projectId,int page,int size){
  validate(page,size);var project=projects.view(user,projectId);
  var all=snapshots.findByProjectIdOrderByCreatedAtDesc(projectId).stream().filter(DocumentSnapshotEntity::isComplete)
   .sorted(Comparator.comparing(DocumentSnapshotEntity::getCreatedAt).thenComparing(s->s.getId().toString()).reversed()).map(s->info(s,project.currentSnapshotId())).toList();
  return new SnapshotPage(slice(all,page,size),all.size(),page,size);
 }
 public Summary summary(CurrentUser user,UUID projectId,UUID from,UUID to){
  var evaluation=evaluate(user,projectId,from,to);var result=evaluation.result();
  if(result==null)return new Summary(evaluation.from(),evaluation.to(),"unchecked",evaluation.reason(),null,"unknown",List.of());
  return new Summary(evaluation.from(),evaluation.to(),result.status(),null,Map.of("documents",count(result.documents()),"items",count(result.items())),result.coverage(),result.findings());
 }
 public ResultPage<ComparisonEngine.Row> rows(CurrentUser user,UUID projectId,UUID from,UUID to,String category,String kind,String change,int page,int size){
  validate(page,size);validateChange(change);if(!Set.of("all","req","task").contains(kind))throw new IllegalArgumentException();
  var e=evaluate(user,projectId,from,to);if(e.result()==null)return empty(e,page,size);
  var rows=(category.equals("documents")?e.result().documents():e.result().items()).stream().filter(r->change.equals("all")||r.change().equals(change)).filter(r->category.equals("documents")||kind.equals("all")||r.kind().equals(kind)).toList();
  return page(e,rows,page,size);
 }
 public ResultPage<ImpactView> impacts(CurrentUser user,UUID projectId,UUID from,UUID to,String change,int page,int size){
  validate(page,size);validateChange(change);var e=evaluate(user,projectId,from,to);if(e.result()==null)return empty(e,page,size);
  var filtered=e.result().impacts().stream().filter(i->change.equals("all")||i.requirementChange().equals(change)).toList();
  Map<String,TaskMappingService.TaskView> execution=new HashMap<>();mapping.map(projectId,to).forEach(t->execution.put(t.taskSpecId(),t));
  var rows=filtered.stream().map(i->new ImpactView(i.requirementId(),i.taskId(),i.requirementChange(),i.beforeRequirement(),i.afterRequirement(),i.beforeTask(),i.afterTask(),i.taskPresence(),i.taskPresence().equals("present")?execution.get(i.taskId()):null)).toList();
  return page(e,rows,page,size);
 }
 private Evaluation evaluate(CurrentUser user,UUID projectId,UUID from,UUID to){
  if(from==null||to==null)throw new IllegalArgumentException();var project=projects.view(user,projectId);
  var a=documents.snapshotFor(project,from).orElseThrow(DocumentService.DocumentsNotReadyException::new);
  var b=documents.snapshotFor(project,to).orElseThrow(DocumentService.DocumentsNotReadyException::new);
  var left=info(a,project.currentSnapshotId());var right=info(b,project.currentSnapshotId());
  if(!scopeKnown(a)||!scopeKnown(b))return new Evaluation(left,right,"SCOPE_NOT_RECORDED",null);
  if(!Objects.equals(a.getCollectionBranch(),b.getCollectionBranch())||!Objects.equals(a.getCollectionDocsRoot(),b.getCollectionDocsRoot()))throw new ScopeMismatchException();
  var old=read(a);var now=read(b);if(old==null||now==null)return new Evaluation(left,right,"COMPARISON_INDEX_UNAVAILABLE",null);
  var result=engine.compare(documentData(a,old),old,trace(a),documentData(b,now),now,trace(b));
  return new Evaluation(left,right,null,result);
 }
 public ResultPage<DesignImpactCalculator.Impact> designImpacts(CurrentUser user,UUID projectId,UUID from,UUID to,int page,int size){
  validate(page,size);var e=evaluate(user,projectId,from,to);if(e.result()==null)return empty(e,page,size);
  var old=RelationService.read(snapshots.findById(from).orElseThrow(),json);var now=RelationService.read(snapshots.findById(to).orElseThrow(),json);
  if(old.analysisStatus().equals("unchecked")||now.analysisStatus().equals("unchecked"))return new ResultPage<>(e.from(),e.to(),"unchecked","RELATIONS_NOT_COMPUTED","unknown",List.of(),List.of(),0,page,size);
  var rows=new DesignImpactCalculator().compute(e.result().items(),old,now);
  var findings=new ArrayList<>(e.result().findings());for(var graph:List.of(old,now))for(var finding:graph.findings())findings.add(finding.code()+" "+finding.path()+":"+finding.line()+" "+finding.message());
  boolean partial=e.result().status().equals("partial")||old.analysisStatus().equals("partial")||now.analysisStatus().equals("partial");
  return new ResultPage<>(e.from(),e.to(),partial?"partial":"complete",null,partial?"incomplete":e.result().coverage(),findings.stream().distinct().toList(),slice(rows,page,size),rows.size(),page,size);
 }
 private List<ComparisonEngine.Document> documentData(DocumentSnapshotEntity snapshot,ComparisonIndex index){
  Map<String,String> states=new HashMap<>();index.documents().forEach(d->states.put(d.documentId(),d.status()));
  return stored.findBySnapshotIdOrderByPath(snapshot.getId()).stream().map(d->new ComparisonEngine.Document(d.getId().toString(),d.getSpecId(),d.getPath(),d.getTitle(),d.getKind(),d.getSourceHash(),states.get(d.getId().toString()))).toList();
 }
 private SpecTraceability trace(DocumentSnapshotEntity s){
  try {var report=s.getTraceabilityJson()==null?null:json.readValue(s.getTraceabilityJson(),SpecTraceability.class);return report!=null&&Objects.equals(report.schemaVersion(),1)&&report.edges()!=null&&Set.of("complete","partial","unchecked").contains(report.analysisStatus())?report:SpecTraceability.notComputed();}catch(RuntimeException e){return SpecTraceability.notComputed();}
 }
 private ComparisonIndex read(DocumentSnapshotEntity snapshot){
  if(snapshot.getComparisonJson()==null)return null;
  try {
   if(json.readTree(snapshot.getComparisonJson()).path("schemaVersion").asInt()!=1)return null;
   var index=json.readValue(snapshot.getComparisonJson(),ComparisonIndex.class);
   if(index.fingerprintAlgorithm()==null||index.fingerprintAlgorithm().isBlank()||index.items()==null||index.documents()==null||index.findings()==null||!Set.of("complete","partial").contains(index.indexStatus()))return null;
   if(index.items().stream().anyMatch(i->i==null||i.documentId()==null||i.itemId()==null||i.kind()==null||!Set.of("req","task").contains(i.kind())))return null;
   if(index.documents().stream().anyMatch(d->d==null||d.documentId()==null))return null;
   return index;
  }catch(RuntimeException e){return null;}
 }
 private SnapshotInfo info(DocumentSnapshotEntity s,UUID current){
  var index=read(s);String ready=!scopeKnown(s)||index==null?"legacy":index.indexStatus().equals("partial")||!ComparisonIndex.ALGORITHM.equals(index.fingerprintAlgorithm())?"partial":"ready";
  return new SnapshotInfo(s.getId(),s.getSourceRevision(),s.getCreatedAt(),s.getRendererVersion(),s.getPolicyVersion(),s.getCollectionBranch(),s.getCollectionDocsRoot(),s.getId().equals(current),ready);
 }
 private static boolean scopeKnown(DocumentSnapshotEntity s){return s.getCollectionBranch()!=null&&!s.getCollectionBranch().isBlank()&&s.getCollectionDocsRoot()!=null&&!s.getCollectionDocsRoot().isBlank();}
 private static Map<String,Long> count(List<ComparisonEngine.Row> rows){Map<String,Long> counts=new LinkedHashMap<>();for(String change:List.of("added","removed","modified","moved","moved_modified","unchanged","unknown"))counts.put(change,rows.stream().filter(r->r.change().equals(change)).count());return counts;}
 private static void validateChange(String change){if(!CHANGES.contains(change))throw new IllegalArgumentException();}
 private static void validate(int page,int size){if(page<0||size<1||size>100)throw new IllegalArgumentException();}
 private static <T> List<T> slice(List<T> rows,int page,int size){int offset=(int)Math.min(rows.size(),(long)page*size);return List.copyOf(rows.subList(offset,(int)Math.min(rows.size(),(long)offset+size)));}
 private static <T> ResultPage<T> page(Evaluation e,List<T> rows,int page,int size){return new ResultPage<>(e.from(),e.to(),e.result().status(),null,e.result().coverage(),e.result().findings(),slice(rows,page,size),rows.size(),page,size);}
 private static <T> ResultPage<T> empty(Evaluation e,int page,int size){return new ResultPage<>(e.from(),e.to(),"unchecked",e.reason(),"unknown",List.of(),List.of(),0,page,size);}
 public static class ScopeMismatchException extends RuntimeException{}
}
