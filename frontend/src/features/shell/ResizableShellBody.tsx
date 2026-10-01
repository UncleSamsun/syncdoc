import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

const STORAGE_KEY = "syncdoc:sidebar-width";
const DEFAULT_WIDTH = 248;
const MIN_WIDTH = 200;
const MAX_WIDTH = 560;
const MIN_CONTENT = 320;

function savedWidth() {
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY) ?? DEFAULT_WIDTH);
    return Number.isFinite(value) ? Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, value)) : DEFAULT_WIDTH;
  } catch { return DEFAULT_WIDTH; }
}
function saveWidth(width: number) {
  try { localStorage.setItem(STORAGE_KEY, String(width)); } catch { /* Resizing still works without storage. */ }
}

/** Desktop splitter. Mobile keeps the existing stacked layout. */
export default function ResizableShellBody({ sidebar, children }: { sidebar: ReactNode; children: ReactNode }) {
  const [preferredWidth, setPreferredWidth] = useState(savedWidth);
  const [containerWidth, setContainerWidth] = useState(() => window.innerWidth);
  const [resizing, setResizing] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const latestWidth = useRef(preferredWidth);
  const drag = useRef<{ pointerId: number; x: number; width: number } | null>(null);
  const maximum = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, containerWidth - MIN_CONTENT));
  const width = Math.min(preferredWidth, maximum);

  useEffect(() => {
    const measure = () => setContainerWidth(container.current?.getBoundingClientRect().width || window.innerWidth);
    const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : null;
    if (container.current) observer?.observe(container.current);
    window.addEventListener("resize", measure);
    return () => { observer?.disconnect(); window.removeEventListener("resize", measure); };
  }, []);

  const update = (next: number, persist = true) => {
    const value = Math.round(Math.max(MIN_WIDTH, Math.min(maximum, next)));
    latestWidth.current = value;
    setPreferredWidth(value);
    if (persist) saveWidth(value);
  };
  const finish = () => {
    if (!drag.current) return;
    drag.current = null;
    setResizing(false);
    saveWidth(latestWidth.current);
  };

  return <div ref={container} className={`shell-body${resizing ? " shell-body--resizing" : ""}`}
    style={{ "--sidebar-width": `${width}px` } as CSSProperties}>
    {sidebar}
    <div className="sidebar-resizer" role="separator" tabIndex={0}
      aria-label="사이드바 너비 조절" aria-orientation="vertical" aria-controls="project-sidebar"
      aria-valuemin={MIN_WIDTH} aria-valuemax={maximum} aria-valuenow={width}
      aria-valuetext={`${width}px`} title="드래그하여 너비 조절 · 더블클릭하여 기본 너비"
      onPointerDown={event => {
        if (event.button !== 0 || window.innerWidth <= 720) return;
        event.preventDefault();
        event.currentTarget.focus();
        drag.current = { pointerId: event.pointerId, x: event.clientX, width };
        latestWidth.current = width;
        setResizing(true);
        event.currentTarget.setPointerCapture?.(event.pointerId);
      }}
      onPointerMove={event => {
        if (drag.current?.pointerId === event.pointerId)
          update(drag.current.width + event.clientX - drag.current.x, false);
      }}
      onPointerUp={finish} onPointerCancel={finish} onLostPointerCapture={finish}
      onDoubleClick={() => update(DEFAULT_WIDTH)}
      onKeyDown={event => {
        const step = event.shiftKey ? 32 : 16;
        const next = { ArrowLeft: width - step, ArrowRight: width + step, Home: MIN_WIDTH, End: maximum }[event.key];
        if (next === undefined) return;
        event.preventDefault(); update(next);
      }}/>
    {children}
  </div>;
}
