import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Answer, Item, Snapshot } from '../src/features/learning/types';

type SeedQuestion = { data: { prompt: string; answerKey: { correctOptionId?: string; blanks?: { blankId: string; acceptedAnswers: string[] }[]; pairs?: { leftId: string; rightId: string }[] } } };
const seed = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as { lessons: { questions: SeedQuestion[] }[] };
const questions = seed.lessons.flatMap(l => l.questions);
const password = 'RewardTest@12345';
async function post(page: Page, path: string, body: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const result = await page.request.post(path, { headers: { [csrf.headerName]: csrf.token }, data: body });
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
async function register(page: Page) {
  const email = `m05-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
  await post(page, '/api/auth/register', { email, password, fullName: 'Học viên M05' });
  await page.goto('/#/rewards'); await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Phần thưởng học tập.' })).toBeVisible(); return email;
}
function answer(item: Item): Answer {
  const key = questions.find(q => q.data.prompt === item.question.prompt)?.data.answerKey;
  if (!key) throw new Error('No seed answer for ' + item.question.prompt);
  if (item.question.type === 'SINGLE_CHOICE') return { type: 'SINGLE_CHOICE', optionId: key.correctOptionId! };
  if (item.question.type === 'FILL_BLANK') return { type: 'FILL_BLANK', values: key.blanks!.map(b => ({ blankId: b.blankId, value: b.acceptedAnswers[0] })) };
  return { type: 'MATCHING', pairs: key.pairs! };
}
async function finishPractice(page: Page) {
  const topics = await (await page.request.get('/api/topics?size=50')).json();
  const snapshot: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'PRACTICE', topicId: topics.items[0].id, difficulty: 'EASY', count: 3 });
  const result = await post(page, `/api/attempts/${snapshot.attemptId}/submit`, {
    requestId: crypto.randomUUID(), baseRevision: snapshot.revision, playerState: snapshot.playerState,
    answers: snapshot.items.map(item => ({ attemptQuestionId: item.attemptQuestionId, response: answer(item) })),
  });
  expect(result.correctCount).toBe(snapshot.items.length);
  return { snapshot, result, xp: snapshot.items.length * 5 };
}

test('new learner has visible rewards, clear rules and three unearned badges', async ({ page }) => {
  await register(page);
  await expect(page.locator('.reward-number').first()).toContainText('1');
  await expect(page.locator('.reward-badge')).toHaveCount(3);
  await expect(page.locator('.reward-badge.earned')).toHaveCount(0);
  await expect(page.getByText('Chưa có bài nộp được xét thưởng.', { exact: false })).toBeVisible();
  await expect(page.getByText('Tối đa 200 XP/ngày.', { exact: false })).toBeVisible();
});

test('early levels need ten then twenty XP and reads do not award extra XP', async ({ page }) => {
  await register(page);
  const detail = await (await page.request.get('/api/units/1')).json();
  for (const [index, lesson] of detail.lessons.entries()) {
    const snapshot: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: lesson.id });
    await post(page, '/api/attempts/' + snapshot.attemptId + '/submit', { requestId: crypto.randomUUID(), baseRevision: snapshot.revision, playerState: snapshot.playerState, answers: snapshot.items.map(item => ({ attemptQuestionId: item.attemptQuestionId, response: answer(item) })) });
    if (index === 0) { await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(25); const first = await (await page.request.get('/api/me/gamification')).json(); expect(first.activityLevel).toBe(2); expect(first.levelFloorXp).toBe(10); expect(first.nextLevelAtXp).toBe(30); }
  }
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(50);
  await page.reload(); await expect(page.getByText('Cấp 3 → 4: 30 XP', { exact: true })).toBeVisible();
  const summary = await (await page.request.get('/api/me/gamification')).json(); expect(summary.activityLevel).toBe(3); expect(summary.levelFloorXp).toBe(30); expect(summary.nextLevelAtXp).toBe(60); expect(summary.policyVersion).toBe('m05-p0-v3');
  await post(page, '/api/me/gamification/sync', { requestId: crypto.randomUUID() });
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(50);
  expect(await page.getByRole('progressbar', { name: 'Tiến độ cấp độ hoạt động' }).evaluate((bar: HTMLProgressElement) => bar.value)).toBeCloseTo(200 / 3, 3);
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: test.info().outputPath('progressive-level-mobile.png'), fullPage: true });
});

test('home shows the daily cap and links to server-timed league and review reward rules', async ({ page }) => {
  await register(page); await page.getByRole('button', { name: 'Trang chủ', exact: true }).click();
  await expect(page.locator('.reward-strip')).toContainText('0/200');await expect(page.locator('.reward-strip')).toContainText('XP hôm nay');
  await page.getByRole('button', { name: 'Thi đua tuần →', exact: true }).click();
  await expect(page.getByLabel('Thời gian còn lại của tuần')).toContainText('Còn');
  await page.evaluate(() => { Date.now = () => 0; });
  await expect(page.getByLabel('Thời gian còn lại của tuần')).toContainText('Còn');
  await page.getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await page.getByRole('tab', { name: 'Ôn tập cá nhân', exact: true }).click();
  await expect(page.getByLabel('Điều kiện thưởng ôn')).toContainText('5 XP');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('review-reward-rules-mobile.png'), fullPage: true });
});

test('background worker awards submitted work and dashboard updates without manual sync', async ({ page }) => {
  await register(page); const { xp } = await finishPractice(page);
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(xp);
  await expect(page.locator('.reward-level .reward-number')).toContainText(`${xp} XP tích lũy`, { timeout: 15000 });
  await expect(page.locator('.reward-history .xp-award')).toContainText(`+${xp} XP`, { timeout: 15000 });
  const summary = await (await page.request.get('/api/me/gamification')).json();
  expect(summary.day.isActive).toBe(true); expect(summary.streak.current).toBe(1);
  await page.screenshot({ path: test.info().outputPath('rewards-desktop.png'), fullPage: true });
});

test('result refresh and lost sync ACK never duplicate earned XP', async ({ page }) => {
  await register(page); const { snapshot, xp } = await finishPractice(page);
  let lost = false;
  await page.route('**/api/me/gamification/sync', async route => {
    if (!lost) { lost = true; const response = await route.fetch(); expect(response.ok()).toBeTruthy(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.goto(`/#/learning?attempt=${snapshot.attemptId}`);
  await expect(page.locator('.attempt-rewards h2')).toHaveText(`+${xp} XP`, { timeout: 15000 });
  await page.reload(); await expect(page.locator('.attempt-rewards h2')).toHaveText(`+${xp} XP`);
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(xp);
  await expect(page.locator('.attempt-rewards summary')).toBeVisible();
});

