import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { DocumentItem, TreeFolder } from "./types";
import { buildTree, foldersOf } from "./types";

type Props = {
  projectId: string;
  items: DocumentItem[];
  currentDocumentId?: string;
  currentPath?: string;
};

const STORAGE_PREFIX = "syncdoc.tree.collapsed.";

/** 접은 폴더만 저장한다. 저장된 값이 없거나 읽을 수 없으면 모두 펼친 상태로 시작한다. */
function loadCollapsed(projectId: string): Set<string> {
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + projectId);
    return new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set<string>();
  }
}

function saveCollapsed(projectId: string, collapsed: Set<string>) {
  try {
    window.localStorage.setItem(STORAGE_PREFIX + projectId, JSON.stringify([...collapsed]));
  } catch {
    // 저장할 수 없는 브라우저에서도 화면은 그대로 동작해야 한다.
  }
}

/**
 * UI-003의 문서 트리. 저장소의 폴더 구조를 그대로 보여준다.
 *
 * <p>폴더를 화면에서 임의로 묶거나 펼쳐서 한 줄로 만들지 않는다. 열고 있는 문서가 든 폴더는
 * 접혀 있어도 항상 펼친다 — 링크를 따라 들어왔을 때 어디에 있는지 보이지 않으면 길을 잃는다.
 */
export default function DocumentTree({ projectId, items, currentDocumentId, currentPath }: Props) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => loadCollapsed(projectId));

  useEffect(() => {
    setCollapsed(loadCollapsed(projectId));
  }, [projectId]);

  const toggle = useCallback(
    (path: string) => {
      setCollapsed((previous) => {
        const next = new Set(previous);
        if (next.has(path)) {
          next.delete(path);
        } else {
          next.add(path);
        }
        saveCollapsed(projectId, next);
        return next;
      });
    },
    [projectId],
  );

  const tree = buildTree(items);
  const openPath = new Set(currentPath ? foldersOf(currentPath) : []);

  return (
    <div className="tree">
      <Folder
        folder={tree}
        depth={0}
        collapsed={collapsed}
        openPath={openPath}
        onToggle={toggle}
        projectId={projectId}
        currentDocumentId={currentDocumentId}
      />
    </div>
  );
}

type FolderProps = {
  folder: TreeFolder;
  depth: number;
  collapsed: Set<string>;
  openPath: Set<string>;
  onToggle: (path: string) => void;
  projectId: string;
  currentDocumentId?: string;
};

function Folder({
  folder,
  depth,
  collapsed,
  openPath,
  onToggle,
  projectId,
  currentDocumentId,
}: FolderProps) {
  const children = (
    <>
      {folder.folders.map((child) => (
        <Folder
          key={child.path}
          folder={child}
          depth={depth + 1}
          collapsed={collapsed}
          openPath={openPath}
          onToggle={onToggle}
          projectId={projectId}
          currentDocumentId={currentDocumentId}
        />
      ))}
      {folder.documents.map((document) => (
        <Link
          key={document.id}
          to={`/projects/${projectId}/documents/${document.id}`}
          aria-current={document.id === currentDocumentId ? "page" : undefined}
        >
          {document.title}
        </Link>
      ))}
    </>
  );

  if (depth === 0) {
    return <div className="fold-b">{children}</div>;
  }

  // 열고 있는 문서가 든 폴더는 접힘 상태와 상관없이 펼친다.
  const open = openPath.has(folder.path) || !collapsed.has(folder.path);

  return (
    <div className={`fold${openPath.has(folder.path) ? " fold--cur" : ""}`}>
      <button
        type="button"
        className="fold-h"
        aria-expanded={open}
        onClick={() => onToggle(folder.path)}
      >
        <span className="chev" aria-hidden="true">
          {open ? "▾" : "▸"}
        </span>
        <span className="fname">{folder.name}</span>
        <span className="n">{folder.count}</span>
      </button>
      {open && <div className="fold-b">{children}</div>}
    </div>
  );
}
