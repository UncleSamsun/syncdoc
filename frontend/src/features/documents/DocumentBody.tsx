import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useLocation, useNavigate } from "react-router-dom";
import DiagramView, { DIAGRAM_LIMITS } from "./DiagramView";
import type { DocumentDiagram } from "./types";

type Props = {
  html: string;
  diagrams: DocumentDiagram[];
  /** 화면이 이미 제목으로 보여준 값. 본문 첫 제목과 같으면 본문에서 뺀다. */
  title: string;
};

/**
 * 문서 본문.
 *
 * <p>`html`은 서버가 정화한 결과이므로 화면에서 원문 Markdown을 다시 해석하지 않는다. 다이어그램은
 * 본문에 남은 자리 표시에만 그려 넣고, 원문은 별도로 받은 값만 쓴다.
 */
export default function DocumentBody({ html, diagrams, title }: Props) {
  const body = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { hash } = useLocation();
  const [slots, setSlots] = useState<{ id: string; node: Element }[]>([]);

  const diagramsById = useMemo(() => {
    const map = new Map<string, { diagram: DocumentDiagram; index: number }>();
    diagrams.forEach((diagram, index) => map.set(diagram.id, { diagram, index }));
    return map;
  }, [diagrams]);

  useEffect(() => {
    const root = body.current;
    if (!root) {
      return;
    }
    root.innerHTML = html;

    // 문서의 첫 제목이 화면 제목과 같으면 본문에서 뺀다. 같은 제목을 두 번 읽게 하지 않는다.
    // 다만 그 제목을 가리키는 앵커는 남긴다. 검색 결과·목차·문서 간 링크가 그 id로 찾아온다.
    const first = root.firstElementChild;
    if (first?.tagName === "H1" && first.textContent?.trim() === title.trim()) {
      const anchorId = first.getAttribute("id");
      if (anchorId) {
        const marker = document.createElement("span");
        marker.id = anchorId;
        first.replaceWith(marker);
      } else {
        first.remove();
      }
    }

    // 표는 자기 컨테이너에서 가로로 스크롤한다. 본문 전체가 가로로 밀리지 않게 한다.
    root.querySelectorAll("table").forEach((table) => {
      table.classList.add("mdtbl");
      if (table.parentElement?.classList.contains("tblwrap")) {
        return;
      }
      const wrapper = document.createElement("div");
      wrapper.className = "tblwrap";
      table.replaceWith(wrapper);
      wrapper.append(table);
    });

    setSlots(
      Array.from(root.querySelectorAll("[data-diagram-id]")).map((node) => ({
        id: node.getAttribute("data-diagram-id") ?? "",
        node,
      })),
    );

    // 검색 결과나 작업 표에서 앵커가 붙은 주소로 들어왔다. 본문을 그린 뒤라야 그 자리를 찾을 수 있다.
    if (hash) {
      document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: "start" });
    }
  }, [html, title, hash]);

  /** 문서 안 링크는 화면을 새로 불러오지 않고 옮겨 간다. 앵커는 그 자리에서 이동한다. */
  const onClick = (event: React.MouseEvent<HTMLDivElement>) => {
    const anchor = (event.target as HTMLElement).closest("a");
    if (!anchor) {
      return;
    }
    const href = anchor.getAttribute("href") ?? "";
    if (href.startsWith("#")) {
      event.preventDefault();
      document.getElementById(decodeURIComponent(href.slice(1)))?.scrollIntoView({ block: "start" });
      return;
    }
    if (href.startsWith("/projects/")) {
      event.preventDefault();
      navigate(href);
    }
  };

  return (
    <>
      <div className="doc-body" ref={body} onClick={onClick} />
      {slots.map(({ id, node }) => {
        const found = diagramsById.get(id);
        if (!found) {
          return null;
        }
        return createPortal(
          <DiagramView
            diagram={found.diagram}
            index={found.index}
            overCountLimit={found.index >= DIAGRAM_LIMITS.maxPerDocument}
          />,
          node,
          id,
        );
      })}
    </>
  );
}