test('a delayed pending read cannot replace a terminal reward', async ({ page }) => {
  await register(page); const { snapshot, result, xp } = await finishPractice(page);
  let release!: () => void; const gate = new Promise<void>(resolve => { release = resolve; });
  let first = true;
  await page.route(`**/api/me/gamification/activities?submissionId=${result.submissionId}`, async route => {
    if (first) {
      first = false; await gate;
      await route.fulfill({ json: { items: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
        requestedSubmission: { submissionId: result.submissionId, state: 'PENDING' } } });
    } else await route.continue();
  });
  try {
    await page.goto(`/#/learning?attempt=${snapshot.attemptId}`);
    await expect(page.locator('.attempt-rewards h2')).toHaveText(`+${xp} XP`, { timeout: 15000 });
    const late = page.waitForResponse(async response => response.url().includes(`/api/me/gamification/activities?submissionId=${result.submissionId}`)
      && (await response.json()).requestedSubmission?.state === 'PENDING');
    release();
    await late; await page.waitForTimeout(250);
    await expect(page.locator('.attempt-rewards h2')).toHaveText(`+${xp} XP`);
    await expect(page.locator('.attempt-rewards').getByText('Đang xét phần thưởng…')).toHaveCount(0);
  } finally { release(); }
});

test('fixture UI distinguishes pending and deferred backlog and supports keyboard retry', async ({ page }) => {
  await register(page); const baseline = await (await page.request.get('/api/me/gamification')).json();
  await page.route('**/api/me/gamification', route => route.fulfill({ json: { ...baseline, backlog: { pendingSubmissions: 1, deferredSubmissions: 1 }, processingState: 'PENDING' } }));
  await page.reload();
  await expect(page.getByText('Có 1 bài nộp đang chờ xét thưởng.', { exact: false })).toBeVisible();
  await expect(page.getByText('Có bài nộp cần xử lý lại.', { exact: false })).toBeVisible();
  await page.unroute('**/api/me/gamification');
  const retry = page.getByRole('button', { name: 'Cập nhật phần thưởng', exact: true });
  await retry.focus(); await page.keyboard.press('Enter');
  await expect(page.getByText('Có 1 bài nộp đang chờ xét thưởng.', { exact: false })).toHaveCount(0);
  await expect(page.getByText('Có bài nộp cần xử lý lại.', { exact: false })).toHaveCount(0);
  await expect(page.locator('.reward-level')).toContainText('0 XP tích lũy');
});

