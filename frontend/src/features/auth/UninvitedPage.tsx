/** UI-006. 프로젝트의 존재 여부를 알려주지 않고, 로그아웃 외의 이동 수단을 두지 않는다. */
export default function UninvitedPage() {
  return (
    <main className="centered">
      <h1>이 계정은 아직 초대되지 않았습니다</h1>
      <p>
        GitHub 로그인은 확인되었지만 서비스 이용이 허용되지 않았습니다. 관리자에게 GitHub 계정 등록을
        요청하세요.
      </p>
      <a className="button" href="/login">
        로그아웃
      </a>
    </main>
  );
}
