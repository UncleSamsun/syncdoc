/** API-017의 목록 항목. */
export type DocumentItem = {
  id: string;
  path: string;
  title: string;
  kind: string | null;
};

export type DocumentList = {
  /** 첫 수집 전에는 null이다. 빈 목록과 "아직 없음"은 다른 상태다. */
  snapshotId: string | null;
  items: DocumentItem[];
  nextCursor: string | null;
};

export type DocumentHeading = {
  level: number;
  id: string;
  text: string;
};

/** 원문 그대로의 다이어그램. 화면은 이 값을 제한된 Mermaid에만 넘긴다. */
export type DocumentDiagram = {
  id: string;
  syntax: string;
  source: string;
};

export type DocumentLink = {
  kind: "document" | "asset" | "external" | "anchor" | "missing";
  href: string;
  text: string;
};

export type DocumentWarning = {
  code: string;
  detail: string;
};

/** API-018의 본문. `html`은 서버가 정화한 결과이며 화면에서 원문을 다시 해석하지 않는다. */
export type DocumentView = {
  id: string;
  snapshotId: string;
  sourceRevision: string;
  path: string;
  title: string;
  specId: string | null;
  kind: string | null;
  html: string;
  headings: DocumentHeading[];
  diagrams: DocumentDiagram[];
  links: DocumentLink[];
  warnings: DocumentWarning[];
};

/** 문서 경로로 만든 트리. 폴더를 화면에서 임의로 묶거나 펼쳐 한 줄로 만들지 않는다. */
export type TreeFolder = {
  path: string;
  name: string;
  folders: TreeFolder[];
  documents: DocumentItem[];
  /** 이 폴더와 아래 폴더에 든 문서 수. */
  count: number;
};

export function buildTree(items: DocumentItem[]): TreeFolder {
  const root: TreeFolder = { path: "", name: "", folders: [], documents: [], count: 0 };

  for (const item of items) {
    const segments = item.path.split("/");
    const fileName = segments.pop();
    if (!fileName) {
      continue;
    }
    let folder = root;
    let walked = "";
    for (const segment of segments) {
      walked = walked ? `${walked}/${segment}` : segment;
      let next = folder.folders.find((candidate) => candidate.name === segment);
      if (!next) {
        next = { path: walked, name: segment, folders: [], documents: [], count: 0 };
        folder.folders.push(next);
      }
      folder = next;
    }
    folder.documents.push(item);
  }

  countDocuments(root);
  return root;
}

function countDocuments(folder: TreeFolder): number {
  folder.count =
    folder.documents.length +
    folder.folders.reduce((total, child) => total + countDocuments(child), 0);
  return folder.count;
}

/** 문서가 든 폴더 경로를 모두 돌려준다. 열려 있는 문서의 폴더는 항상 펼쳐야 한다. */
export function foldersOf(path: string): string[] {
  const segments = path.split("/");
  segments.pop();
  const folders: string[] = [];
  let walked = "";
  for (const segment of segments) {
    walked = walked ? `${walked}/${segment}` : segment;
    folders.push(walked);
  }
  return folders;
}
