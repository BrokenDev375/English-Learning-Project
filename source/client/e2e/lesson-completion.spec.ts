import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import type { Snapshot } from '../src/features/learning/types';

async function begin(page: Page) {
  const email = randomUUID() + '@completion.test', password = 'CompletionTest@12345';
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: { email, password, fullName: 'Học viên hoàn thành bài' } }); expect(response.status()).toBe(201);
  await page.goto('/#/learning?unit=1&lesson=1'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10);
  const id = Number(new URL(page.url()).hash.match(/attempt=(\d+)/)![1]); const snapshot: Snapshot = await (await page.request.get('/api/attempts/' + id)).json(); return snapshot;
}
async function answerAll(page: Page, snapshot: Snapshot) {
  for (const [i, item] of snapshot.items.entries()) {
    await page.locator('.question-nav button').nth(i).click(); const fields = page.locator('.answer-fields');
    if (item.question.type === 'SINGLE_CHOICE') await fields.getByRole('radio').first().check();
    else if (item.question.type === 'FILL_BLANK') for (const input of await fields.getByRole('textbox').all()) await input.fill('demo');
    else if (item.question.type === 'MATCHING') for (let n = 0; n < item.question.content.leftItems!.length; n++) { await fields.locator('.matching-left button').nth(n).click(); await fields.locator('.matching-right button').nth(n).click(); }
    else for (const token of item.question.content.tokens!) await fields.locator('.word-bank').getByRole('button', { name: token.text, exact: true }).click();
  }
}
async function submit(page: Page) {
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); await expect(page.locator('.submission-review-items > li')).toHaveCount(10);
}
test('ten complete answers need no guided checks; review all keys and recover a lost completion ACK', async ({ page }) => {
  test.setTimeout(90000); await page.setViewportSize({ width: 390, height: 844 }); const s = await begin(page); let checks = 0;
  page.on('request', request => { if (request.url().endsWith('/check')) checks++; });
  await answerAll(page, s); await submit(page); expect(checks).toBe(0); await expect(page.getByRole('heading', { name: 'Đã hoàn thành bài học', exact: true })).toBeVisible();
  const original = await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json(); expect(original.lessonCompleted).toBe(true); expect(original.items.every((i: { finalResponseComplete: boolean }) => i.finalResponseComplete)).toBe(true);
  const rounds = await (await page.request.get('/api/me/rounds')).json(); expect(rounds.items.find((r: { unit: { id: number } }) => r.unit.id === 1).completedLessons).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  const confirm = page.getByRole('button', { name: 'Xác nhận hoàn thành bài học', exact: true }); await confirm.scrollIntoViewIfNeeded(); await page.screenshot({ path: 'test-results/completion-review-mobile.png' });
  let calls = 0, body: unknown;
  await page.route('**/api/attempts/*/complete', async route => { calls++; const payload = route.request().postDataJSON(); if (calls === 1) { body = payload; const reply = await route.fetch(); expect(reply.status()).toBe(200); return route.abort('failed'); } if (calls === 2) expect(payload).toEqual(body); return route.continue(); });
  await confirm.click(); await expect(page.getByRole('alert')).toContainText('Chưa kết nối'); await page.reload();
  await expect.poll(() => calls).toBe(2); await expect(page.locator('.learning-status')).toContainText('Đã nộp bài trên máy chủ');
  expect(await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json()).toEqual(original);
  await page.getByRole('button', { name: 'Xác nhận hoàn thành bài học', exact: true }).click(); await expect(page.locator('.round-overview')).toContainText('Đã hoàn thành 1/2 bài');
});
test('a partial submission shows every solution but cannot confirm completion', async ({ page }) => {
  const s = await begin(page); await page.locator('.answer-fields').getByRole('radio').first().check(); await submit(page);
  await expect(page.getByRole('button', { name: 'Xác nhận hoàn thành bài học', exact: true })).toBeDisabled(); await expect(page.locator('.submission-review-complete')).toContainText('chưa đầy đủ');
  const result = await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json(); expect(result.lessonCompleted).toBe(false);
});
test('an older full result without a completion flag can confirm without repeating its questions', async ({ page }) => {
  const s = await begin(page); await answerAll(page, s); await submit(page);
  await page.route('**/api/attempts/*/result', async route => { const response = await route.fetch(); const result = await response.json(); return route.fulfill({ response, json: { ...result, lessonCompleted: false } }); });
  await page.reload(); await expect(page.getByRole('heading', { name: 'Đã trả lời đủ câu hỏi', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xác nhận hoàn thành bài học', exact: true }).click(); await expect(page.locator('.round-overview')).toContainText('Đã hoàn thành 1/2 bài');
});
