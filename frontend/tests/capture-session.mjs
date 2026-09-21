// E2E가 쓸 로그인 세션을 사람이 직접 만들어 파일로 남긴다.
//
// GitHub 로그인은 자동화하지 않는다. 비밀번호와 2단계 인증을 스크립트가 다루게 두지 않는다.
// 순서가 둘로 나뉘어 있는 이유가 있다. 앱에서 바로 `GitHub로 계속`을 누르면 GitHub가
// 로그인 화면으로 보냈다가 원래 주소로 되돌리는데, 새 브라우저에서는 그 되돌리기가 404로
// 끝나는 일이 있었다(2026-09-21 확인). 그래서 GitHub 로그인을 먼저 끝낸 다음 인증을 시작한다.
//
//   npm run e2e:login
//
// 저장하는 것은 이 서비스의 쿠키뿐이다. GitHub 쿠키는 파일에 넣지 않는다.
// 세션 수명은 12시간이다. 만료되면 다시 실행한다. 저장 파일은 형상관리에 넣지 않는다.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { chromium } from "@playwright/test";

const base = process.env.SYNCDOC_E2E_BASE_URL ?? "http://localhost:8081";
const out = process.env.SYNCDOC_E2E_STATE ?? "tests/.auth/session.json";
const waitMs = 10 * 60 * 1000;

/** 이름이 맞는 쿠키가 생길 때까지 기다린다. */
async function waitForCookie(context, page, name, host, what) {
  console.log(what);
  const until = Date.now() + waitMs;
  while (Date.now() < until) {
    const cookies = await context.cookies();
    if (cookies.some((cookie) => cookie.name === name && cookie.value
        && cookie.domain.includes(host))) {
      return true;
    }
    await page.waitForTimeout(1000);
  }
  return false;
}

const browser = await chromium.launch({ headless: false });
const context = await browser.newContext({ locale: "ko-KR" });
const page = await context.newPage();

// 1단계. GitHub에 먼저 로그인한다.
await page.goto("https://github.com/login");
const signedInToGitHub = await waitForCookie(context, page, "user_session", "github.com",
    `열린 창에서 GitHub에 로그인하세요. ${waitMs / 60000}분 안에 끝내면 됩니다.`);
if (!signedInToGitHub) {
  console.error("GitHub 로그인을 확인하지 못했습니다. 저장하지 않습니다.");
  await browser.close();
  process.exit(1);
}

// 2단계. 그 상태에서 서비스 인증을 시작한다. 승인 화면이 나오면 사람이 누른다.
console.log("GitHub 로그인을 확인했습니다. 이제 서비스 인증을 시작합니다.");
await page.goto(`${base}/api/v1/auth/github/start`);
const signedIn = await waitForCookie(context, page, "SYNCDOC_SESSION", "localhost",
    "승인 화면이 나오면 계속을 누르세요.");
if (!signedIn) {
  console.error(`세션 쿠키를 찾지 못했습니다. 저장하지 않습니다. 마지막 주소: ${page.url()}`);
  await browser.close();
  process.exit(1);
}

// 서비스 쿠키만 남긴다. GitHub 쿠키를 파일에 쓰지 않는다.
const cookies = (await context.cookies())
    .filter((cookie) => cookie.domain.includes("localhost") || cookie.domain.includes("127.0.0.1"));
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({ cookies, origins: [] }, null, 2), "utf-8");
console.log(`세션을 ${out}에 저장했습니다. 쿠키 ${cookies.length}개.`);
await browser.close();
