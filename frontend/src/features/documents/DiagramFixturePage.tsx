import DiagramView from "./DiagramView";
import type { DocumentDiagram } from "./types";

/**
 * 다이어그램 다섯 유형과 실패 처리를 눈으로 확인하는 fixture 화면이다.
 *
 * <p>화면 명세의 `fixture 표시` 규칙을 따른다 — 실제 GitHub 조회 결과로 보이지 않게 `샘플 데이터`를
 * 화면에 표시하고, fixture에는 실행 가능한 script를 넣지 않는다. 제품 화면이 아니라 확인용이다.
 */
const SAMPLES: DocumentDiagram[] = [
  {
    id: "flowchart",
    syntax: "mermaid",
    source: "flowchart TD\n  A[연결] --> B[수집]\n  B --> C{변경?}\n  C -->|있음| D[게시]\n  C -->|없음| E[유지]",
  },
  {
    id: "sequence",
    syntax: "mermaid",
    source:
      "sequenceDiagram\n  participant 사용자\n  participant SyncDoc\n  participant GitHub\n"
      + "  사용자->>SyncDoc: 문서 열기\n  SyncDoc->>GitHub: 권한 확인\n  GitHub-->>SyncDoc: 허용\n"
      + "  SyncDoc-->>사용자: 본문",
  },
  {
    id: "er",
    syntax: "mermaid",
    source:
      "erDiagram\n  PROJECTS ||--o{ DOCUMENT_SNAPSHOTS : has\n"
      + "  DOCUMENT_SNAPSHOTS ||--o{ DOCUMENTS : contains\n"
      + "  DOCUMENT_SNAPSHOTS ||--o{ ASSETS : contains",
  },
  {
    id: "class",
    syntax: "mermaid",
    source:
      "classDiagram\n  class SyncQueue {\n    +request()\n    +claimNext()\n    +publish()\n  }\n"
      + "  class SyncWorker {\n    +runOnce()\n  }\n  SyncWorker --> SyncQueue",
  },
  {
    id: "state",
    syntax: "mermaid",
    source:
      "stateDiagram-v2\n  [*] --> queued\n  queued --> running\n  running --> succeeded\n"
      + "  running --> failed\n  failed --> queued: 재시도\n  succeeded --> [*]",
  },
  {
    id: "broken",
    syntax: "mermaid",
    source: "flowchart TD\n  A -->--> ((((",
  },
];

export default function DiagramFixturePage() {
  return (
    <main className="doc">
      <h1>다이어그램 확인</h1>
      <div className="dmeta">
        <span>샘플 데이터</span>
        <span>실제 GitHub 조회 결과가 아닙니다</span>
      </div>
      {SAMPLES.map((diagram, index) => (
        <section key={diagram.id}>
          <h2>{diagram.id}</h2>
          <DiagramView diagram={diagram} index={index} />
        </section>
      ))}
    </main>
  );
}
