import { write } from './round-fixtures';
import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { Item, Snapshot, Result } from '../src/features/learning/types';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
const local = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const password = 'LearningTest@12345';
async function login(page: Page, email: string, pass = password) {
  await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(pass);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
}
async function student(page: Page) {
  const email = 'm03-' + Date.now() + '-' + Math.random().toString(36).slice(2) + '@test.local';
  const token = await (await page.request.get('/api/auth/csrf')).json();
  const r = await page.request.post('/api/auth/register', { headers: { [token.headerName]: token.token }, data: { email, password, fullName: 'Học viên M03' } }); expect(r.status()).toBe(201);
  await page.goto('/#/learning'); await login(page, email); return email;
}
async function start(page: Page) {
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click();
  await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click();
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10);
  return snapshot(page);
}
async function snapshot(page: Page): Promise<Snapshot> { const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1]; const r = await page.request.get('/api/attempts/' + id); expect(r.status()).toBe(200); return r.json(); }
async function select(page: Page, n: number) { await page.getByRole('navigation', { name: 'Các câu trong lượt làm', exact: true }).getByRole('button').nth(n).click(); }
async function fill(page: Page, item: Item) {
  const fields = page.locator('.answer-fields');
  if (item.question.type === 'SINGLE_CHOICE') await fields.getByRole('radio').first().check();
  if (item.question.type === 'FILL_BLANK') for (const input of await fields.getByRole('textbox').all()) await input.fill('demo');
  if (item.question.type === 'MATCHING') for (let i = 0; i < item.question.content.leftItems!.length; i++) { await fields.locator('.matching-left button').nth(i).click(); await fields.locator('.matching-right button').nth(i).click(); }
}
async function settled(page: Page) { await expect(page.locator('.learning-status')).not.toContainText('Đang gửi'); }
async function save(page: Page) { await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ'); }
async function submit(page: Page) { await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); }
async function seekAudio(page: Page, seconds: number) {
  const audio = page.locator('audio');
  await expect.poll(() => audio.evaluate((p: HTMLAudioElement) => p.readyState)).toBeGreaterThanOrEqual(1);
  await audio.evaluate(async (p: HTMLAudioElement, target) => {
    p.pause();
    if (Math.abs(p.currentTime - target) < 0.005) return;
    await new Promise<void>(resolve => { p.addEventListener('seeked', () => resolve(), { once: true }); p.currentTime = target; });
  }, seconds);
}

for (const keepLocal of [false, true]) test('two-tab audio ' + (keepLocal ? 'keeps the selected local position' : 'restores the selected server position'), async ({ page }) => {
  await student(page); const s = await start(page);
  const index = s.items.findIndex(i => s.materials.some(m => m.kind === 'AUDIO' && m.materialVersionId === i.materialVersionId));
  expect(index).toBeGreaterThanOrEqual(0);
  const materialId = s.items[index].materialVersionId;
  await select(page, index); await seekAudio(page, 1); await save(page);
  const tab = await page.context().newPage();
  try {
    await tab.goto(page.url()); await expect(tab.locator('.question-nav button')).toHaveCount(10);
    await expect.poll(() => tab.locator('audio').evaluate((p: HTMLAudioElement) => p.currentTime)).toBeCloseTo(1, 1);
    await seekAudio(tab, 2);
    // Keep a dirty local position before the remote invalidation arrives.
    await seekAudio(page, 7); await save(page);
    await expect(tab.getByRole('heading', { name: 'Đối chiếu bài làm', exact: true })).toBeVisible();
    tab.once('dialog', dialog => dialog.accept());
    await tab.getByRole('button', { name: keepLocal ? 'Giữ bản đang nhập sau đối chiếu' : 'Dùng bản máy chủ', exact: true }).click();
    await expect.poll(() => tab.locator('audio').evaluate((p: HTMLAudioElement) => p.currentTime)).toBeCloseTo(keepLocal ? 2 : 7, 1);
    await expect.poll(() => tab.locator('audio').evaluate((p: HTMLAudioElement) => p.seeking)).toBe(false);
    if (keepLocal) await save(tab);
    else await expect(tab.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true })).toBeDisabled();
    expect((await snapshot(tab)).playerState.audioPositions.find(p => p.materialVersionId === materialId)?.seconds).toBeCloseTo(keepLocal ? 2 : 7, 1);
  } finally { await tab.close(); }
});

