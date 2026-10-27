import { test, expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#') && line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
let email: string, topicId: number, objectiveId: number, unitId: number;
const stamp = Date.now();
const prefix = 'e2e-authoring-' + stamp;
type Root = { id: number; rootRevision: number; draftVersionId: number; originKey: string; draft: Record<string, unknown> & { id: number; revision: number } };
const slots: Root[][] = [[], []];
async function post(request: APIRequestContext, path: string, data: unknown, method = 'POST') {
  const csrf = await (await request.get('/api/auth/csrf')).json() as { token: string; headerName: string };
  const response = await request.fetch(path, { method, headers: { [csrf.headerName]: csrf.token }, data });
  expect(response.ok(), await response.text()).toBeTruthy(); return await response.json();
}
test.beforeAll(async ({ request }) => {
  email = prefix + '@english.local';
  await post(request, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  const user = await post(request, '/api/auth/register', { email, password: 'Teacher@12345', fullName: 'Tác giả kiểm thử câu và bài' });
  await post(request, '/api/admin/users/' + user.id + '/role', { role: 'TEACHER' }, 'PATCH');
  await post(request, '/api/auth/login', { email, password: 'Teacher@12345' });
  topicId = (await post(request, '/api/author/topics', { requestId: randomUUID(), code: prefix, name: 'Chủ đề ' + prefix, description: '' })).id;
  objectiveId = (await post(request, '/api/author/objectives', { requestId: randomUUID(), code: prefix, topicId, name: 'Mục tiêu ' + prefix, description: '' })).id;
  unitId = (await post(request, '/api/author/units', { requestId: randomUUID(), topicId, title: 'Unit ' + prefix, description: '', stage: 1, sortOrder: 0 })).id;
  for (let group = 0; group < 2; group++) for (let i = 0; i < 3; i++) slots[group].push(await post(request, '/api/author/questions', {
    requestId: randomUUID(), type: 'SINGLE_CHOICE', topicId, objectiveId, difficulty: 'EASY', prompt: prefix + '-slot-' + group + '-' + i,
    explanation: 'Explanation must stay out of learner preview.', hint: 'Look at the time.', materialVersionId: null,
    source: { label: 'Kiểm thử tự soạn', url: null, license: null }, content: { options: [{ id: 'a', text: 'Seven' }, { id: 'b', text: 'Eight' }] }, answerKey: { correctOptionId: 'a' },
  }));
});
test.afterAll(async ({ request }) => {
  if (!unitId) return;
  await post(request, '/api/auth/login', { email, password: 'Teacher@12345' });
  const actor = await (await request.get('/api/auth/me')).json();
  expect(actor.email).toBe(email);
  // Retire this run's publications so they do not enter the learner catalog
  // or the reusable question bank. Retain roots/versions for failure analysis.
  for (const resource of ['lessons', 'questions']) {
    const rows = [];
    for (let page = 0; ; page++) {
      const response = await request.get('/api/author/' + resource + '?scope=OWN&status=PUBLISHED&size=50&page=' + page);
      expect(response.status()).toBe(200);
      const bank = await response.json(); rows.push(...bank.items);
      if (page + 1 >= bank.totalPages) break;
    }
    for (const row of rows) {
      expect(row.ownerId).toBe(actor.id);
      const root = await (await request.get('/api/author/' + resource + '/' + row.id)).json();
      if (resource === 'lessons') { expect(root.unitId).toBe(unitId); expect(root.published.title).toMatch(new RegExp('^' + prefix + '-lesson-[01]$')); }
      await post(request, '/api/author/' + resource + '/' + row.id + '/retire', { requestId: randomUUID(), baseRevision: root.rootRevision });
    }
  }
  expect((await request.get('/api/units/' + unitId)).status()).toBe(404);
});
async function login(page: Page, area = 'Câu hỏi', demo = false) {
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(demo ? env.DEMO_TEACHER_EMAIL ?? 'teacher@english.local' : email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(demo ? env.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345' : 'Teacher@12345');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Quản lý học liệu', exact: true }).click(); await page.getByRole('button', { name: area, exact: true }).click();
}
async function newQ(page: Page, type: string, prompt: string) {
  await page.getByRole('button', { name: '+ Thêm câu hỏi', exact: true }).click();
  await page.getByRole('combobox', { name: 'Dạng câu hỏi', exact: true }).selectOption(type);
  await page.getByRole('combobox', { name: 'Chủ đề câu hỏi', exact: true }).selectOption(String(topicId));
  await page.getByRole('combobox', { name: 'Mục tiêu chính', exact: true }).selectOption(String(objectiveId));
  await page.getByRole('textbox', { name: 'Nội dung câu hỏi', exact: true }).fill(prompt);
  await page.getByRole('textbox', { name: 'Giải thích đáp án', exact: true }).fill('This answer follows the sentence.');
}
async function save(page: Page) { await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã lưu bản nháp trên máy chủ.'); }
async function publish(page: Page, kind: string) { await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await page.getByRole('button', { name: 'Xuất bản ' + kind, exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã xuất bản ' + kind); }
async function newLesson(page: Page, group: number) {
  await page.getByRole('button', { name: '+ Thêm bài học', exact: true }).click();
  await page.getByRole('combobox', { name: 'Unit của bài học', exact: true }).selectOption(String(unitId));
  await page.getByRole('textbox', { name: 'Tên bài học', exact: true }).fill(prefix + '-lesson-' + group);
  await page.getByRole('checkbox', { name: 'Mục tiêu ' + prefix, exact: true }).check();
  await page.getByRole('textbox', { name: 'Nội dung khối 1', exact: true }).fill('Read, listen and answer.');
  for (let i = 0; i < 3; i++) { await page.getByRole('combobox', { name: 'Câu hỏi để thêm', exact: true }).selectOption(String(slots[group][i].draftVersionId)); await page.getByRole('combobox', { name: 'Nhóm sử dụng', exact: true }).selectOption(['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT'][i]); await page.getByRole('button', { name: 'Thêm vào bài', exact: true }).click(); }
}

test('single choice retries a lost ACK, publishes and keeps the old version beside its draft', async ({ page }) => {
  await login(page); await newQ(page, 'SINGLE_CHOICE', prefix + '-single');
  await page.getByRole('textbox', { name: 'Lựa chọn 1', exact: true }).fill('Seven'); await page.getByRole('textbox', { name: 'Lựa chọn 2', exact: true }).fill('Eight'); await page.getByRole('radio', { name: 'Đáp án đúng 1', exact: true }).check();
  let calls = 0, key = '';
  await page.route('**/api/author/questions', async route => { if (route.request().method() !== 'POST') return route.continue(); const body = route.request().postDataJSON() as { requestId: string }; calls++; if (calls === 1) { key = body.requestId; const committed = await route.fetch(); expect(committed.status()).toBe(201); return route.abort('failed'); } expect(body.requestId).toBe(key); return route.continue(); });
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Chưa kết nối'); await page.getByRole('button', { name: 'Thử lại thao tác', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã lưu bản nháp'); expect(calls).toBe(2);
  await publish(page, 'câu hỏi'); await expect(page.locator('.author-preview').getByText('This answer follows the sentence.', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true }).click(); await page.getByRole('textbox', { name: 'Nội dung câu hỏi', exact: true }).fill(prefix + '-single-edited'); await save(page);
  await page.getByRole('tab', { name: 'Bản đã xuất bản', exact: true }).click(); await expect(page.locator('.question-presentation legend')).toHaveText(prefix + '-single');
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('tab', { name: 'Bản nháp', exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/module-2-question-editor-mobile.png', fullPage: true });
});
test('fill blank accepts several answers and previews a blank without revealing the key', async ({ page }) => {
  await login(page); await newQ(page, 'FILL_BLANK', prefix + '-fill');
  await page.getByRole('textbox', { name: 'Đoạn chữ 1', exact: true }).fill('She '); await page.getByRole('textbox', { name: 'Đáp án ô trống 1', exact: true }).fill('gets up\nwakes up');
  await save(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await page.getByRole('textbox', { name: 'Trả lời ô trống 1', exact: true }).fill('my answer');
  await expect(page.locator('.author-preview').getByText('gets up', { exact: true })).toHaveCount(0); await page.getByRole('button', { name: 'Xuất bản câu hỏi', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã xuất bản câu hỏi');
});
test('matching editor and learner-style interactions work', async ({ page }) => {
  await login(page); await newQ(page, 'MATCHING', prefix + '-match');
  for (let i = 1; i <= 2; i++) { await page.getByRole('textbox', { name: 'Mục trái ' + i, exact: true }).fill(i === 1 ? 'seven' : 'eight'); await page.getByRole('textbox', { name: 'Mục phải ' + i, exact: true }).fill(i === 1 ? '7' : '8'); await page.getByRole('combobox', { name: 'Ghép mục trái ' + i + ' với', exact: true }).selectOption('right-' + i); }
  await save(page); await publish(page, 'câu hỏi'); await page.locator('.author-preview .matching-left button').first().click(); await page.locator('.author-preview .matching-right button').first().click(); await expect(page.locator('.author-preview .matched-pairs li')).toHaveCount(1);
});
test('ordering editor publishes accepted word orders and offers a safe interactive preview', async ({ page }) => {
  await login(page); await newQ(page, 'ORDERING', prefix + '-ordering');
  await page.getByLabel('Từ hoặc cụm từ 1', { exact: true }).fill('She'); await page.getByLabel('Từ hoặc cụm từ 2', { exact: true }).fill('likes');
  await page.getByRole('button', { name: 'Thêm thẻ từ', exact: true }).click(); await page.getByLabel('Từ hoặc cụm từ 3', { exact: true }).fill('music');
  await save(page); await publish(page, 'câu hỏi');
  const preview = page.locator('.author-preview'); await expect(preview.locator('.word-bank button')).toHaveCount(3);
  await expect(preview).not.toContainText('This answer follows the sentence.');
  for (const word of ['She', 'likes', 'music']) await preview.locator('.word-bank').getByRole('button', { name: word, exact: true }).click();
  await expect(preview.locator('.ordered-words .word-token')).toHaveText(['She', 'likes', 'music']);
});
test('lesson editor previews and publishes its three question drafts together', async ({ page }) => {
  await login(page, 'Bài học'); await newLesson(page, 0); await save(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.locator('.author-preview .question-presentation')).toHaveCount(3); await expect(page.getByText('Explanation must stay out of learner preview.', { exact: true })).toHaveCount(0);
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.screenshot({ path: 'test-results/module-2-lesson-editor-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/module-2-lesson-editor-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Xuất bản bài học', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã xuất bản bài học');
  for (const q of slots[0]) { const root = await (await page.request.get('/api/author/questions/' + q.id)).json(); expect(root.status).toBe('PUBLISHED'); expect(root.draft).toBeNull(); }
});
test('dependency changes reject stale lesson preview and a fresh preview can publish', async ({ page }) => {
  await login(page, 'Bài học'); await newLesson(page, 1); await save(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  const { id: _id, questionId: _parent, versionNo: _number, state: _state, schemaVersion: _schema, revision: _revision, ...data } = slots[1][0].draft;
  await post(page.request, '/api/author/questions/' + slots[1][0].id + '/draft', { ...data, requestId: randomUUID(), draftVersionId: slots[1][0].draftVersionId, baseRevision: slots[1][0].draft.revision, prompt: prefix + '-slot-updated' }, 'PUT');
  await page.getByRole('button', { name: 'Xuất bản bài học', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('đã đổi sau lần xem trước');
  await publish(page, 'bài học');
});
test('unit lesson order can be changed and saved', async ({ page }) => {
  await login(page, 'Bài học'); await page.getByRole('combobox', { name: 'Unit', exact: true }).selectOption(String(unitId)); await page.getByRole('button', { name: 'Sắp xếp bài trong Unit đã chọn →', exact: true }).click();
  await expect(page.locator('.lesson-slot')).toHaveCount(2); await page.getByRole('button', { name: 'Đưa bài 2 lên', exact: true }).click(); await page.getByRole('button', { name: 'Lưu thứ tự bài', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã lưu thứ tự bài học.');
});
test('published question from another author can be copied with the same origin', async ({ page }) => {
  await login(page); await page.getByRole('combobox', { name: 'Phạm vi', exact: true }).selectOption('PUBLISHED'); await page.getByRole('textbox', { name: 'Tìm câu hỏi', exact: true }).fill('What time does Linh get up?'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Mở What time does Linh get up?', exact: true }).click(); await expect(page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Sao chép thành câu của tôi', exact: true }).click(); await expect(page.getByRole('status')).toContainText('giữ nguồn gốc câu'); await expect(page.getByRole('textbox', { name: 'Nội dung câu hỏi', exact: true })).toHaveValue('What time does Linh get up?');
});
test('student sees published lesson introductions without question keys', async ({ page }) => {
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(env.DEMO_STUDENT_EMAIL ?? 'student@english.local'); await page.getByLabel('Mật khẩu', { exact: true }).fill(env.DEMO_STUDENT_PASSWORD ?? 'Student@12345'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click(); const intro = page.waitForResponse('**/api/lessons/*'); await page.getByRole('button', { name: 'Chơi bài 1 · A day with Linh — Morning routine →', exact: true }).click(); expect(await (await intro).text()).not.toMatch(/answerKey|correctOptionId|transcript|materialVersionId/); await expect(page.getByRole('heading', { name: 'Bạn sẽ học gì?', exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false); await page.screenshot({ path: 'test-results/module-2-learner-intro-mobile.png', fullPage: true });
});
test('demo question library and lesson preview fit desktop and mobile', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 }); await login(page, 'Câu hỏi', true); await expect(page.locator('.catalog-card').first()).toBeVisible(); await page.screenshot({ path: 'test-results/module-2-questions-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false); await page.screenshot({ path: 'test-results/module-2-questions-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Bài học', exact: true }).click(); await page.getByRole('button', { name: 'Mở A day with Linh — Morning routine', exact: true }).click(); await expect(page.locator('.author-preview .question-presentation')).toHaveCount(15); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false); await page.screenshot({ path: 'test-results/module-2-lesson-mobile.png', fullPage: true });
});
