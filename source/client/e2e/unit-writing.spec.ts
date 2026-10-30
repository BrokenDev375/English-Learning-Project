import { test, expect, type APIRequestContext } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { write, completeAnswers } from './round-fixtures';
import type { Snapshot } from '../src/features/learning/types';

const env = Object.fromEntries(readFileSync('../../.env', 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#') && s.includes('=')).map(s => { const i = s.indexOf('='); return [s.slice(0, i), s.slice(i + 1).replace(/^(["'])(.*)\1$/, '$2')]; }));
let unit: number;
const definition = { title: 'My daily routine', prompt: 'Write about three activities in your day. Include times and use the present simple.', minWords: 80, maxWords: 120, criteria: [{ id: 'content', text: 'Describe three activities and their times', points: 6 }, { id: 'grammar', text: 'Use the present simple correctly', points: 4 }] };
async function login(r: APIRequestContext, kind: 'admin' | 'teacher') { return write(r, '/api/auth/login', { email: kind === 'admin' ? env.BOOTSTRAP_ADMIN_EMAIL : env.DEMO_TEACHER_EMAIL, password: kind === 'admin' ? env.BOOTSTRAP_ADMIN_PASSWORD : env.DEMO_TEACHER_PASSWORD }); }
async function draft(r: APIRequestContext, path: string, body: object) { const csrf = await (await r.get('/api/auth/csrf')).json(); const response = await r.put(path, { headers: { [csrf.headerName]: csrf.token }, data: body }); expect(response.ok(), await response.text()).toBeTruthy(); return response.json(); }
async function publish(r: APIRequestContext) { let t = await (await r.get(`/api/author/units/${unit}/writing`)).json(); t = await draft(r, `/api/author/units/${unit}/writing/draft`, { requestId: randomUUID(), baseRevision: t.revision, definition }); return write(r, `/api/author/units/${unit}/writing/publish`, { requestId: randomUUID(), baseRevision: t.revision }); }
async function student(r: APIRequestContext) {
  const credentials = { email: 'writing-' + randomUUID() + '@test.local', password: 'WritingTest@12345' };
  await write(r, '/api/auth/register', { ...credentials, fullName: 'Writing learner' }); await write(r, '/api/auth/login', credentials);
  const detail = await (await r.get('/api/units/' + unit)).json();
  for (const lesson of detail.lessons) { const s: Snapshot = await write(r, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: lesson.id }); await write(r, `/api/attempts/${s.attemptId}/submit`, { requestId: randomUUID(), baseRevision: s.revision, answers: completeAnswers(s), playerState: s.playerState }); }
  return credentials;
}
test.beforeAll(async ({ playwright, baseURL }) => {
  const admin = await playwright.request.newContext({ baseURL }); await login(admin, 'admin');
  unit = (await (await admin.get('/api/units')).json()).items[0].id;
  const teacher = await playwright.request.newContext({ baseURL }); const me = await login(teacher, 'teacher');
  const u = (await (await admin.get('/api/author/units/' + unit)).json()).unit;
  await draft(admin, '/api/author/units/' + unit, { requestId: randomUUID(), baseRevision: u.revision, topicId: u.topicId, title: u.title, description: u.description, stage: u.stage, sortOrder: u.sortOrder, roundNumber: u.roundNumber, assignedTeacherId: me.id });
  await publish(teacher); await admin.dispose(); await teacher.dispose();
});
test.afterAll(async ({ playwright, baseURL }) => { const r = await playwright.request.newContext({ baseURL }); await login(r, 'admin'); const t = await (await r.get(`/api/author/units/${unit}/writing`)).json(); await write(r, `/api/author/units/${unit}/writing/retire`, { requestId: randomUUID(), baseRevision: t.revision }); await r.dispose(); });

test('teacher defines rubric, learner submits and teacher returns scored feedback with immutable history', async ({ page }) => {
  test.setTimeout(180000);
  await login(page.request, 'teacher'); await page.goto(`/#/content?area=writing&unit=${unit}`);
  await page.getByRole('button', { name: 'Tạo bài viết', exact: true }).click();
  await expect(page.getByLabel('Tên bài viết', { exact: true })).toHaveValue(definition.title);
  await page.getByLabel('Đề bài và hướng dẫn').fill(definition.prompt + '\nInclude a final sentence.');
  await page.getByRole('button', { name: 'Lưu đề viết', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã lưu');
  await page.getByRole('button', { name: 'Công bố đề viết', exact: true }).click(); await expect(page.getByRole('button', { name: 'Ngừng cấp đề viết' })).toBeEnabled();
  const credentials = await student(page.request); await page.goto(`/#/learning?unit=${unit}`); await page.reload();
  await page.getByRole('button', { name: 'Bắt đầu viết', exact: true }).click();
  await page.getByLabel('Bài viết của bạn', { exact: true }).fill('I wake up at seven. I go to school at eight. I play football at five.');
  await page.getByRole('button', { name: 'Lưu bài viết ngay', exact: true }).click();
  await expect(page.getByText('Đã lưu trên máy chủ', { exact: false })).toBeVisible();
  await page.reload(); await expect(page.getByLabel('Bài viết của bạn', { exact: true })).toHaveValue(/I wake up/);
  await page.getByRole('button', { name: 'Nộp bài viết', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài viết', exact: true }).click();
  await expect(page.getByText('Chờ giáo viên chấm', { exact: true })).toBeVisible();
  const rounds = await (await page.request.get('/api/me/rounds')).json(); expect(rounds.items.find((r: { unit: { id: number } }) => r.unit.id === unit).state).toBe('COMPLETED');
  await login(page.request, 'teacher'); await page.goto(`/#/content?area=writing&unit=${unit}`); await page.reload();
  await page.getByRole('button', { name: 'Chấm bài', exact: true }).click();
  await page.getByRole('button').filter({ hasText: 'Writing learner' }).first().click();
  await page.getByRole('checkbox', { name: /Describe three activities/ }).check();
  await page.getByLabel('Nhận xét chung', { exact: true }).fill('Good ideas. Check the verb forms, then revise.');
  await page.getByRole('checkbox', { name: 'Cho phép học sinh sửa và nộp lại' }).check();
  await page.getByRole('button', { name: 'Gửi kết quả chấm', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Kết quả: 6/10 điểm' })).toBeVisible();
  await write(page.request, '/api/auth/login', credentials); await page.goto(`/#/learning?unit=${unit}`); await page.reload();
  await expect(page.getByText('Được phép sửa và nộp lại', { exact: true })).toBeVisible();
  await expect(page.getByText('Good ideas. Check the verb forms, then revise.', { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.writing-panel').screenshot({ path: '../../.test-state/writing-mobile.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByLabel('Bài viết của bạn', { exact: true }).fill('My revised essay. I wake up at seven every day.');
  await page.getByRole('button', { name: 'Nộp bài viết', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài viết', exact: true }).click();
  await expect(page.locator('.writing-history details')).toHaveCount(2);
});
test('writing hub separates authoring and grading and preserves each route on reload and browser history', async ({ page }) => {
  test.setTimeout(90000);
  await login(page.request, 'teacher');
  const reads: string[] = [];
  page.on('request', r => { if (r.method() === 'GET' && /\/api\/author\/(?:units\/\d+\/writing|writing-submissions)/.test(r.url())) reads.push(r.url()); });
  await page.goto(`/#/content?area=writing&unit=${unit}`);
  await expect(page.getByRole('button', { name: 'Tạo bài viết', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Chấm bài', exact: true })).toBeVisible();
  await expect(page.getByLabel('Tên bài viết', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Bài học sinh đã nộp', exact: true })).toHaveCount(0);
  expect(reads).toHaveLength(0);
  await page.locator('.writing-hub').screenshot({ path: '../../.test-state/writing-hub-desktop.png' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.writing-hub').screenshot({ path: '../../.test-state/writing-hub-mobile.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Tạo bài viết', exact: true }).click();
  await expect(page.getByLabel('Tên bài viết', { exact: true })).toHaveValue(definition.title);
  await expect(page.getByRole('heading', { name: 'Bài học sinh đã nộp', exact: true })).toHaveCount(0);
  expect(reads.some(url => url.includes('/writing-submissions'))).toBe(false);
  await page.reload(); await expect(page.getByLabel('Tên bài viết', { exact: true })).toHaveValue(definition.title);
  await page.getByRole('button', { name: '← Chọn chức năng bài viết', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Chấm bài', exact: true })).toBeVisible();
  reads.length = 0;
  await page.getByRole('button', { name: 'Chấm bài', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bài học sinh đã nộp', exact: true })).toBeVisible();
  await expect(page.getByLabel('Tên bài viết', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Cập nhật danh sách bài viết', exact: true })).toBeVisible();
  expect(reads.some(url => /\/units\/\d+\/writing$/.test(url))).toBe(false);
  await page.reload(); await expect(page.getByRole('heading', { name: 'Bài học sinh đã nộp', exact: true })).toBeVisible();
  await page.goBack(); await expect(page.getByRole('button', { name: 'Tạo bài viết', exact: true })).toBeVisible();
  await page.goForward(); await expect(page.getByRole('heading', { name: 'Bài học sinh đã nộp', exact: true })).toBeVisible();
});
test('lost submit acknowledgement survives reload and replays the identical command', async ({ page }) => {
  test.setTimeout(150000); await student(page.request); await page.goto(`/#/learning?unit=${unit}`); await page.getByRole('button', { name: 'Bắt đầu viết', exact: true }).click();
  await page.getByLabel('Bài viết của bạn', { exact: true }).fill('An essay that survives a lost acknowledgement.');
  const sent: string[] = [];
  await page.route(`**/api/me/units/${unit}/writing/submit`, async route => { sent.push(route.request().postData()!); if (sent.length === 1) { const r = await route.fetch(); expect(r.ok()).toBe(true); await route.abort('failed'); } else await route.continue(); });
  await page.getByRole('button', { name: 'Nộp bài viết', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài viết', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại bài viết và đối soát', exact: true })).toBeEnabled();
  await page.reload(); await page.getByRole('button', { name: 'Khôi phục bản tại máy', exact: true }).click();
  await page.getByRole('button', { name: 'Gửi lại bài viết và đối soát', exact: true }).click();
  await expect(page.getByText('Chờ giáo viên chấm', { exact: true })).toBeVisible(); expect(sent).toHaveLength(2); expect(sent[0]).toBe(sent[1]);
  expect((await (await page.request.get(`/api/me/units/${unit}/writing`)).json()).history).toHaveLength(1);
});
test('two tabs require an explicit comparison instead of overwriting the newer draft', async ({ page, context }) => {
  test.setTimeout(150000); await student(page.request); await page.goto(`/#/learning?unit=${unit}`); await page.getByRole('button', { name: 'Bắt đầu viết', exact: true }).click();
  const other = await context.newPage(); await other.goto(`/#/learning?unit=${unit}`); await expect(other.getByLabel('Bài viết của bạn', { exact: true })).toBeVisible();
  await page.getByLabel('Bài viết của bạn', { exact: true }).fill('Newer text from tab one.'); await page.getByRole('button', { name: 'Lưu bài viết ngay', exact: true }).click(); await expect(page.getByText('Đã lưu trên máy chủ', { exact: false })).toBeVisible();
  await other.getByLabel('Bài viết của bạn', { exact: true }).fill('Different text from tab two.'); await other.getByRole('button', { name: 'Lưu bài viết ngay', exact: true }).click();
  await expect(other.getByRole('heading', { name: 'Bản máy chủ đã thay đổi' })).toBeVisible(); await expect(other.locator('.writing-conflict')).toContainText('Newer text from tab one.'); await expect(other.locator('.writing-conflict')).toContainText('Different text from tab two.');
  await other.getByRole('button', { name: 'Dùng bản máy chủ', exact: true }).click(); await expect(other.getByLabel('Bài viết của bạn', { exact: true })).toHaveValue('Newer text from tab one.'); await other.close();
});