test('a delayed save ACK preserves a newer local audio position', async ({ page }) => {
  await student(page); const s = await start(page);
  const index = s.items.findIndex(i => s.materials.some(m => m.kind === 'AUDIO' && m.materialVersionId === i.materialVersionId));
  expect(index).toBeGreaterThanOrEqual(0);
  const materialId = s.items[index].materialVersionId;
  await select(page, index); await seekAudio(page, 1); await save(page); await seekAudio(page, 2);
  let release!: () => void, committed!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const reached = new Promise<void>(resolve => { committed = resolve; });
  await page.route('**/api/attempts/*/draft', async route => {
    const response = await route.fetch(); expect(response.status()).toBe(200); committed(); await gate;
    return route.fulfill({ response });
  }, { times: 1 });
  try {
    await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await reached;
    await seekAudio(page, 4);
  } finally { release(); }
  await settled(page);
  await expect.poll(() => page.locator('audio').evaluate((p: HTMLAudioElement) => p.currentTime)).toBeCloseTo(4, 1);
  await expect(page.locator('.learning-status')).toContainText('Chưa lưu lên máy chủ');
  expect((await snapshot(page)).playerState.audioPositions.find(p => p.materialVersionId === materialId)?.seconds).toBeCloseTo(2, 1);
  await save(page);
  expect((await snapshot(page)).playerState.audioPositions.find(p => p.materialVersionId === materialId)?.seconds).toBeCloseTo(4, 1);
});

