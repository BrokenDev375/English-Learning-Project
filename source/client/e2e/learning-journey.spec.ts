import { fixtureWrite } from './governance-fixtures';
import { completeEarlierRounds } from './round-fixtures';
import { test, expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Snapshot } from '../src/features/learning/types';

const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const stamp = 'journey-' + Date.now(), pass = 'JourneyTest@12345', author = stamp + '@test.local';
let unitId: number, lessonId: number;
const resources: { kind: string; id: number }[] = [];
async function write(request: APIRequestContext, path: string, data: unknown, method = 'POST') {
  const result = await fixtureWrite(request, path, data, method);
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
test.beforeAll(async ({ request }) => {
  await write(request, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  const user = await write(request, '/api/auth/register', { email: author, password: pass, fullName: 'Giáo viên vòng học' });
  await write(request, '/api/admin/users/' + user.id + '/role', { role: 'TEACHER' }, 'PATCH');
  await write(request, '/api/auth/login', { email: author, password: pass });
  const topic = await write(request, '/api/author/topics', { requestId: randomUUID(), code: stamp, name: stamp, description: '' });
  const objective = await write(request, '/api/author/objectives', { requestId: randomUUID(), code: stamp, topicId: topic.id, name: 'Sắp xếp câu và ghép từ', description: '' });
  const unit = await write(request, '/api/author/units', { requestId: randomUUID(), topicId: topic.id, title: 'Vòng tương tác ' + stamp, description: 'Ghép thẻ và tạo câu tiếng Anh.', stage: 99, sortOrder: 0 }); unitId = unit.id;
  const questions = [];
  for (let i = 0; i < 4; i++) {
    const matching = i === 2;
    const q = await write(request, '/api/author/questions', {
      requestId: randomUUID(), type: matching ? 'MATCHING' : 'ORDERING', topicId: topic.id, objectiveId: objective.id, difficulty: 'EASY',
      prompt: matching ? 'Ghép từ với nghĩa' : 'Sắp xếp thành câu ' + (i + 1), explanation: 'She likes music.', hint: 'Bắt đầu bằng chủ ngữ.', materialVersionId: null,
      source: { label: 'Học liệu kiểm thử tự soạn', url: null, license: null },
      content: matching ? { leftItems: [{ id: 'l1', text: 'music' }, { id: 'l2', text: 'school' }], rightItems: [{ id: 'r1', text: 'âm nhạc' }, { id: 'r2', text: 'trường học' }] } : { tokens: [{ id: 'w1', text: 'She' }, { id: 'w2', text: 'likes' }, { id: 'w3', text: 'music' }] },
      answerKey: matching ? { pairs: [{ leftId: 'l1', rightId: 'r1' }, { leftId: 'l2', rightId: 'r2' }] } : { acceptedOrders: [['w1', 'w2', 'w3']] },
    });
    questions.push(q); resources.push({ kind: 'questions', id: q.id });
  }
  const lesson = await write(request, '/api/author/lessons', { requestId: randomUUID(), unitId, unitBaseRevision: unit.revision, title: 'Tạo câu và ghép từ ' + stamp, description: 'Luyện tập tương tác.', instructions: [{ kind: 'TEXT', text: 'Chọn thẻ từ và kiểm tra câu có hướng dẫn trước khi nộp.' }], objectiveIds: [objective.id], questions: questions.map((q, i) => ({ questionVersionId: q.draftVersionId, practiceRole: i === 0 ? 'GUIDED' : i === 3 ? 'REVIEW_VARIANT' : 'INDEPENDENT', position: i === 2 ? 1 : 0 })) });
  lessonId = lesson.id; resources.unshift({ kind: 'lessons', id: lessonId });
  const preview = await (await request.get('/api/author/lessons/' + lessonId + '/preview')).json(); expect(preview.errors).toEqual([]);
  await write(request, '/api/author/lessons/' + lessonId + '/publish', { requestId: randomUUID(), draftVersionId: preview.draftVersionId, baseRevision: preview.baseRevision, previewToken: preview.previewToken });
});
test.afterAll(async ({ request }) => {
  if (!resources.length) return;
  await write(request, '/api/auth/login', { email: author, password: pass });
  for (const { kind, id } of resources) {
    const root = await (await request.get('/api/author/' + kind + '/' + id)).json();
    if (root.status === 'PUBLISHED') await write(request, '/api/author/' + kind + '/' + id + '/retire', { requestId: randomUUID(), baseRevision: root.rootRevision });
  }
});
async function student(page: Page, path = '/') {
  const email = randomUUID() + '@test.local';
  await write(page.request, '/api/auth/register', { email, password: pass, fullName: 'Học viên hành trình' });
  await page.goto(path); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(pass); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible(); await completeEarlierRounds(page.request, unitId); await page.reload();
}
async function snapshot(page: Page): Promise<Snapshot> { const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1]; return (await page.request.get('/api/attempts/' + id)).json(); }
async function start(page: Page) {
  await student(page, '/#/learning?unit=' + unitId); await expect(page.locator('.round-overview')).toContainText('Đã hoàn thành 0/1 bài');
  await page.getByRole('button', { name: 'Chơi bài tiếp theo →', exact: true }).click(); await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(3); return snapshot(page);
}
async function chooseOrdering(page: Page, words = ['She', 'likes', 'music']) {
  for (const word of words) await page.locator('.word-bank').getByRole('button', { name: word, exact: true }).click();
}

test('home recommends a real next lesson, retries failed reads and fits mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await student(page);
  await expect(page.getByRole('button', { name: /^Vào chơi vòng / })).toBeVisible();
  await expect(page.locator('.journey-totals')).toContainText('Bài đã hoàn thành');
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/journey-home-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.screenshot({ path: 'test-results/journey-home-desktop.png', fullPage: true });
  let fail = true; await page.route('**/api/me/statistics?period=ALL', route => fail ? route.abort('failed') : route.continue());
  await page.getByRole('button', { name: 'Cập nhật trang chủ', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Chưa kết nối');
  fail = false; await page.getByRole('button', { name: 'Thử lại', exact: true }).click(); await expect(page.getByRole('alert')).toHaveCount(0);
  await page.getByRole('button', { name: /^Vào chơi vòng / }).click(); await expect(page.locator('.round-overview')).toBeVisible();
});

test('ordering draft survives reload and home resume; submit completes the round without leaking keys', async ({ page }) => {
  test.setTimeout(90000); let s = await start(page); expect(JSON.stringify(s)).not.toContain('acceptedOrders');
  await expect(page.locator('.word-bridge .bridge-scene')).toHaveAttribute('data-open', 'false');
  await chooseOrdering(page, ['She', 'likes']);
  await page.getByRole('button', { name: 'Chuyển từ 2 sang trái', exact: true }).click(); await expect(page.locator('.ordered-words .word-token')).toHaveText(['likes', 'She']);
  await page.getByRole('button', { name: 'Chuyển từ 1 sang phải', exact: true }).click();
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  const initialOrder = s.items[0].question.content.tokens!.map(t => t.id); await page.reload(); await expect(page.locator('.ordered-words > li')).toHaveCount(2);
  s = await snapshot(page); expect(s.items[0].question.content.tokens!.map(t => t.id)).toEqual(initialOrder); expect(s.items[0].response).toEqual({ type: 'ORDERING', tokenIds: ['w1', 'w2'] });
  await page.getByRole('button', { name: 'Trang chủ', exact: true }).click();
  const resume = page.getByRole('button', { name: 'Chơi tiếp bài đang làm →', exact: true }); await expect(resume).toBeEnabled();
  // Hold a background reward refresh between pointerdown/up: it must not swallow the navigation click.
  let release!: () => void, started!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; }), reached = new Promise<void>(resolve => { started = resolve; });
  await page.route('**/api/me/statistics?period=ALL', async route => { started(); await gate; return route.continue(); });
  await resume.evaluate(button => button.addEventListener('pointerdown', () => window.dispatchEvent(new Event('gamification:changed')), { once: true }));
  await resume.scrollIntoViewIfNeeded(); const box = await resume.boundingBox(); expect(box).not.toBeNull();
  try {
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2); await page.mouse.down(); await reached; await page.mouse.up();
    await expect(page).toHaveURL(new RegExp('attempt=' + s.attemptId + '(?:&|$)'));
  } finally { release(); }
  await expect(page.locator('.ordered-words > li')).toHaveCount(2);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.word-bank').getByRole('button', { name: 'music', exact: true }).dragTo(page.locator('.ordered-words'));
  await expect(page.locator('.ordered-words > li')).toHaveCount(3); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/journey-ordering-mobile.png', fullPage: true });
  for (let i = 0; i < s.items.length; i++) {
    await page.locator('.question-nav button').nth(i).click(); const q = s.items[i];
    if (q.question.type === 'ORDERING') { await page.getByRole('button', { name: 'Xóa câu trả lời', exact: true }).click(); await chooseOrdering(page); }
    else for (let n = 0; n < 2; n++) { await page.locator('.matching-left button').nth(n).click(); const id = 'r' + (n + 1); await page.locator('.matching-right button').nth(q.question.content.rightItems!.findIndex(r => r.id === id)).click(); }
    if (q.practiceRole === 'GUIDED') { await page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true }).click(); await expect(page.locator('.key-explanation')).toContainText('She likes music'); await expect(page.locator('.word-bridge .bridge-scene')).toHaveAttribute('data-open', 'true'); }
  }
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); await expect(page.locator('.result-score')).toContainText('100');
  await expect(page.locator('.game-vault-scene .bridge-scene')).toHaveAttribute('data-open', 'true');
  await expect(page.locator('.game-chests .is-open')).toHaveCount(3);
  const rounds = await (await page.request.get('/api/me/rounds?size=50')).json(); const round = rounds.items.find((r: { unit: { id: number } }) => r.unit.id === unitId); expect(round.completedLessons).toBe(1); expect(round.state).toBe('COMPLETED');
  await page.goto('/#/learning?unit=' + unitId); await expect(page.locator('.round-overview')).toContainText('Đã hoàn thành 1/1 bài'); await expect(page.locator('.round-overview')).toContainText('hoàn thành tất cả bài');
  await page.reload(); await expect(page.locator('.round-overview')).toContainText('Đã hoàn thành 1/1 bài');
});

