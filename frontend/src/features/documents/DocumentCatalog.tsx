import { useState } from "react";
import { Link } from "react-router-dom";
import type { ChecklistType } from "../spec/types";
import type { DocumentItem } from "./types";

type Props = { projectId: string; snapshotId?: string; documents: DocumentItem[]; types: ChecklistType[]; currentDocumentId?: string };

/** Names/order come from the repository's format, never from guessed folder names. */
export default function DocumentCatalog({ projectId, snapshotId, documents, types, currentDocumentId }: Props) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const known = new Set(types.map((type) => type.type));
  const groups = types.map((type) => ({ key: `type:${type.type}`, name: type.name,
    documents: documents.filter((document) => document.kind === type.type) }));
  groups.push({ key: "unclassified", name: "분류 없음",
    documents: documents.filter((document) => !document.kind || !known.has(document.kind)) });
  const toggle = (key: string) => setCollapsed((previous) => {
    const next = new Set(previous);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });
  return <div className="catalog tree">
    {groups.filter((group) => group.documents.length > 0).map((group) => {
      const open = group.documents.some((document) => document.id === currentDocumentId) || !collapsed.has(group.key);
      return <section className="fold" key={group.key}>
        <button className="fold-h" type="button" aria-expanded={open} onClick={() => toggle(group.key)}>
          <span className="chev" aria-hidden="true">{open ? "▾" : "▸"}</span>
          <span className="fname" title={group.name}>{group.name}</span>
          <span className="n">{group.documents.length}</span>
        </button>
        {open && <div className="fold-b">{group.documents.map((document) =>
          <Link key={document.id} to={`/projects/${projectId}/documents/${document.id}${snapshotId ? `?snapshotId=${encodeURIComponent(snapshotId)}` : ""}`}
            title={document.path} aria-current={document.id === currentDocumentId ? "page" : undefined}>
            {document.title}
          </Link>)}
        </div>}
      </section>;
    })}
  </div>;
}