test('LEARN renders all answer types, saves/restores, completes, corrects and updates catalog progress', async ({ page }) => {
  test.setTimeout(90000); await student(page); const s = await start(page); const url = page.url();
  expect(s.items.map(i => i.question.type)).toEqual(expect.arrayContaining(['SINGLE_CHOICE', 'FILL_BLANK', 'MATCHING']));
  expect(JSON.stringify(s)).not.toMatch(/answerKey|transcript|explanation/);
  for (let i = 0; i < s.items.length; i++) {
    await select(page, i); await fill(page, s.items[i]);
    if (s.items[i].practiceRole === 'GUIDED') {
      if (i === 0) { await page.getByRole('button', { name: 'Mở gợi ý', exact: true }).click(); await expect(page.getByText('Gợi ý:', { exact: true })).toBeVisible(); await settled(page); }
      const check = page.waitForResponse(r => r.url().endsWith('/check') && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true }).click(); expect(JSON.stringify(await (await check).json())).not.toContain('transcript'); await settled(page);
    } else await expect(page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true })).toHaveCount(0);
  }
  await save(page); const restored = await snapshot(page); expect(restored.items.every(i => i.response)).toBe(true);
  await page.reload(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  await page.getByRole('button', { name: 'Về danh sách lượt', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Lịch sử làm bài.' })).toBeVisible();
  await page.goBack(); await expect(page.locator('.question-nav button')).toHaveCount(10); expect(page.url()).toBe(url);
  await page.goForward(); await expect(page.getByRole('heading', { name: 'Lịch sử làm bài.' })).toBeVisible();
  await page.goBack(); await settled(page); await submit(page);
  await expect(page.getByRole('heading', { name: 'Đã hoàn thành bài học', exact: true })).toBeVisible();
  const before: Result = await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json();
  await page.getByRole('button', { name: 'Chữa câu 1', exact: true }).click(); await page.locator('.answer-fields').getByRole('radio').last().check();
  await page.getByRole('button', { name: 'Kiểm tra bài chữa', exact: true }).click(); await expect(page.getByRole('status').filter({ hasText: 'Bài chữa:' })).toBeVisible();
  expect(await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json()).toEqual(before);
  await page.screenshot({ path: 'test-results/module-3-result-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/module-3-result-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Về danh sách lượt', exact: true }).click(); await page.getByRole('tab', { name: 'Chơi theo vòng', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click(); await expect(page.getByText('Đã hoàn thành bài học', { exact: true })).toBeVisible();
});
test('legacy PRACTICE work stays accessible by history and direct URL after the menu is removed', async ({ page }) => {
  await student(page);
  const legacy = await write(page.request, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'PRACTICE', topicId: 1, difficulty: 'EASY', count: 3 });
  await page.goto('/#/learning?tab=practice&attempt=' + legacy.attemptId); await expect(page.locator('.question-nav button')).toHaveCount(3);
  const s = await snapshot(page); expect(s.mode).toBe('PRACTICE'); await expect(page.getByRole('button', { name: 'Mở gợi ý', exact: true })).toHaveCount(0);
  await submit(page); await expect(page.locator('.result-score')).toContainText('3 câu chưa trả lời');
  await page.reload(); await expect(page.locator('.result-score')).toContainText('0.00'); expect((await snapshot(page)).attemptId).toBe(s.attemptId);
});
test('lost draft ACK replays the original payload and cannot overwrite newer local answers', async ({ page }) => {
  await student(page); const s = await start(page); await page.locator('.answer-fields').getByRole('radio').first().check();
  let original: unknown; let calls = 0;
  await page.route('**/api/attempts/*/draft', async route => {
    calls++; if (calls === 1) { original = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(200); return route.abort('failed'); }
    if (calls === 2) expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible();
  await page.locator('.answer-fields').getByRole('radio').last().check(); await expect(page.getByRole('button', { name: 'Nộp bài', exact: true })).toBeDisabled();
  // Leaving and returning in the SPA keeps the unresolved command and local edits.
  await page.getByRole('button', { name: 'Về danh sách lượt', exact: true }).click(); await page.getByRole('button', { name: 'Tiếp tục làm #' + s.attemptId, exact: true }).click();
  // M06 replays the durable command when the runner is reopened.
  await expect.poll(() => calls).toBe(2); await settled(page);
  await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked(); await expect(page.locator('.learning-status')).toContainText('Chưa lưu lên máy chủ');
  await save(page); expect((await snapshot(page)).revision).toBe(2); expect(calls).toBe(3);
});
test('two tabs require explicit comparison before replacing the winning draft', async ({ page }) => {
  await student(page); const s = await start(page); const tab = await page.context().newPage(); await tab.goto(page.url()); await expect(tab.locator('.question-nav button')).toHaveCount(10);
  await page.locator('.answer-fields').getByRole('radio').first().check(); await tab.locator('.answer-fields').getByRole('radio').last().check(); await save(page);
  // Socket invalidation detects the same conflict before a second save is attempted.
  await expect(tab.getByRole('heading', { name: 'Đối chiếu bài làm', exact: true })).toBeVisible();
  await expect(tab.locator('.answer-fields').getByRole('radio').last()).toBeChecked(); expect((await snapshot(page)).revision).toBe(1);
  tab.once('dialog', dialog => dialog.accept()); await tab.getByRole('button', { name: 'Giữ bản đang nhập sau đối chiếu', exact: true }).click(); await save(tab);
  const latest = await snapshot(tab); expect(latest.attemptId).toBe(s.attemptId); expect(latest.revision).toBe(2); await tab.close();
});
test('submit committed without its response replays the same key and result', async ({ page }) => {
  await student(page); const s = await start(page); let original: unknown; let submission = 0; let calls = 0;
  await page.route('**/api/attempts/*/submit', async route => {
    calls++; if (calls === 1) { original = route.request().postDataJSON(); const committed = await route.fetch(); expect(committed.status()).toBe(200); submission = (await committed.json()).submissionId; return route.abort('failed'); }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await submit(page); await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible(); await expect(page.locator('.answer-fields').getByRole('radio').first()).toBeDisabled();
  await page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true }).click(); await expect(page.locator('.result-score')).toBeVisible();
  expect((await (await page.request.get('/api/attempts/' + s.attemptId + '/result')).json()).submissionId).toBe(submission); expect(calls).toBe(2);
});
test('401 keeps draft and command across login; 503 keeps the next command pending', async ({ page }) => {
  const email = await student(page); await start(page); await page.locator('.answer-fields').getByRole('radio').last().check(); let original: unknown; let calls = 0;
  await page.route('**/api/attempts/*/draft', async route => {
    calls++;
    if (calls === 1) { original = route.request().postDataJSON(); return route.fulfill({ status: 401, json: { code: 'SESSION_EXPIRED', message: 'Phiên đã hết hạn' } }); }
    if (calls === 2) expect(route.request().postDataJSON()).toEqual(original);
    if (calls === 3) { original = route.request().postDataJSON(); return route.fulfill({ status: 503, json: { code: 'DATABASE_UNAVAILABLE', message: 'Chưa lưu được' } }); }
    if (calls === 4) expect(route.request().postDataJSON()).toEqual(original);
    return route.continue();
  });
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await page.getByRole('button', { name: 'Đăng nhập lại', exact: true }).click(); await login(page, email);
  await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  await page.locator('.answer-fields').getByRole('radio').first().check(); await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Chưa lưu được'); await expect(page.locator('.learning-status')).not.toContainText('Đã lưu trên máy chủ');
  await page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ'); expect(calls).toBe(4);
});
test('real audio restores absolute position and clamps to the supplied segment on mobile', async ({ page }) => {
  await student(page); const s = await start(page); const index = s.items.findIndex(i => s.materials.some(m => m.kind === 'AUDIO' && m.materialVersionId === i.materialVersionId)); expect(index).toBeGreaterThanOrEqual(0);
  // Override only segment metadata to cover nonzero bounds, using the real seeded audio and backend draft storage.
  await page.route('**/api/attempts/' + s.attemptId, async route => { const r = await route.fetch(); const body = await r.json() as Snapshot; body.materials.filter(m => m.kind === 'AUDIO').forEach(m => { m.startSeconds = 1; m.endSeconds = 3; }); return route.fulfill({ response: r, json: body }); });
  await page.reload(); await expect(page.locator('.question-nav button')).toHaveCount(10); await select(page, index);
  const audio = page.locator('audio'); await expect.poll(() => audio.evaluate((p: HTMLAudioElement) => p.readyState)).toBeGreaterThanOrEqual(1);
  await expect.poll(() => audio.evaluate((p: HTMLAudioElement) => p.currentTime)).toBeCloseTo(1, 1);
  await audio.evaluate((p: HTMLAudioElement) => { p.currentTime = 2; }); await expect(page.locator('.learning-status')).toContainText('Chưa lưu lên máy chủ'); await save(page);
  await page.reload(); await expect.poll(() => audio.evaluate((p: HTMLAudioElement) => p.currentTime)).toBeCloseTo(2, 1);
  await audio.evaluate((p: HTMLAudioElement) => { p.currentTime = 5; }); await expect.poll(() => audio.evaluate((p: HTMLAudioElement) => p.currentTime)).toBeLessThanOrEqual(3.01);
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); await page.screenshot({ path: 'test-results/module-3-runner-mobile.png', fullPage: true });
});
test('lost create response survives sidebar navigation and replays the exact command', async ({ page }) => {
  await student(page); let original: unknown; let calls = 0;
  await page.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue(); calls++;
    if (calls === 1) { original = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(201); return route.abort('failed'); }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click(); await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click(); await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tài khoản', exact: true }).click(); await page.getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10); expect(calls).toBe(2);
  expect((await (await page.request.get('/api/attempts')).json()).totalElements).toBe(1);
});
test('logout discards private memory; another student cannot load the previous attempt', async ({ page }) => {
  await student(page); await start(page); const url = page.url();
  const radio = page.locator('.answer-fields').getByRole('radio').first(); await radio.focus(); await page.keyboard.press('ArrowDown'); await expect(page.locator('.answer-fields').getByRole('radio').nth(1)).toBeChecked();
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await student(page); const denied = page.waitForResponse(r => /\/api\/attempts\/\d+$/.test(r.url())); await page.goto(url); expect((await denied).status()).toBe(404); await expect(page.getByRole('alert')).toBeVisible(); await expect(page.locator('.answer-fields')).toHaveCount(0);
});
test('actual 15-second timeout retains the request key and blocks later mutations', async ({ page }) => {
  test.setTimeout(60000); await student(page); await start(page); await page.locator('.answer-fields').getByRole('radio').first().check(); let original: unknown; let calls = 0;
  await page.route('**/api/attempts/*/draft', async route => {
    calls++;
    if (calls === 1) {
      original = route.request().postDataJSON(); const committed = await route.fetch(); expect(committed.status()).toBe(200);
      await new Promise(resolve => setTimeout(resolve, 17000)); await route.fulfill({ response: committed }).catch(() => {}); return;
    }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible({ timeout: 20000 });
  await expect(page.getByRole('button', { name: 'Nộp bài', exact: true })).toBeDisabled(); await page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ'); expect(calls).toBe(2);
});
test('long prompts and blank values stay within the mobile viewport and input contract', async ({ page }) => {
  await student(page); const s = await start(page);
  await page.route('**/api/attempts/' + s.attemptId, async route => {
    const response = await route.fetch(); const body = await response.json() as Snapshot;
    body.items[1].question.prompt = 'Long prompt ' + 'Vocabulary'.repeat(35);
    body.items[1].question.content.parts!.unshift({ kind: 'TEXT', text: 'Sentence'.repeat(60) });
    return route.fulfill({ response, json: body });
  });
  await page.reload(); await expect(page.locator('.question-nav button')).toHaveCount(10); await select(page, 1); await page.setViewportSize({ width: 390, height: 844 });
  const input = page.locator('.answer-fields').getByRole('textbox').first(); await input.fill('x'.repeat(250)); await expect(input).toHaveValue('x'.repeat(200)); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('lesson introduction deduplicates description and distinguishes completed, active and latest submitted attempts', async ({ page }) => {
  test.setTimeout(90000); await student(page); const first = await start(page);
  const introUrl = new URL(page.url()); const [path, query] = introUrl.hash.split('?'); const params = new URLSearchParams(query); params.delete('attempt'); introUrl.hash = path + '?' + params;
  for (let i = 0; i < first.items.length; i++) {
    await select(page, i); await fill(page, first.items[i]);
    if (first.items[i].practiceRole === 'GUIDED') { await page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true }).click(); await settled(page); }
  }
  await submit(page); await expect(page.getByRole('heading', { name: 'Đã hoàn thành bài học', exact: true })).toBeVisible();
  await page.goto(introUrl.toString()); await expect(page.getByRole('button', { name: 'Chơi lại bài', exact: true })).toBeEnabled();
  const intro = await (await page.request.get('/api/lessons/' + first.lesson!.lesson.id)).json();
  await expect(page.getByText(intro.lesson.description, { exact: true })).toHaveCount(1); await expect(page.locator('.lesson-facts')).toContainText('10 câu');
  const actions = page.locator('.lesson-intro-actions'); const firstBox = await actions.getByRole('button').first().boundingBox(), lastBox = await actions.getByRole('button').last().boundingBox();
  expect(Math.abs(firstBox!.y - lastBox!.y)).toBeLessThan(2); await page.screenshot({ path: 'test-results/module-3-intro-completed-desktop.png', fullPage: true });
  await page.getByRole('button', { name: 'Chơi lại bài', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10); const second = await snapshot(page); expect(second.attemptId).not.toBe(first.attemptId);
  await page.goto(introUrl.toString()); await expect(page.getByRole('button', { name: 'Tiếp tục làm', exact: true })).toBeEnabled(); await expect(page.getByText('Đã hoàn thành bài học', { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); await page.screenshot({ path: 'test-results/module-3-intro-active-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Xem kết quả gần nhất', exact: true }).click(); await expect(page.locator('.result-score')).toBeVisible(); expect((await snapshot(page)).attemptId).toBe(first.attemptId);
  await page.goto(introUrl.toString()); await page.getByRole('button', { name: 'Tiếp tục làm', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10); expect((await snapshot(page)).attemptId).toBe(second.attemptId);
  await submit(page); await expect(page.getByRole('heading', { name: 'Bài học chưa hoàn thành', exact: true })).toBeVisible();
  // Correcting the older submission changes updatedAt, but the more recently submitted result must stay selected.
  const token = await (await page.request.get('/api/auth/csrf')).json(); const old = await (await page.request.get('/api/attempts/' + first.attemptId)).json() as Snapshot;
  const corrected = await page.request.post('/api/attempts/' + first.attemptId + '/items/' + first.items[0].attemptQuestionId + '/retry', { headers: { [token.headerName]: token.token }, data: { requestId: crypto.randomUUID(), baseRevision: old.revision, response: { type: 'SINGLE_CHOICE', optionId: first.items[0].question.content.options![0].id } } }); expect(corrected.status()).toBe(200);
  await page.goto(introUrl.toString()); await page.getByRole('button', { name: 'Chơi lại bài', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10);
  await page.goto(introUrl.toString()); await expect(page.getByRole('button', { name: 'Tiếp tục làm', exact: true })).toBeEnabled(); await page.getByRole('button', { name: 'Xem kết quả gần nhất', exact: true }).click(); await expect(page.locator('.result-score')).toBeVisible(); expect((await snapshot(page)).attemptId).toBe(second.attemptId);
});

test('teacher can browse introductions without student actions or attempt/progress requests', async ({ page }) => {
  let privateRequests = 0; page.on('request', r => { if (/\/api\/(attempts|me\/learning-progress)/.test(r.url())) privateRequests++; });
  await page.goto('/#/learning'); await login(page, local.DEMO_TEACHER_EMAIL ?? 'teacher@english.local', local.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345');
  await page.getByRole('button', { name: 'Xem bài trong Sinh hoạt hằng ngày — Bắt đầu →', exact: true }).click(); await page.getByRole('button', { name: /Xem giới thiệu A day with Linh/ }).click();
  await expect(page.getByRole('button', { name: 'Bắt đầu chơi', exact: true })).toHaveCount(0); await expect(page.getByRole('tab', { name: 'Luyện tự chọn', exact: true })).toHaveCount(0); expect(privateRequests).toBe(0);
});

test('admin can browse without real attempts or learning progress calls', async ({ page }) => {
  let calls = 0; page.on('request', r => { if (/\/api\/(attempts|me\/learning-progress)/.test(r.url())) calls++; });
  await page.goto('/#/learning'); await login(page, local.BOOTSTRAP_ADMIN_EMAIL, local.BOOTSTRAP_ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Xem bài trong Sinh hoạt hằng ngày — Bắt đầu →', exact: true }).click(); await page.getByRole('button', { name: /Xem giới thiệu A day with Linh/ }).click();
  await expect(page.getByRole('button', { name: 'Bắt đầu chơi', exact: true })).toHaveCount(0); expect(calls).toBe(0);
});