test('matching cards support keyboard, reassignment, drag and mobile recovery', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); const s = await start(page); const index = s.items.findIndex(i => i.question.type === 'MATCHING');
  await page.locator('.question-nav button').nth(index).click();
  const left = page.locator('.matching-left button'), right = page.locator('.matching-right button');
  await left.first().focus(); await page.keyboard.press('Enter'); await right.first().focus(); await page.keyboard.press('Enter'); await expect(page.locator('.matched-pairs li')).toHaveCount(1);
  await left.nth(1).click(); await right.first().click(); await expect(page.locator('.matched-pairs li')).toHaveCount(1);
  // Move through intermediate points so Chromium starts native dragging before entering the target.
  const source = await left.first().boundingBox(), target = await right.nth(1).boundingBox();
  expect(source).not.toBeNull(); expect(target).not.toBeNull();
  await page.mouse.move(source!.x + 12, source!.y + 12); await page.mouse.down();
  await page.mouse.move(target!.x + target!.width / 2, target!.y + target!.height / 2, { steps: 12 });
  await page.mouse.move(target!.x + target!.width / 2 + 1, target!.y + target!.height / 2 + 1); await page.mouse.up();
  await expect(page.locator('.matched-pairs li')).toHaveCount(2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false); await page.screenshot({ path: 'test-results/journey-matching-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ'); await page.reload(); await expect(page.locator('.matched-pairs li')).toHaveCount(2);
  await page.getByRole('button', { name: 'Bỏ cặp 1', exact: true }).click(); await expect(page.locator('.matched-pairs li')).toHaveCount(1);
});

