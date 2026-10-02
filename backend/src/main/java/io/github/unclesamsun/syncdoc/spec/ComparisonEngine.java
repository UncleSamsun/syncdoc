package io.github.unclesamsun.syncdoc.spec;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class ComparisonEngine {
 public record Document(String documentId,String specId,String path,String title,String kind,String sourceHash,String status) {}
 public record Ref(String documentId,String specId,String path,String title,String kind,String itemId,String anchor,int line,String status) {}
 public record Row(String key,String kind,String change,String reason,Ref before,Ref after) {}
 public record Impact(String requirementId,String taskId,String requirementChange,Ref beforeRequirement,Ref afterRequirement,Ref beforeTask,Ref afterTask,String taskPresence) {}
 public record Result(String status,String coverage,List<Row> documents,List<Row> items,List<Impact> impacts,List<String> findings) {}
 public Result compare(List<Document> beforeDocs,ComparisonIndex before,SpecTraceability beforeTrace,List<Document> afterDocs,ComparisonIndex after,SpecTraceability afterTrace) {
  Map<String,Document> oldByUuid=byUuid(beforeDocs),newByUuid=byUuid(afterDocs);
  Map<String,List<Document>> oldDocs=group(beforeDocs,ComparisonEngine::documentKey),newDocs=group(afterDocs,ComparisonEngine::documentKey);
  List<String> findings=new ArrayList<>(); List<Row> documents=new ArrayList<>(),items=new ArrayList<>();
  for(String key:union(oldDocs.keySet(),newDocs.keySet())) {
   var old=oldDocs.getOrDefault(key,List.of());var now=newDocs.getOrDefault(key,List.of());Document a=single(old),b=single(now);
   String reason=null;String change;
   if(old.size()>1||now.size()>1||key.startsWith("invalid:")){change="unknown";reason="invalid_or_duplicate_document_id";}
   else if(a==null||b==null){change=a==null?"added":"removed";Document present=a==null?b:a;
    if((a==null?beforeDocs:afterDocs).stream().anyMatch(d->d.path().equals(present.path())&&!Objects.equals(d.specId(),present.specId()))){reason="identity_changed";findings.add(reason+": "+present.path());}
   } else change=classify(!a.path().equals(b.path()),a.sourceHash(),b.sourceHash(),true);
   documents.add(new Row(key,"document",change,reason,ref(a),ref(b)));
  }
  var oldItems=group(before.items(),i->i.kind()+":"+i.itemId());var newItems=group(after.items(),i->i.kind()+":"+i.itemId());
  boolean algorithm=Objects.equals(before.fingerprintAlgorithm(),after.fingerprintAlgorithm())&&ComparisonIndex.ALGORITHM.equals(before.fingerprintAlgorithm());
  for(String key:union(oldItems.keySet(),newItems.keySet())) {
   var old=oldItems.getOrDefault(key,List.of());var now=newItems.getOrDefault(key,List.of());var a=single(old);var b=single(now);
   Ref left=ref(a,oldByUuid),right=ref(b,newByUuid);String change,reason=null;
   if(old.size()>1||now.size()>1){change="unknown";reason="duplicate_item_id";}
   else if(a==null||b==null){
    if((a==null?before:after).indexStatus().equals("partial")){change="unknown";reason="incomplete_index";}
    else {change=a==null?"added":"removed";reason="confirmed_definition_set";}
   } else if(left==null||right==null){change="unknown";reason="item_document_missing";}
   else {change=classify(!sameLocation(left,right),a.sectionHash(),b.sectionHash(),algorithm);if(change.equals("unknown"))reason="fingerprint_unavailable";}
   items.add(new Row(key,key.substring(0,key.indexOf(':')),change,reason,left,right));
  }
  List<Impact> impacts=new ArrayList<>();
  for(Row requirement:items) if(requirement.kind().equals("req")&&Set.of("added","removed","modified","moved_modified").contains(requirement.change())) {
   String id=requirement.key().substring(4);Set<String> taskIds=new TreeSet<>();
   for(var trace:List.of(beforeTrace,afterTrace))for(var edge:trace.edges())if(edge.requirementId().equals(id))taskIds.add(edge.taskId());
   for(String taskId:taskIds) {
    var old=oldItems.getOrDefault("task:"+taskId,List.of());var now=newItems.getOrDefault("task:"+taskId,List.of());
    Ref left=ref(single(old),oldByUuid),right=ref(single(now),newByUuid);
    String presence=now.size()==1&&right!=null?"present":now.isEmpty()&&after.indexStatus().equals("complete")?"removed":"unknown";
    impacts.add(new Impact(id,taskId,requirement.change(),requirement.before(),requirement.after(),left,right,presence));
   }
  }
  String coverage;
  if(before.indexStatus().equals("complete")&&after.indexStatus().equals("complete")&&before.items().stream().noneMatch(i->i.kind().equals("req"))&&after.items().stream().noneMatch(i->i.kind().equals("req")))coverage="not_applicable";
  else if(beforeTrace.analysisStatus().equals("unchecked")||afterTrace.analysisStatus().equals("unchecked"))coverage="unknown";
  else if(beforeTrace.analysisStatus().equals("partial")||afterTrace.analysisStatus().equals("partial"))coverage="incomplete";
  else coverage="complete";
  boolean partial=before.indexStatus().equals("partial")||after.indexStatus().equals("partial")||concat(documents,items).stream().anyMatch(r->r.change().equals("unknown"));
  for(var finding:before.findings())findings.add(describe("from",finding));
  for(var finding:after.findings())findings.add(describe("to",finding));
  for(var finding:beforeTrace.findings())findings.add(describe("from",finding));
  for(var finding:afterTrace.findings())findings.add(describe("to",finding));
  return new Result(partial?"partial":"complete",coverage,List.copyOf(documents),List.copyOf(items),List.copyOf(impacts),findings.stream().distinct().sorted().toList());
 }
 private static String describe(String side,SpecTraceability.Finding f){return side+": "+f.code()+" "+f.path()+":"+f.line()+" "+Objects.toString(f.targetId(),"")+" "+f.message();}
 private static String classify(boolean moved,String a,String b,boolean compatible){if(!compatible||!hashValid(a)||!hashValid(b))return "unknown";boolean changed=!a.equals(b);return moved?(changed?"moved_modified":"moved"):(changed?"modified":"unchanged");}
 private static boolean hashValid(String hash){return hash!=null&&hash.matches("[a-f0-9]{64}");}
 private static String documentKey(Document d){return d.specId()==null||d.specId().isBlank()?"path:"+d.path():d.specId().matches("DOC-\\d{3}")?"id:"+d.specId():"invalid:"+d.documentId();}
 private static Ref ref(Document d){return d==null?null:new Ref(d.documentId(),d.specId(),d.path(),d.title(),d.kind(),null,null,0,d.status());}
 private static Ref ref(ComparisonIndex.Item i,Map<String,Document> docs){if(i==null)return null;var d=docs.get(i.documentId());return d==null?null:new Ref(d.documentId(),d.specId(),d.path(),i.title(),i.kind(),i.itemId(),i.anchor(),i.line(),d.status());}
 private static boolean sameLocation(Ref a,Ref b){return Objects.equals(a.path(),b.path())&&Objects.equals(a.specId(),b.specId());}
 private static Map<String,Document> byUuid(List<Document> docs){Map<String,Document> result=new HashMap<>();for(var d:docs)result.put(d.documentId(),d);return result;}
 private static <T> T single(List<T> items){return items.size()==1?items.getFirst():null;}
 private static <T> Map<String,List<T>> group(List<T> items,java.util.function.Function<T,String> key){Map<String,List<T>> map=new TreeMap<>();for(var i:items)map.computeIfAbsent(key.apply(i),k->new ArrayList<>()).add(i);return map;}
 private static Set<String> union(Set<String> a,Set<String>b){Set<String> all=new TreeSet<>(a);all.addAll(b);return all;}
 private static <T> List<T> concat(List<T>a,List<T>b){var all=new ArrayList<>(a);all.addAll(b);return all;}
}
