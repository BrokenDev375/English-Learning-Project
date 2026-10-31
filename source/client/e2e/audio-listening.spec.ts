import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { Snapshot } from '../src/features/learning/types';
import { resolve } from 'node:path';

async function open(page: Page) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json(), email = `audio-${crypto.randomUUID()}@test.local`, password = 'AudioTest@12345';
  expect((await page.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: { email, password, fullName: 'Kiểm tra nghe' } })).status()).toBe(201);
  await page.goto('/#/learning'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click(); await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click(); await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10);
  const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1];
  const snapshot: Snapshot = await (await page.request.get('/api/attempts/' + id)).json();
  const index = snapshot.items.findIndex(i => snapshot.materials.some(m => m.kind === 'AUDIO' && m.materialVersionId === i.materialVersionId));
  await page.locator('.question-nav button').nth(index).click();
  await expect(page.getByRole('button', { name: 'Nghe lần 1/2', exact: true })).toBeEnabled();
  return { snapshot, index, path: `/api/attempts/${id}/audio/${snapshot.items[index].materialVersionId}` };
}
const player = (page: Page) => page.locator('.restricted-audio audio');
const position = (page: Page) => player(page).evaluate((p: HTMLAudioElement) => p.currentTime);

test('learner audio has no seek controls and rejects forward/backward seeking', async ({ page }) => {
  const { path } = await open(page);
  expect(await player(page).evaluate((p: HTMLAudioElement) => p.controls)).toBe(false);
  await expect(page.locator('.restricted-audio input[type=range]')).toHaveCount(0);
  await player(page).evaluate((p: HTMLAudioElement) => { p.currentTime = 7; }); await expect.poll(() => position(page)).toBeLessThan(.1);
  await page.getByRole('button', { name: 'Nghe lần 1/2', exact: true }).click(); await expect.poll(() => position(page)).toBeGreaterThan(.4);
  await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click(); const paused = await position(page);
  for (const target of [0, paused + 5]) { await player(page).evaluate((p: HTMLAudioElement, t) => { p.currentTime = t; }, target); await expect.poll(() => position(page)).toBeCloseTo(paused, 1); }
  expect((await (await page.request.get(path)).json()).playsUsed).toBe(1);
});

test('pause/resume and changing questions sharing audio retain the same listen', async ({ page }) => {
  const { snapshot, index, path } = await open(page); let starts = 0;
  page.on('request', r => { if (r.url().endsWith('/start') && r.url().includes('/audio/')) starts++; });
  await page.getByRole('button', { name: 'Nghe lần 1/2', exact: true }).click(); await expect.poll(() => position(page)).toBeGreaterThan(.5);
  await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click(); const paused = await position(page);
  const shared = snapshot.items.findIndex((i, n) => n !== index && i.materialVersionId === snapshot.items[index].materialVersionId);
  expect(shared).toBeGreaterThanOrEqual(0); await page.locator('.question-nav button').nth(shared).click();
  await expect(page.getByRole('button', { name: 'Nghe tiếp', exact: true })).toBeEnabled();
  const other = snapshot.items.findIndex(i => i.materialVersionId !== snapshot.items[index].materialVersionId);
  await page.locator('.question-nav button').nth(other).click(); await page.locator('.question-nav button').nth(index).click();
  await expect(page.getByRole('button', { name: 'Nghe tiếp', exact: true })).toBeEnabled(); await expect.poll(() => position(page)).toBeCloseTo(paused, 1);
  await page.getByRole('button', { name: 'Nghe tiếp', exact: true }).click(); await expect.poll(() => position(page)).toBeGreaterThan(paused + .2);
  expect(starts).toBe(1); expect((await (await page.request.get(path)).json()).playsUsed).toBe(1);
});

test('lost start ACK replays one reservation; reload and another tab cannot reset two listens', async ({ page, context }) => {
  const { path } = await open(page); let body: unknown, calls = 0;
  await page.route('**' + path + '/start', async route => {
    calls++; if (calls === 1) { body = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(200); await route.abort('failed'); }
    else { expect(route.request().postDataJSON()).toEqual(body); await route.continue(); }
  });
  await page.getByRole('button', { name: 'Nghe lần 1/2', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận lại lượt nghe', exact: true }).click(); await expect(page.getByRole('button', { name: 'Tạm dừng', exact: true })).toBeVisible();
  expect(calls).toBe(2); expect((await (await page.request.get(path)).json()).playsUsed).toBe(1);
  await page.unroute('**' + path + '/start'); await page.reload();
  await page.getByRole('button', { name: 'Nghe lần 2/2', exact: true }).click(); await expect(page.getByRole('button', { name: 'Tạm dừng', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('button', { name: 'Đã hết lượt nghe', exact: true })).toBeDisabled();
  const tab = await context.newPage(); await tab.goto(page.url()); await expect(tab.getByRole('button', { name: 'Đã hết lượt nghe', exact: true })).toBeDisabled(); await tab.close();
});

test('two completed segment listens exhaust the quota on mobile and result review', async ({ page }) => {
  const { index, path } = await open(page);
  await page.route('**/api/attempts/*', async route => {
    if (route.request().method() !== 'GET' || !/\/api\/attempts\/\d+$/.test(new URL(route.request().url()).pathname)) return route.continue();
    const response = await route.fetch(), data = await response.json();
    data.materials.filter((m: { kind: string }) => m.kind === 'AUDIO').forEach((m: { startSeconds: number; endSeconds: number }) => { m.startSeconds = 1; m.endSeconds = 2; });
    await route.fulfill({ response, json: data });
  });
  await page.reload(); await page.setViewportSize({ width: 390, height: 844 });
  for (const n of [1, 2]) {
    await page.getByRole('button', { name: `Nghe lần ${n}/2`, exact: true }).click();
    if (n === 1) await expect(page.getByRole('button', { name: 'Nghe lần 2/2', exact: true })).toBeEnabled();
  }
  await expect(page.getByRole('button', { name: 'Đã hết lượt nghe', exact: true })).toBeDisabled();
  expect((await (await page.request.get(path)).json()).remaining).toBe(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: resolve('../../.test-state/audio-limit-mobile.png'), fullPage: true });
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click();
  await expect(page.locator('.result-score')).toBeVisible(); await page.getByRole('button', { name: 'Chữa câu ' + (index + 1), exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đã hết lượt nghe', exact: true })).toBeDisabled();
});

test('retrying a failed audio file retains the already granted listen', async ({ page }) => {
  const { snapshot, index, path } = await open(page);
  await page.getByRole('button', { name: 'Nghe lần 1/2', exact: true }).click(); await expect.poll(() => position(page)).toBeGreaterThan(.2);
  await page.getByRole('button', { name: 'Tạm dừng', exact: true }).click();
  const material = snapshot.materials.find(m => m.materialVersionId === snapshot.items[index].materialVersionId)!;
  let failed = true;
  await page.route('**' + material.asset!.url, route => failed ? route.fulfill({ status: 503, body: 'test media unavailable' }) : route.continue());
  await player(page).evaluate((p: HTMLAudioElement) => p.load()); await expect(page.locator('.restricted-audio').getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Thử tải lại', exact: true }).click();
  await expect(page.locator('.restricted-audio').getByRole('alert')).toBeVisible();
  failed = false; await page.getByRole('button', { name: 'Thử tải lại', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Nghe tiếp', exact: true })).toBeEnabled(); await page.getByRole('button', { name: 'Nghe tiếp', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Tạm dừng', exact: true })).toBeVisible(); expect((await (await page.request.get(path)).json()).playsUsed).toBe(1);
});