test('old summary response and repeated invalidations never decrease or duplicate XP', async ({ page }) => {
  await register(page); const old = await (await page.request.get('/api/me/gamification')).json();const { xp } = await finishPractice(page);
  let release!: () => void; const gate = new Promise<void>(resolve => { release = resolve; });let first = true;
  await page.route('**/api/me/gamification', async route => { if (first) { first = false; await gate; await route.fulfill({ json: old }); } else await route.continue(); });
  try {
    await page.reload(); await expect.poll(() => first).toBe(false);
    await page.evaluate(() => { for (let i = 0; i < 5; i++) window.dispatchEvent(new Event('gamification:changed')); });
    await expect(page.locator('.reward-level')).toContainText(`${xp} XP tích lũy`);
    const late = page.waitForResponse(async r => r.url().endsWith('/api/me/gamification') && (await r.json()).revision === old.revision);
    release(); await late; await page.waitForTimeout(250);
    await expect(page.locator('.reward-level')).toContainText(`${xp} XP tích lũy`);
    expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(xp);
  } finally { release(); }
});

test('a real socket disconnect reconnects and reconciles rewards without manual sync', async ({ page }) => {
  await page.addInitScript(() => {
    const NativeSocket = window.WebSocket;
    const tracked: WebSocket[] = [];
    Object.assign(window, { rewardTestSockets: tracked });
    window.WebSocket = class extends NativeSocket {
      constructor(url: string | URL, protocols?: string | string[]) { super(url, protocols); tracked.push(this); }
    };
  });
  await register(page);
  await page.waitForFunction(() => (window as unknown as { rewardTestSockets: WebSocket[] }).rewardTestSockets.some(s => s.readyState === WebSocket.OPEN));
  await page.evaluate(() => (window as unknown as { rewardTestSockets: WebSocket[] }).rewardTestSockets.at(-1)!.close());
  const { xp } = await finishPractice(page);
  await page.waitForFunction(() => { const list = (window as unknown as { rewardTestSockets: WebSocket[] }).rewardTestSockets;return list.length >= 2 && list.at(-1)!.readyState === WebSocket.OPEN; });
  await expect(page.locator('.reward-level')).toContainText(`${xp} XP tích lũy`, { timeout: 20000 });
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(xp);
});

test('rewards stay private across account switch and mobile layout fits', async ({ page }) => {
  await register(page); const { xp } = await finishPractice(page);
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(xp);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await register(page); await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.reward-level .reward-number')).toContainText('0 XP tích lũy');
  await expect(page.locator('.reward-history .xp-award')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('rewards-mobile.png'), fullPage: true });
});
