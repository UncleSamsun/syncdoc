import { expect, test, type Page } from "@playwright/test";

/**
 * TASK-008 실제 흐름 검증. 연결 → 수집 → 문서·표·다이어그램 → 검색 → 현황 갱신을 한 벌로 돌린다.
 *
 * 떠 있는 구성을 그대로 본다(기본 `http://localhost:8081`). 고정 데이터를 넣지 않고 실제로
 * 연결된 프로젝트를 읽어 대상을 고른다. 표나 다이어그램이 있는 문서도 목록에서 찾아 쓴다.
 * 그래서 이 시험은 "이 저장소에는 표가 있다"는 가정 없이 돈다.
 *
 * 로그인은 자동화하지 않는다. `npm run e2e:login`이 만든 세션이 없으면 로그인 이후 시험은
 * 건너뛴다. 건너뛴 것을 통과로 적지 않는다.
 */

const API = "/api/v1";

type DocumentItem = { id: string; path: string; title: string };

test.describe("세션 없이 들어온 경우", () => {
  // 세션 파일이 있어도 이 묶음은 비운 상태로 본다. 공개 진입점이 없다는 것이 검증 대상이다.
  test.use({ storageState: { cookies: [], origins: [] } });

  test("어느 경로로 들어와도 로그인 화면이다 (UI-005)", async ({ page }) => {
    const paths = [
      "/",
      "/projects/00000000-0000-0000-0000-000000000000",
      "/projects/00000000-0000-0000-0000-000000000000/search",
    ];
    for (const path of paths) {
      await page.goto(path);
      await expect(page.getByRole("link", { name: "GitHub로 계속" })).toBeVisible();
      // 경로만 바꿔 들어와도 프로젝트 이름 같은 내용이 새어 나오지 않는다.
      await expect(page.locator("main")).not.toContainText("현황");
    }
  });

  test("초대되지 않은 계정 화면에는 로그아웃 말고 다른 길이 없다 (UI-006)", async ({ page }) => {
    await page.goto("/uninvited");
    await expect(page.getByRole("heading", { name: /초대되지 않았습니다/ })).toBeVisible();
    const links = page.locator("main a");
    await expect(links).toHaveCount(1);
    await expect(links).toHaveText("로그아웃");
  });

  test("API는 세션 없이 401이고 본문에 내부 정보를 담지 않는다", async ({ request }) => {
    const response = await request.get(`${API}/projects`);
    expect(response.status()).toBe(401);
    expect(await response.text()).not.toMatch(/ghs_|github_pat_|jdbc:|Exception|at io\.github/);
  });
});

