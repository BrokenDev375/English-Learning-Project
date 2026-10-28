import { fixtureWrite } from './governance-fixtures';
import { test, expect } from '@playwright/test';
import type { Page, Browser } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Snapshot } from '../src/features/learning/types';

const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/)
  .filter(line => line && !line.startsWith('#') && line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const secret = 'TeacherStats@12345';
async function mutate(page: Page, method: 'POST' | 'PATCH', path: string, body: unknown) {
  const result = await fixtureWrite(page.request, path, body, method);
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
const loginApi = (page: Page, email: string, password: string) => mutate(page, 'POST', '/api/auth/login', { email, password });
async function createTeacher(browser: Browser) {
  const context = await browser.newContext(), page = await context.newPage();
  try {
    await loginApi(page, env.BOOTSTRAP_ADMIN_EMAIL, env.BOOTSTRAP_ADMIN_PASSWORD);
    const email = `m07-teacher-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
    const user = await mutate(page, 'POST', '/api/auth/register', { email, password: secret, fullName: 'Giảng viên thống kê' });
    await mutate(page, 'PATCH', '/api/admin/users/' + user.id + '/role', { role: 'TEACHER' });
    return email;
  } finally { await context.close(); }
}
let foreignUnit: number, foreignTitle: string;
test.beforeAll(async ({ browser }) => {
  const email = await createTeacher(browser), context = await browser.newContext(), page = await context.newPage();
  try {
    await loginApi(page, email, secret);
    const topic = await mutate(page, 'POST', '/api/author/topics', { requestId: crypto.randomUUID(), code: 'teacher-stats-' + Date.now(), name: 'Chủ đề riêng', description: '' });
    foreignTitle = 'Unit của giảng viên khác ' + Date.now();
    const unit = await mutate(page, 'POST', '/api/author/units', { requestId: crypto.randomUUID(), topicId: topic.id, title: foreignTitle, description: '', stage: 1, sortOrder: 0 });
    foreignUnit = unit.id;
  } finally { await context.close(); }
});
async function teacher(page: Page) {
  await loginApi(page, env.DEMO_TEACHER_EMAIL, env.DEMO_TEACHER_PASSWORD);
  await page.goto('/#/statistics');
  await expect(page.getByRole('heading', { name: 'Thống kê học liệu.' })).toBeVisible();
}
async function submitBlank(page: Page, input: unknown) {
  const attempt: Snapshot = await mutate(page, 'POST', '/api/attempts', { requestId: crypto.randomUUID(), ...input as object });
  await mutate(page, 'POST', `/api/attempts/${attempt.attemptId}/submit`, { requestId: crypto.randomUUID(), baseRevision: attempt.revision,
    playerState: attempt.playerState, answers: attempt.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: null })) });
  return attempt;
}
test('teacher sees owned Unit results, ignores unassigned practice, and keeps filters across reload/history/mobile', async ({ page, browser }) => {
  await teacher(page);
  const owned = await (await page.request.get('/api/teacher/statistics/units?size=50')).json();
  const unitId = owned.items[0].unitId;
  const before = await (await page.request.get('/api/teacher/statistics?unitId=' + unitId)).json();
  const context = await browser.newContext(), student = await context.newPage();
  try {
    const email = `m07-teacher-student-${Date.now()}@test.local`;
    await mutate(student, 'POST', '/api/auth/register', { email, password: secret, fullName: 'Học viên thống kê Unit' });
    await loginApi(student, email, secret);
    const detail = await (await student.request.get('/api/units/' + unitId)).json();
    const learn = await submitBlank(student, { mode: 'LEARN', lessonId: detail.lessons[0].id });
    await submitBlank(student, { mode: 'PRACTICE', topicId: detail.unit.topicId, difficulty: 'EASY', count: 2 });
    await page.getByRole('combobox', { name: 'Vòng đấu của tôi', exact: true }).selectOption(String(unitId));
    const cards = page.getByLabel('Tổng quan học liệu', { exact: true });
    await expect(cards.locator('article').filter({ hasText: 'Lượt đã nộp trong khoảng' }).locator('strong')).toHaveText(String(before.activity.submittedAttempts + 1));
    const after = await (await page.request.get('/api/teacher/statistics?unitId=' + unitId)).json();
    expect(after.performance.first.total).toBe(before.performance.first.total + learn.items.length);
    await expect(page.getByRole('combobox', { name: 'Vòng đấu của tôi', exact: true }).locator('option')).not.toContainText([foreignTitle]);
    expect((await page.request.get('/api/teacher/statistics?unitId=' + foreignUnit)).status()).toBe(404);
    await page.getByRole('combobox', { name: 'Thống kê theo', exact: true }).selectOption('OBJECTIVE');
    await page.getByRole('combobox', { name: 'Khoảng kết quả', exact: true }).selectOption('7D');
    await page.getByRole('combobox', { name: 'Độ khó câu hỏi', exact: true }).selectOption('HARD');
    await page.reload();
    await expect(page.getByRole('combobox', { name: 'Vòng đấu của tôi', exact: true })).toHaveValue(String(unitId));
    await expect(page.getByRole('combobox', { name: 'Khoảng kết quả', exact: true })).toHaveValue('7D');
    await page.goBack(); await expect(page.getByRole('combobox', { name: 'Khoảng kết quả', exact: true })).toHaveValue('ALL');
    await page.goForward(); await expect(page.getByRole('combobox', { name: 'Khoảng kết quả', exact: true })).toHaveValue('7D');
    await expect(page.getByRole('status')).toHaveCount(0);
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    mkdirSync(resolve(process.cwd(), '../../.test-state'), { recursive: true });
    await page.screenshot({ path: resolve(process.cwd(), '../../.test-state/m07-teacher-mobile.png'), fullPage: true });
  } finally { await context.close(); }
});
test('teacher without Units sees empty results and cannot read student or admin statistics', async ({ page, browser }) => {
  const email = await createTeacher(browser); await loginApi(page, email, secret); await page.goto('/#/statistics');
  await expect(page.getByRole('heading', { name: 'Thống kê học liệu.' })).toBeVisible();
  await expect(page.getByText('Bạn chưa được phân công vòng đấu. Quản trị viên phân công vòng để bạn biên soạn và theo dõi kết quả.', { exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có bài học trong phạm vi này.', { exact: true })).toBeVisible();
  expect((await page.request.get('/api/me/statistics')).status()).toBe(403);
  expect((await page.request.get('/api/admin/statistics')).status()).toBe(403);
  expect((await page.request.get('/api/teacher/statistics?unitId=' + foreignUnit)).status()).toBe(404);
  await expect(page.getByRole('button', { name: 'Hồ sơ học tập', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Quản lý tài khoản', exact: true })).toHaveCount(0);
});
test('foreign Unit deep link and network errors show failure without revealing foreign content or fake zeros', async ({ page }) => {
  await teacher(page); await page.goto('/#/statistics?unit=' + foreignUnit);
  await expect(page.getByRole('alert').first()).toBeVisible();
  await expect(page.getByLabel('Tổng quan học liệu', { exact: true })).toHaveCount(0);
  await expect(page.locator('body')).not.toContainText(foreignTitle);
  await page.getByRole('combobox', { name: 'Vòng đấu của tôi', exact: true }).selectOption('');
  await expect(page.getByLabel('Tổng quan học liệu', { exact: true })).toBeVisible();
  await page.route('**/api/teacher/statistics?*', route => route.abort());
  await page.getByRole('button', { name: 'Cập nhật số liệu', exact: true }).click();
  await expect(page.getByRole('alert').first()).toBeVisible();
  await expect(page.getByLabel('Tổng quan học liệu', { exact: true })).toHaveCount(0);
  await page.unroute('**/api/teacher/statistics?*');
  await page.getByRole('button', { name: 'Thử lại thống kê', exact: true }).first().click();
  await expect(page.getByLabel('Tổng quan học liệu', { exact: true })).toBeVisible();
});