test('teacher round numbers match the learner journey and survive own/search filters', async ({ page, browser }) => {
  await write(page.request, '/api/auth/login', { email: author, password: pass });
  const root = await (await page.request.get('/api/author/units/' + unitId)).json();
  const draft = await write(page.request, '/api/author/units', { requestId: randomUUID(), topicId: root.unit.topicId, title: 'Vòng chờ công bố ' + stamp, description: '', stage: 100, sortOrder: 0 });
  const viewer = await browser.newContext();
  try {
    const email = randomUUID() + '@test.local';
    await write(viewer.request, '/api/auth/register', { email, password: pass, fullName: 'Xem số vòng học viên' });
    await write(viewer.request, '/api/auth/login', { email, password: pass });
    const journey = await (await viewer.request.get('/api/me/rounds?size=50')).json();
    const expected = journey.items.find((r: { unit: { id: number } }) => r.unit.id === unitId).number;
    expect(expected).toBeGreaterThan(1); expect(journey.items.some((r: { unit: { id: number } }) => r.unit.id === draft.id)).toBe(false);
    await page.goto('/#/content');
    const published = page.locator('.catalog-card').filter({ has: page.getByRole('heading', { name: root.unit.title, exact: true }) });
    await expect(published.locator('.eyebrow')).toHaveText('VÒNG ĐẤU ' + expected);
    const waiting = page.locator('.catalog-card').filter({ has: page.getByRole('heading', { name: draft.title, exact: true }) });
    await expect(waiting.locator('.eyebrow')).toHaveText('VÒNG ĐẤU ' + draft.roundNumber);
    await page.getByLabel('Tìm học liệu', { exact: true }).fill(root.unit.title); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    await expect(page.locator('.catalog-card')).toHaveCount(1); await expect(published.locator('.eyebrow')).toHaveText('VÒNG ĐẤU ' + expected);
    await page.getByRole('button', { name: 'Bài học', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Vòng đấu', exact: true }).locator('option[value="' + unitId + '"]')).toHaveText('Vòng đấu ' + expected + ' · ' + root.unit.title);
    await page.getByRole('button', { name: 'Thống kê học liệu', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Vòng đấu của tôi', exact: true }).locator('option[value="' + unitId + '"]')).toHaveText('Vòng đấu ' + expected + ' · ' + root.unit.title);
  } finally { await viewer.close(); }
});

test('editing a persistent number survives reload and duplicate numbers keep the form editable', async ({ page }) => {
  await write(page.request, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  const root = await (await page.request.get('/api/author/units/' + unitId)).json();
  await page.goto('/#/content?catalogEditor=' + unitId);
  await expect(page.getByLabel('Số thứ tự vòng', { exact: true })).toHaveValue(String(root.unit.roundNumber));
  const existing = await (await page.request.get('/api/units?size=50')).json();
  const taken = existing.items.find((u: { id: number }) => u.id !== unitId).roundNumber;
  await page.getByLabel('Số thứ tự vòng', { exact: true }).fill(String(taken));
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Số thứ tự vòng đã được sử dụng');
  await expect(page.getByLabel('Số thứ tự vòng', { exact: true })).toBeEnabled();
  const changed = 10000 + Math.floor(Math.random() * 100000);
  await page.getByLabel('Số thứ tự vòng', { exact: true }).fill(String(changed));
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toBeVisible();
  await page.getByLabel('Tìm học liệu', { exact: true }).fill(root.unit.title);
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.locator('.catalog-card .eyebrow')).toHaveText('VÒNG ĐẤU ' + changed);
  await page.goto('/#/content?catalogEditor=' + unitId);
  await expect(page.getByLabel('Số thứ tự vòng', { exact: true })).toHaveValue(String(changed));
  await page.reload();
  await expect(page.getByLabel('Số thứ tự vòng', { exact: true })).toHaveValue(String(changed));
  const saved = await (await page.request.get('/api/author/units/' + unitId)).json();
  expect(saved.unit.roundNumber).toBe(changed);
});
