import { useEffect, useState } from "react";
import { apiGet } from "../shared/api/client";

type Health = { status: string };

export default function App() {
  const [health, setHealth] = useState<"unknown" | "up" | "down">("unknown");

  useEffect(() => {
    let cancelled = false;
    apiGet<Health>("/health/live")
      .then(() => !cancelled && setHealth("up"))
      .catch(() => !cancelled && setHealth("down"));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>SyncDoc</h1>
      {health === "up" ? <p>서버에 연결되었습니다.</p> : <p>서버에 연결되지 않았습니다.</p>}
    </main>
  );
}
