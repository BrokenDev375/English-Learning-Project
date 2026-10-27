import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { Answer, Item, Snapshot } from '../src/features/learning/types';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

type SeedQuestion = { data: { prompt: string; type: string; answerKey: { correctOptionId?: string; blanks?: { blankId: string; acceptedAnswers: string[] }[]; pairs?: { leftId: string; rightId: string }[] } } };
const curriculum = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as { lessons: { questions: SeedQuestion[] }[] };
const seedQuestions = curriculum.lessons.flatMap(l => l.questions);
const password = 'ReviewTest@12345';
async function register(page: Page) {
  const email = 'm04-' + Date.now() + '-' + Math.random().toString(36).slice(2) + '@test.local';
  const token = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.post('/api/auth/register', { headers: { [token.headerName]: token.token }, data: { email, password, fullName: 'Học viên M04' } })).status()).toBe(201);
  await page.goto('/#/learning');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'Ôn tập cá nhân', exact: true })).toBeVisible();
}
async function post(page: Page, path: string, body: unknown, method = 'POST') {
  const token = await (await page.request.get('/api/auth/csrf')).json();
  const result = await page.request.fetch(path, { method, headers: { [token.headerName]: token.token }, data: body });
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
async function prepare(page: Page, right = false) {
  const units = await (await page.request.get('/api/units?size=50')).json();
  let lesson = 0;
  for (const unit of units.items) {
    const detail = await (await page.request.get('/api/units/' + unit.id)).json();
    const found = detail.lessons.find((l: { title: string }) => l.title.includes('A day with Linh'));
    if (found) { lesson = found.id; break; }
  }
  expect(lesson).toBeGreaterThan(0);
  const s: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: lesson });
  await finish(page, s, right); return s;
}
function answer(item: Item, right: boolean): Answer {
  const seed = seedQuestions.find(q => q.data.prompt === item.question.prompt);
  if (!seed) throw new Error('Missing seeded question: ' + item.question.prompt);
  const key = seed.data.answerKey;
  if (item.question.type === 'SINGLE_CHOICE') return { type: 'SINGLE_CHOICE', optionId: right ? key.correctOptionId! : item.question.content.options!.find(o => o.id !== key.correctOptionId)!.id };
  if (item.question.type === 'FILL_BLANK') return { type: 'FILL_BLANK', values: key.blanks!.map(b => ({ blankId: b.blankId, value: right ? b.acceptedAnswers[0] : 'wrong-example' })) };
  return { type: 'MATCHING', pairs: key.pairs!.map((p, i, all) => ({ leftId: p.leftId, rightId: right ? p.rightId : all[(i + 1) % all.length].rightId })) };
}
async function finish(page: Page, s: Snapshot, right: boolean) {
  return post(page, '/api/attempts/' + s.attemptId + '/submit', {
    requestId: crypto.randomUUID(), baseRevision: s.revision, playerState: s.playerState,
    answers: s.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: answer(i, right) })),
  });
}
async function openReview(page: Page) {
  await page.getByRole('tab', { name: 'Ôn tập cá nhân', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Ôn tập cá nhân.' })).toBeVisible();
  await expect(page.getByText('Đang cập nhật kết quả và nội dung ôn…', { exact: true })).toHaveCount(0);
}
async function snapshot(page: Page): Promise<Snapshot> {
  const id = page.url().match(/attempt=(\d+)/)![1];
  return (await page.request.get('/api/attempts/' + id)).json();
}

test('review shows an honest empty state for a new student', async ({ page }) => {
  await register(page); await openReview(page);
  await expect(page.getByRole('heading', { name: 'Chưa có kết quả để chọn bài ôn' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bắt đầu ôn nội dung cần ôn', exact: true })).toBeDisabled();
});

test('review uses unseen variants, retains drafts and reschedules after independent success', async ({ page }) => {
  await register(page); const learned = await prepare(page); await openReview(page);
  await expect(page.locator('.review-objective')).toHaveCount(1);
  await expect(page.locator('.review-state')).toHaveText('Cần luyện thêm');
  await expect(page.locator('.review-reason strong')).toHaveText('Vì sao cần ôn?');
  await expect(page.locator('.review-reason span')).not.toBeEmpty();
  const data = await (await page.request.get('/api/me/review')).json();
  expect(data.objectives.items[0].hasMistakes).toBe(true);
  await page.getByLabel('Số câu mỗi lượt', { exact: true }).fill('1');
  await page.getByRole('button', { name: 'Ôn mục tiêu còn lỗi sai', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(1);
  const s = await snapshot(page);
  expect(s.mode).toBe('REVIEW');
  expect(s.items.every(i => i.objectiveId === learned.items[0].objectiveId && i.practiceRole === 'REVIEW_VARIANT' && !i.wasSeenBefore)).toBe(true);
  await expect(page.getByRole('button', { name: 'Mở gợi ý', exact: true })).toHaveCount(0);
  const first = s.items[0];
  if (first.question.type === 'SINGLE_CHOICE') await page.locator('.answer-fields').getByRole('radio').first().check();
  else if (first.question.type === 'FILL_BLANK') for (const field of await page.locator('.answer-fields').getByRole('textbox').all()) await field.fill('draft');
  else if (first.question.type === 'MATCHING') for (let i = 0; i < first.question.content.leftItems!.length; i++) { await page.locator('.matching-left button').nth(i).click(); await page.locator('.matching-right button').nth(i).click(); }
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click();
  await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  const saved = await snapshot(page); await page.reload();
  await expect(page.locator('.question-nav button')).toHaveCount(1);
  expect((await snapshot(page)).items).toEqual(saved.items);
  await finish(page, await snapshot(page), true); await page.reload();
  await expect(page.locator('.result-score')).toContainText('100.00');
  await page.getByRole('button', { name: 'Xem nội dung cần ôn', exact: true }).click();
  await expect(page.locator('.review-state')).toHaveText('Đã có mốc ôn');
  const updated = await (await page.request.get('/api/me/review')).json();
  expect(updated.objectives.items[0].stage).toBe(1);
  expect(updated.objectives.items[0].submissionCount).toBe(2);
  expect(updated.objectives.items[0].needsReview).toBe(false);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '../../.test-state/m04-review-mobile.png', fullPage: true });
});

test('lost synchronization ACK replays the same request without counting twice', async ({ page }) => {
  await register(page); await prepare(page);
  let original: unknown; let calls = 0;
  await page.route('**/api/me/review/sync', async route => {
    calls++;
    if (calls === 1) { original = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(200); return route.abort('failed'); }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await openReview(page);
  await expect(page.getByRole('button', { name: 'Thử tải lại nội dung ôn', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Thử tải lại nội dung ôn', exact: true }).click();
  await expect(page.locator('.review-objective')).toHaveCount(1);
  const d = await (await page.request.get('/api/me/review')).json();
  expect(d.objectives.items[0].submissionCount).toBe(1); expect(calls).toBe(2);
});

test('lost create ACK resumes the same review attempt after retry', async ({ page }) => {
  await register(page); await prepare(page); await openReview(page);
  let original: unknown; let committedId = 0; let calls = 0;
  await page.getByLabel('Số câu mỗi lượt', { exact: true }).fill('1');
  await page.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    calls++;
    if (calls === 1) {
      original = route.request().postDataJSON(); const r = await route.fetch(); expect(r.status()).toBe(201);
      committedId = (await r.json()).attemptId; return route.abort('failed');
    }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await page.getByRole('button', { name: 'Bắt đầu ôn nội dung cần ôn', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(1);
  expect((await snapshot(page)).attemptId).toBe(committedId); expect(calls).toBe(2);
});

test('different accounts in one browser do not share review progress', async ({ page }) => {
  await register(page); await prepare(page); await openReview(page);
  await expect(page.locator('.review-objective')).toHaveCount(1);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await register(page); await openReview(page);
  await expect(page.getByRole('heading', { name: 'Chưa có kết quả để chọn bài ôn' })).toBeVisible();
  await expect(page.locator('.review-objective')).toHaveCount(0);
});