test.describe("로그인한 사용자의 한 흐름", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading").first()).toBeVisible();
    const signedOut = await page.getByRole("link", { name: "GitHub로 계속" }).isVisible();
    test.skip(signedOut, "로그인 세션이 없다. `npm run e2e:login`으로 만든 뒤 다시 실행한다.");
  });

  test("프로젝트 홈에서 연결한 저장소를 연다 (UI-001)", async ({ page }) => {
    await expect(page.getByRole("heading", { name: "프로젝트 홈" })).toBeVisible();
    const card = page.locator(".pcard").first();
    await expect(card).toBeVisible();
    // 저장소 이름·브랜치·문서 경로가 카드에 함께 있어야 무엇을 여는지 알 수 있다.
    await expect(card.locator(".nm b")).not.toBeEmpty();

    await card.getByRole("link", { name: "열기" }).click();
    await expect(page.getByRole("heading", { name: "현황" })).toBeVisible();
    await expect(page.locator("header.sh-top .sw").first()).not.toBeEmpty();
  });

  test("현황은 분모 없는 숫자를 두지 않는다 (UI-002)", async ({ page }) => {
    await open(page);
    const done = page.locator(".card", { hasText: "작업 완료" }).first();
    await expect(done).toBeVisible();
    // `12`처럼 기준 없는 숫자는 두지 않는다. 분모가 있거나, 없다고 적혀 있어야 한다.
    await expect(done.locator(".v")).toHaveText(/\d+\s*\/\s*\d+|계산 대상 없음/);
    await expect(done.locator(".n")).toContainText("기준");
  });

  test("작업 표가 집계와 같은 수를 보여준다 (UI-002)", async ({ page }) => {
    const projectId = await open(page);
    const overview = await (await page.request.get(`${API}/projects/${projectId}/overview`)).json();
    const total = overview.taskTotal as number;

    // 건수를 시험이 정해 두지 않는다. 보고 있는 브랜치에 작업 목록이 없을 수도 있다.
    // 화면과 집계가 어긋나지 않는지가 검사 대상이다.
    const rows = page.locator("table.tasks tbody tr");
    await expect(page.locator("section.panel", { hasText: "작업 · Issue" })).toBeVisible();
    expect(await rows.count()).toBe(total);
    if (total > 0) {
      await expect(rows.first()).toContainText(/TASK-\d{3}/);
    }
  });

  test("문서 본문과 표가 화면에 나온다 (UI-003)", async ({ page }) => {
    const projectId = await open(page);
    const found = await findDocuments(page, projectId);
    test.skip(!found.withTable, "지금 보고 있는 게시본에 표가 든 문서가 없다");

    await page.goto(`/projects/${projectId}/documents/${found.withTable}`);
    await expect(page.locator(".doc table").first()).toBeVisible();
    // 목차는 본문에서 뽑은 제목이다. 본문이 비면 목차도 비어 이 확인이 걸린다.
    await expect(page.locator(".toc a").first()).toBeVisible();
    await expectNoSideScroll(page);
  });

  test("다이어그램이 브라우저에서 그려진다 (UI-003)", async ({ page }) => {
    const projectId = await open(page);
    const found = await findDocuments(page, projectId);
    test.skip(!found.withDiagram, "지금 보고 있는 게시본에 다이어그램이 든 문서가 없다");

    await page.goto(`/projects/${projectId}/documents/${found.withDiagram}`);
    const diagram = page.locator("[aria-label^='다이어그램']").first();
    await expect(diagram).toBeVisible();
    // 그려진 결과를 본다. 자리만 잡히고 실패 문구가 남는 경우를 통과로 보지 않는다.
    await expect(diagram.locator("svg")).toBeVisible({ timeout: 30_000 });
  });

  test("검색 결과에서 문서의 그 자리로 간다 (UI-004)", async ({ page }) => {
    const projectId = await open(page);
    await page.goto(`/projects/${projectId}/search`);

    await page.getByLabel("검색어").fill("수집");
    await page.getByRole("button", { name: "찾기" }).click();

    const hits = page.locator(".hit");
    await expect(hits.first()).toBeVisible();
    const anchored = hits.filter({ has: page.locator("a[href*='#']") }).first();
    const target = (await anchored.count()) > 0 ? anchored : hits.first();
    const href = await target.locator("a.t").getAttribute("href");

    await target.locator("a.t").click();
    await expect(page.locator(".doc h1")).toBeVisible();
    await expectNoSideScroll(page);
    expect(decodeURIComponent(page.url())).toContain(decodeURIComponent(href ?? ""));
    if (href?.includes("#")) {
      // 검색 결과가 준 자리가 화면에 실제로 있어야 한다. 예전에는 본문 첫 제목을 뺄 때 id까지
      // 지워서, 이 링크를 눌러도 아무 데도 가지 않았다.
      const anchor = href.slice(href.indexOf("#") + 1);
      // 앵커에 한글과 점이 섞여 있어 `#id` 선택자로는 못 쓴다. 속성으로 고른다.
      const target = page.locator(`[id="${anchor.replace(/"/g, '\\"')}"]`);
      await expect(target).toHaveCount(1);
      // 제목만 빼고 남긴 앵커는 넓이·높이가 0이라 보이는지로는 판단할 수 없다. 자리로 본다.
      const box = await target.boundingBox();
      const viewport = page.viewportSize();
      expect(box, "앵커가 화면에 없다").not.toBeNull();
      expect(box!.y, "앵커가 보이는 자리에 없다").toBeGreaterThanOrEqual(-2);
      expect(box!.y).toBeLessThan(viewport!.height);
    }
  });

  test("게시본의 모든 문서가 열리고 가로로 밀리지 않는다 (UI-003)", async ({ page }) => {
    const projectId = await open(page);
    const list = await (await page.request.get(`${API}/projects/${projectId}/documents`)).json();
    const items = list.items as DocumentItem[];
    expect(items.length, "수집된 문서가 없다").toBeGreaterThan(0);

    // 문서 하나가 브라우저를 죽이면 그 문서를 연 사람은 화면을 잃는다. 원문은 우리가 고르지
    // 않으므로 게시본에 든 것을 전부 열어 본다.
    const broken: string[] = [];
    for (const item of items) {
      try {
        await page.goto(`/projects/${projectId}/documents/${item.id}`, { waitUntil: "load" });
        await expect(page.locator(".doc h1")).toBeVisible();
        const overflow = await page.evaluate(
            () => document.body.scrollWidth - window.innerWidth);
        if (overflow > 1) {
          broken.push(`${item.path}: 가로로 ${overflow}px 밀린다`);
        }
      } catch (error) {
        broken.push(`${item.path}: 열지 못했다 (${String(error).slice(0, 60)})`);
      }
    }
    expect(broken, broken.join(" / ")).toHaveLength(0);
  });

  test("지금 동기화를 누르면 수집이 돌고 현황이 갱신된다 (API-013·API-014)", async ({ page }) => {
    const projectId = await open(page);
    await page.getByRole("button", { name: "지금 동기화" }).click();

    await expect
      .poll(
        async () => {
          const response = await page.request.get(`${API}/projects/${projectId}/sync`);
          return (await response.json()).state as string;
        },
        { timeout: 90_000, intervals: [1_000] },
      )
      .toBe("succeeded");

    await page.reload();
    // 수집이 끝나자마자 주기 조회가 다음 작업을 잡아 둘 수 있다. 그래서 `최신`으로 못 박지 않고,
    // 정해진 상태 문구 중 하나와 마지막 성공 시각이 함께 보이는지를 본다. 시각 없는 상태나
    // 뜻 모를 문구가 나오면 걸린다.
    await expect(page.locator(".syncpill"))
        .toContainText(/(최신|수집 중|갱신 대기|갱신 실패).*\d{4}-\d{2}-\d{2} \d{2}:\d{2}/);
  });
});

