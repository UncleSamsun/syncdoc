import {existsSync,readFileSync} from 'node:fs';
import {expect,test} from '@playwright/test';
import {SESSION_STATE} from '../playwright.config.ts';

/** Arrange data is created explicitly at the final company-server verification stage. */
const fixturePath='tests/.auth/harness-fixture.json';
type Fixture={projectId:string;from:string;to:string;taskId:string;requirementId:string;uiId:string;apiId:string;mainSnapshot:string};
const fixture:Fixture|null=existsSync(fixturePath)?JSON.parse(readFileSync(fixturePath,'utf8')):null;

test('integrated relations, design impact and copied task context stay snapshot-pinned',async({page,context,request})=>{
 test.skip(!fixture||!existsSync(SESSION_STATE),'Final integrated server fixture/session is absent; not a pass.');
 if(!fixture)return;
 const base=`/projects/${fixture.projectId}`,api=`/api/v1${base}`;
 const pair=`fromSnapshotId=${fixture.from}&toSnapshotId=${fixture.to}`;
 const relationResponse=await request.get(`${api}/spec-relations?snapshotId=${fixture.to}`);expect(relationResponse.status()).toBe(200);
 const relations=await relationResponse.json();
 const row=relations.requirements.find((r:{requirement:{itemId:string}})=>r.requirement.itemId===fixture.requirementId);expect(row).toBeTruthy();
 expect(row.designs.map((d:{itemId:string})=>d.itemId)).toEqual(expect.arrayContaining([fixture.uiId,fixture.apiId]));
 expect(row.tasks.map((t:{item:{itemId:string}})=>t.item.itemId)).toContain(fixture.taskId);
 const impactResponse=await request.get(`${api}/snapshot-comparison/design-impacts?${pair}`);expect(impactResponse.status()).toBe(200);
 const impacts=await impactResponse.json();expect(impacts.items.map((i:{itemId:string})=>i.itemId)).toEqual(expect.arrayContaining([fixture.uiId,fixture.apiId]));
 await page.goto(`${base}/comparison?${pair}`);await page.getByRole('button',{name:'설계 재검토',exact:true}).click();
 const links=page.getByRole('region',{name:'게시본 비교 표'}).getByRole('link',{name:new RegExp(fixture.uiId)});await expect(links).toHaveCount(2);
 expect(await links.first().getAttribute('href')).toContain(`snapshotId=${fixture.from}`);expect(await links.last().getAttribute('href')).toContain(`snapshotId=${fixture.to}`);
 const contextResponse=await request.get(`${api}/tasks/${fixture.taskId}/context?snapshotId=${fixture.to}`);expect(contextResponse.status()).toBe(200);
 const bundle=await contextResponse.json();expect(bundle.task.item.itemId).toBe(fixture.taskId);expect(bundle.snapshotId).toBe(fixture.to);expect(bundle.rules).toHaveLength(10);expect(bundle.rules.every((r:{available:boolean;sourceHash:string|null})=>r.available&&r.sourceHash?.length===64)).toBe(true);
 await context.grantPermissions(['clipboard-read','clipboard-write']);
 await page.goto(`${base}/tasks/${fixture.taskId}/context?snapshotId=${fixture.to}`);
 await expect(page.getByRole('textbox',{name:'컨텍스트 Markdown'})).toHaveValue(bundle.markdown);
 await page.getByRole('button',{name:'컨텍스트 복사',exact:true}).click();await expect(page.getByText('복사했습니다.',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>navigator.clipboard.readText())).toBe(bundle.markdown);
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth)).toBe(true);
});
