import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Answer, Snapshot } from '../src/features/learning/types';

const password = 'StatsBrowser@12345';
const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/)
  .filter(l => l && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
async function post(page: Page, path: string, data: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const result = await page.request.post(path, { headers: { [csrf.headerName]: csrf.token }, data });
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
async function login(page: Page, email: string, secret: string) {
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(secret);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
}
async function register(page: Page) {
  const email = `m07-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
  await post(page, '/api/auth/register', { email, password, fullName: 'Học viên M07' });
  await page.goto('/#/profile'); await login(page, email, password); return email;
}
async function profile(page: Page) {
  await page.getByRole('button', { name: 'Hồ sơ học tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Hồ sơ học tập.' })).toBeVisible();
  await page.getByRole('button', { name: 'Cập nhật số liệu', exact: true }).click();
  await expect(page.getByRole('status')).toHaveCount(0);
}
async function submitBlank(page: Page) {
  const units = await (await page.request.get('/api/units?size=50')).json();
  const detail = await (await page.request.get('/api/units/' + units.items[0].id)).json();
  const snapshot: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: detail.lessons[0].id });
  await post(page, `/api/attempts/${snapshot.attemptId}/submit`, { requestId: crypto.randomUUID(), baseRevision: snapshot.revision,
    playerState: snapshot.playerState, answers: snapshot.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: null })) });
  return snapshot;
}
test('student profile shows honest empty values and persists route on reload', async ({ page }) => {
  await register(page); await profile(page);
  await expect(page.getByText('0 XP · Cấp 1 · Chuỗi 0 ngày', { exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có bài hoàn thành. Bạn có thể bắt đầu từ Khám phá bài học.', { exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Hồ sơ học tập.' })).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  mkdirSync(resolve(process.cwd(), '../../.test-state'), { recursive: true });
  await page.screenshot({ path: resolve(process.cwd(), '../../.test-state/m07-profile-mobile.png'), fullPage: true });
});

test('profile reconciles committed XP and current league with M05 without awarding again on refresh', async ({ page }) => {
  await register(page);
  await post(page, '/api/me/league/join', { requestId: crypto.randomUUID() });
  const units = await (await page.request.get('/api/units?size=50')).json();
  const snapshot: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'PRACTICE', topicId: units.items[0].topicId, difficulty: 'EASY', count: 3 });
  type Seed = { lessons: { questions: { data: { prompt: string; answerKey: { correctOptionId: string; blanks: { blankId: string; acceptedAnswers: string[] }[]; pairs: { leftId: string; rightId: string }[] } } }[] }[] };
  const bank = (JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as Seed).lessons.flatMap(l => l.questions);
  const answers = snapshot.items.map(item => {
    const key = bank.find(question => question.data.prompt === item.question.prompt)!.data.answerKey;
    const response: Answer = item.question.type === 'SINGLE_CHOICE' ? { type: 'SINGLE_CHOICE', optionId: key.correctOptionId }
      : item.question.type === 'FILL_BLANK' ? { type: 'FILL_BLANK', values: key.blanks.map(blank => ({ blankId: blank.blankId, value: blank.acceptedAnswers[0] })) }
      : { type: 'MATCHING', pairs: key.pairs };
    return { attemptQuestionId: item.attemptQuestionId, response };
  });
  await post(page, `/api/attempts/${snapshot.attemptId}/submit`, { requestId: crypto.randomUUID(), baseRevision: snapshot.revision, playerState: snapshot.playerState, answers });
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(15);
  await profile(page);
  const value = await (await page.request.get('/api/me/statistics')).json();
  const league = await (await page.request.get('/api/me/league')).json();
  expect(value.rewards.totalXp).toBe(15);
  expect(value.rewards.currentWeek.xp).toBe(league.membership.weeklyXp);
  expect(value.rewards.currentWeek.position).toBe(league.membership.rank);
  await expect(page.getByText('15 XP · Cấp 1 · Chuỗi 1 ngày', { exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByText('15 XP · Cấp 1 · Chuỗi 1 ngày', { exact: true })).toBeVisible();
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(15);
});
test('profile reads submitted work and links to review and immutable results', async ({ page }) => {
  await register(page); const snapshot = await submitBlank(page); await profile(page);
  const metrics = await (await page.request.get('/api/me/statistics')).json();
  expect(metrics.activity.submittedAttempts).toBe(1);
  expect(metrics.performance.first.total).toBe(snapshot.items.length);
  expect(metrics.performance.first.accuracyPercent).toBeNull();
  await expect(page.getByText('Có 1 kết quả mới chưa cập nhật vào lịch ôn. Mở Ôn tập cá nhân để cập nhật.', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Thống kê theo', exact: true }).selectOption('OBJECTIVE');
  await expect(page.getByRole('status')).toHaveCount(0);
  await page.getByRole('button', { name: 'Xem nội dung cần ôn', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Ôn tập cá nhân.' })).toBeVisible();
  await expect(page.getByText('Đang cập nhật kết quả và nội dung ôn…', { exact: true })).toHaveCount(0);
  await profile(page); await page.getByRole('button', { name: 'Xem kết quả', exact: true }).click();
  await expect(page.getByText('KẾT QUẢ ĐÃ NỘP', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(new RegExp('attempt=' + snapshot.attemptId));
});
test('accounts in the same browser do not share profile or history', async ({ page }) => {
  await register(page); await submitBlank(page); await profile(page);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await register(page); await profile(page);
  const overview = await (await page.request.get('/api/me/statistics')).json();
  expect(overview.activity.submittedAttempts).toBe(0);
  await expect(page.getByText('Chưa có lượt làm phù hợp.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xem kết quả', exact: true })).toHaveCount(0);
});
test('network failure shows retry and does not manufacture zero statistics', async ({ page }) => {
  await register(page); await profile(page);
  await page.route('**/api/me/statistics?*', route => route.abort('failed'));
  await page.getByRole('button', { name: 'Cập nhật số liệu', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByLabel('Tổng quan học tập', { exact: true })).toHaveCount(0);
  await page.unroute('**/api/me/statistics?*');
  await page.getByRole('button', { name: 'Thử lại thống kê', exact: true }).click();
  await expect(page.getByLabel('Tổng quan học tập', { exact: true })).toBeVisible();
});
test('admin sees aggregates and bounded question filters while students are forbidden', async ({ page }) => {
  await register(page);
  expect((await page.request.get('/api/admin/statistics')).status()).toBe(403);
  await expect(page.getByRole('button', { name: 'Thống kê học tập', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await login(page, env.BOOTSTRAP_ADMIN_EMAIL, env.BOOTSTRAP_ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Thống kê học tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Thống kê học tập.' })).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Kết quả theo độ khó', exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Khoảng kết quả', exact: true }).selectOption('7D');
  await page.getByRole('combobox', { name: 'Độ khó câu hỏi', exact: true }).selectOption('HARD');
  await expect(page.getByRole('status')).toHaveCount(0);
  const stats = await (await page.request.get('/api/admin/statistics')).json();
  expect(stats.presence.connectedAccounts).toBeGreaterThanOrEqual(1);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: resolve(process.cwd(), '../../.test-state/m07-admin-mobile.png'), fullPage: true });
});