/** 페이지 본문이 가로로 밀리지 않는지 본다. 공통 UI 규칙이 정한 것이다. */
async function expectNoSideScroll(page: Page) {
  const overflow = await page.evaluate(
      () => document.body.scrollWidth - window.innerWidth);
  expect(overflow, "페이지가 가로로 밀린다").toBeLessThanOrEqual(1);
}

/** 첫 프로젝트를 열고 그 id를 준다. 어떤 저장소가 연결되어 있든 같은 흐름으로 돈다. */
async function open(page: Page): Promise<string> {
  await page.goto("/");
  const openLink = page.locator(".pcard").first().getByRole("link", { name: "열기" });
  await expect(openLink).toBeVisible();
  const href = await openLink.getAttribute("href");
  const projectId = (href ?? "").split("/").pop() ?? "";
  expect(projectId).not.toBe("");
  await openLink.click();
  await expect(page.getByRole("heading", { name: "현황" })).toBeVisible();
  return projectId;
}

/** 표와 다이어그램이 든 문서를 실제 수집 결과에서 고른다. */
async function findDocuments(page: Page, projectId: string) {
  const list = await page.request.get(`${API}/projects/${projectId}/documents`);
  expect(list.ok()).toBeTruthy();
  const items = ((await list.json()).items ?? []) as DocumentItem[];
  expect(items.length, "수집된 문서가 없다").toBeGreaterThan(0);

  let withTable: string | undefined;
  let withDiagram: string | undefined;
  for (const item of items) {
    if (withTable && withDiagram) {
      break;
    }
    const view = await page.request.get(`${API}/projects/${projectId}/documents/${item.id}`);
    if (!view.ok()) {
      continue;
    }
    const body = await view.json();
    if (!withTable && typeof body.html === "string" && body.html.includes("<table")) {
      withTable = item.id;
    }
    if (!withDiagram && Array.isArray(body.diagrams) && body.diagrams.length > 0) {
      withDiagram = item.id;
    }
  }
  return { withTable, withDiagram };
}
