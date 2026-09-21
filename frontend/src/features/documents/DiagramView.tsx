import { useEffect, useRef, useState } from "react";
import type { DocumentDiagram } from "./types";

/** UI-003이 정한 제한. 넘으면 그리지 않고 원문 보기로 대체한다. */
export const DIAGRAM_LIMITS = {
  maxBytes: 20 * 1024,
  maxPerDocument: 20,
  renderTimeoutMs: 3000,
};

type Props = {
  diagram: DocumentDiagram;
  index: number;
  /** 문서당 상한을 넘은 블록이다. 그리지 않고 원문만 준다. */
  overCountLimit?: boolean;
};

type Render =
  | { state: "pending" }
  | { state: "drawn"; svg: string }
  | { state: "failed"; reason: string };

/**
 * Mermaid는 한 번만 불러오고, 블록은 한 번에 하나씩 그린다.
 *
 * <p>제한 3초는 **그리는 시간**이다. 모듈을 처음 불러오는 시간이나 앞 블록을 기다린 시간까지
 * 제한에 넣으면, 다이어그램이 여러 개인 문서에서 뒤쪽 블록이 멀쩡한데도 모두 시간 초과가 된다.
 * 실제 브라우저에서 클래스·상태도가 그렇게 걸리는 것을 보고 고쳤다.
 */
let loader: Promise<typeof import("mermaid").default> | null = null;
let queue: Promise<unknown> = Promise.resolve();

async function draw(id: string, source: string, timeoutMs: number): Promise<string> {
  loader ??= import("mermaid").then(({ default: mermaid }) => {
    mermaid.initialize({
      startOnLoad: false,
      // 원문이 HTML이나 스크립트로 해석되지 않게 가장 엄격한 설정만 쓴다.
      securityLevel: "strict",
      htmlLabels: false,
      theme: "neutral",
      fontFamily: "inherit",
    });
    return mermaid;
  });
  const mermaid = await loader;

  const slot = queue.then(async () => {
    const timeout = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error("시간이 초과되었습니다")), timeoutMs);
    });
    const { svg } = await Promise.race([mermaid.render(id, source), timeout]);
    return svg;
  });
  // 한 블록이 실패해도 다음 블록은 계속 그린다.
  queue = slot.catch(() => undefined);
  return slot;
}

/** 원문 크기를 사람이 읽는 단위로. 오류 상자의 `원문 보기 (<크기>)`에 쓴다. */
function sizeOf(source: string): string {
  const bytes = new TextEncoder().encode(source).length;
  return bytes < 1024 ? `${bytes}B` : `${(bytes / 1024).toFixed(1)}KB`;
}

/** 첫 줄에서 유형을 읽는다. 지원 목록 밖이면 그리지 않는다. */
export function kindOf(source: string): string | null {
  const first = source.trim().split(/\s|\n/)[0]?.toLowerCase() ?? "";
  const supported: Record<string, string> = {
    flowchart: "흐름도",
    graph: "흐름도",
    sequencediagram: "시퀀스",
    erdiagram: "ERD",
    classdiagram: "클래스",
    statediagram: "상태도",
    "statediagram-v2": "상태도",
  };
  return supported[first] ?? null;
}

/**
 * 다이어그램 한 블록.
 *
 * <p>블록 하나가 실패해도 그 자리만 오류 상자로 바뀐다. 문서의 나머지는 그대로 읽을 수 있어야 한다.
 * 원문은 Mermaid에만 넘기고 어떤 경로로도 코드로 평가하지 않는다.
 */
export default function DiagramView({ diagram, index, overCountLimit = false }: Props) {
  const [render, setRender] = useState<Render>({ state: "pending" });
  const [showSource, setShowSource] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const kind = kindOf(diagram.source);
  const size = sizeOf(diagram.source);

  useEffect(() => {
    let cancelled = false;
    const bytes = new TextEncoder().encode(diagram.source).length;

    if (overCountLimit) {
      setRender({ state: "failed", reason: `문서당 ${DIAGRAM_LIMITS.maxPerDocument}개를 넘었습니다` });
      return;
    }
    if (bytes > DIAGRAM_LIMITS.maxBytes) {
      setRender({ state: "failed", reason: "원문이 너무 큽니다" });
      return;
    }
    if (!kind) {
      setRender({ state: "failed", reason: "지원하지 않는 유형입니다" });
      return;
    }

    draw(`diagram-${diagram.id}-${index}`, diagram.source, DIAGRAM_LIMITS.renderTimeoutMs)
      .then((svg) => {
        if (!cancelled) {
          setRender({ state: "drawn", svg });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          const reason = error instanceof Error ? error.message : "그릴 수 없습니다";
          setRender({ state: "failed", reason });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [diagram.id, diagram.source, index, kind, overCountLimit]);

  useEffect(() => {
    if (render.state === "drawn" && container.current) {
      // Mermaid가 만든 SVG다. 원문이 아니라 그려진 결과를 넣는다.
      container.current.innerHTML = render.svg;
    }
  }, [render]);

  if (render.state === "failed") {
    return (
      <figure className="dgm-err" data-diagram-id={diagram.id}>
        <div className="bar">
          <span>diagram {index + 1}</span>
          <span>{render.reason}</span>
          <button type="button" className="r" onClick={() => setShowSource((open) => !open)}>
            원문 보기 ({size})
          </button>
        </div>
        <div className="bd">
          <b>이 블록만 표시되지 않습니다.</b>
          문서의 나머지는 그대로 읽을 수 있습니다.
          {showSource && <pre>{diagram.source}</pre>}
        </div>
      </figure>
    );
  }

  return (
    <figure className="dgm" data-diagram-id={diagram.id}>
      <div className="bar">
        <span>
          diagram {index + 1} · {kind}
        </span>
        <button type="button" className="r" onClick={() => setShowSource((open) => !open)}>
          원문 보기
        </button>
      </div>
      {showSource && <pre>{diagram.source}</pre>}
      <div ref={container} aria-label={`다이어그램 ${index + 1}`} />
    </figure>
  );
}
