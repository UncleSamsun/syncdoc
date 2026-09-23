type Props = { returnTo?: string; error?: string };

/** UI-005. 로그인 외 공개 진입점을 두지 않는다. */
export default function LoginPage({ returnTo, error }: Props) {
  const href = returnTo
    ? `/api/v1/auth/github/start?returnTo=${encodeURIComponent(returnTo)}`
    : "/api/v1/auth/github/start";

  return (
    <main className="centered">
      <h1>SyncDoc</h1>
      <p>초대받은 GitHub 계정으로 로그인하세요.</p>
      {error === "state" && <p role="alert">로그인을 다시 시도해 주세요.</p>}
      <a className="button" href={href}>
        GitHub로 계속
      </a>
    </main>
  );
}
