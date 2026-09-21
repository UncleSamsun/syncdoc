// E2E가 쓸 로그인 세션을 사람이 직접 만들어 파일로 남긴다.
//
// GitHub 로그인은 자동화하지 않는다. 비밀번호와 2단계 인증을 스크립트가 다루게 두지 않는다.
// 창이 열리면 평소처럼 로그인하고, 로그인이 끝나면 이 스크립트가 쿠키를 저장하고 닫는다.
//
//   npm run e2e:login
//
// 세션 수명은 12시간이다. 만료되면 다시 실행한다. 저장 파일은 형상관리에 넣지 않는다.
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { chromium } from "@playwright/test";

const base = process.env.SYNCDOC_E2E_BASE_URL ?? "http://localhost:8081";
const out = process.env.SYNCDOC_E2E_STATE ?? "tests/.auth/session.json";
const waitMs = 10 * 60 * 1000;

const browser = await chromium.launch({ headless: false });
const context = await browser.newContext({ locale: "ko-KR" });
const page = await context.newPage();
await page.goto(base);

console.log(`열린 창에서 로그인하세요. ${waitMs / 60000}분 안에 끝내면 됩니다.`);

const until = Date.now() + waitMs;
let signedIn = false;
while (Date.now() < until) {
  const cookies = await context.cookies();
  if (cookies.some((cookie) => cookie.name === "SYNCDOC_SESSION" && cookie.value)) {
    signedIn = true;
    break;
  }
  await page.waitForTimeout(1000);
}

if (!signedIn) {
  console.error("세션 쿠키를 찾지 못했습니다. 저장하지 않습니다.");
  await browser.close();
  process.exit(1);
}

mkdirSync(dirname(out), { recursive: true });
await context.storageState({ path: out });
console.log(`세션을 ${out}에 저장했습니다.`);
await browser.close();
