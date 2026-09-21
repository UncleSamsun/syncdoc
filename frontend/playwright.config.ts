import { existsSync } from "node:fs";
import { defineConfig } from "@playwright/test";

/**
 * TASK-008 실제 흐름 검증(E2E).
 *
 * 대상은 이미 떠 있는 구성이다. 여기서 서버를 띄우지 않는다. compose로 올린 웹(기본
 * `http://localhost:8081`)을 그대로 보며, 검증 대상과 검증 도구를 같은 명령이 만들지 않게 한다.
 *
 * 로그인은 GitHub OAuth라 자동화하지 않는다. `npm run e2e:login`으로 사람이 한 번 로그인해
 * 세션을 파일로 남기고, 그 파일이 있을 때만 로그인 이후 흐름을 돌린다. 파일이 없으면 해당
 * 시험은 건너뛴 것으로 보고한다. 통과로 적지 않는다.
 */
export const SESSION_STATE = "tests/.auth/session.json";

export default defineConfig({
  testDir: "tests",
  testMatch: "**/*.spec.ts",
  // 수집 한 번이 10초 안팎이라 기본 30초로는 모자란 시험이 있다.
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: process.env.SYNCDOC_E2E_BASE_URL ?? "http://localhost:8081",
    locale: "ko-KR",
    storageState: existsSync(SESSION_STATE) ? SESSION_STATE : undefined,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
