import { test, expect } from '@playwright/test';
import type { APIRequestContext, Browser, BrowserContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test.setTimeout(60000);
const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5174', stamp = 'gov-' + Date.now(), password = 'GovernanceTest@12345';
const users = { a: { email: stamp + '-a@test.local', fullName: 'Giáo viên A ' + stamp, id: 0 }, b: { email: stamp + '-b@test.local', fullName: 'Giáo viên B ' + stamp, id: 0 }, student: { email: stamp + '-s@test.local', fullName: 'Học viên ' + stamp, id: 0 } };
const contexts: BrowserContext[] = [], roots: { kind: string; id: number }[] = [];
async function write(api: APIRequestContext, path: string, data: unknown, method = 'POST', status = 200) {
  const csrf = await (await api.get('/api/auth/csrf')).json();
  const response = await api.fetch(path, { method, data, headers: { [csrf.headerName]: csrf.token } });
  expect(response.status(), await response.text()).toBe(status); return response.json();
}
async function login(api: APIRequestContext, who: keyof typeof users | 'admin') {
  return write(api, '/api/auth/login', who === 'admin' ? { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD } : { email: users[who].email, password });
}
async function actor(browser: Browser, who: keyof typeof users | 'admin') {
  const context = await browser.newContext({ baseURL }); contexts.push(context); await login(context.request, who);
  return { page: await context.newPage(), api: context.request };
}
test.beforeAll(async ({ request }) => {
  expect(new URL(baseURL).port).not.toBe('15183'); await login(request, 'admin');
  for (const [kind, user] of Object.entries(users)) {
    const created = await write(request, '/api/auth/register', { email: user.email, password, fullName: user.fullName }, 'POST', 201); user.id = created.id;
    if (kind !== 'student') await write(request, '/api/admin/users/' + created.id + '/role', { role: 'TEACHER' }, 'PATCH');
  }
});
test.afterEach(async () => { for (const context of contexts.splice(0)) await context.close(); });
test.afterAll(async ({ request }) => {
  await login(request, 'admin');
  for (const kind of ['lessons', 'questions', 'materials']) for (const row of roots.filter(r => r.kind === kind)) {
    const root = await (await request.get('/api/author/' + kind + '/' + row.id)).json();
    if (root.status === 'PUBLISHED') await write(request, '/api/author/' + kind + '/' + row.id + '/retire', { requestId: randomUUID(), baseRevision: root.rootRevision });
  }
});
async function catalog(api: APIRequestContext, assigned = users.a.id) {
  const code = 'gov-' + randomUUID();
  const topic = await write(api, '/api/author/topics', { requestId: randomUUID(), code, name: code, description: '' }, 'POST', 201);
  const objective = await write(api, '/api/author/objectives', { requestId: randomUUID(), code, topicId: topic.id, name: 'Mục tiêu ' + code, description: '' }, 'POST', 201);
  const unit = await write(api, '/api/author/units', { requestId: randomUUID(), topicId: topic.id, title: 'Vòng ' + code, description: '', stage: 1, sortOrder: 0, assignedTeacherId: assigned }, 'POST', 201);
  return { topic, objective, unit };
}
type Catalog = Awaited<ReturnType<typeof catalog>>;
async function question(api: APIRequestContext, cat: Catalog, index: number, materialVersionId: number | null = null) {
  const root = await write(api, '/api/author/questions', { requestId: randomUUID(), type: 'SINGLE_CHOICE', topicId: cat.topic.id, objectiveId: cat.objective.id, difficulty: 'EASY', prompt: 'Question ' + index + ' ' + cat.topic.code, explanation: 'Seven is correct.', hint: 'Look for seven.', materialVersionId,
    content: { options: [{ id: 'a', text: 'Seven' }, { id: 'b', text: 'Eight' }] }, answerKey: { correctOptionId: 'a' }, source: { label: 'Self authored test', url: null, license: null } }, 'POST', 201);
  roots.push({ kind: 'questions', id: root.id }); return root;
}
async function lesson(api: APIRequestContext, cat: Catalog, title: string, materialVersionId: number | null = null) {
  const questions = []; for (let i = 0; i < 3; i++) questions.push(await question(api, cat, i, materialVersionId));
  const unit = await (await api.get('/api/author/units/' + cat.unit.id)).json();
  const root = await write(api, '/api/author/lessons', { requestId: randomUUID(), unitId: cat.unit.id, unitBaseRevision: unit.unit.revision, title, description: '', instructions: [{ kind: 'TEXT', text: 'Choose the correct answer.' }], objectiveIds: [cat.objective.id], questions: questions.map((q, i) => ({ questionVersionId: q.draftVersionId, practiceRole: ['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT'][i], position: 0 })) }, 'POST', 201);
  roots.push({ kind: 'lessons', id: root.id }); return root;
}
async function submit(api: APIRequestContext, kind: string, id: number) {
  const preview = await (await api.get('/api/author/' + kind + '/' + id + '/preview')).json();
  return write(api, '/api/author/' + kind + '/' + id + '/submit-review', { requestId: randomUUID(), draftVersionId: preview.draftVersionId, baseRevision: preview.baseRevision, previewToken: preview.previewToken }, 'POST', 201);
}

test('admin assigns a round in the UI and teachers see only their assigned curriculum', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'), b = await actor(browser, 'b'); const cat = await catalog(admin.api);
  await admin.page.goto('/#/content?catalogEditor=' + cat.unit.id);
  await expect(admin.page.getByRole('combobox', { name: 'Giáo viên phụ trách', exact: true })).toHaveValue(String(users.a.id));
  await admin.page.getByRole('combobox', { name: 'Giáo viên phụ trách', exact: true }).selectOption(String(users.b.id));
  await admin.page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click(); await expect(admin.page.getByRole('status').filter({ hasText: 'Đã lưu' })).toBeVisible();
  await a.page.goto('/#/content'); await expect(a.page.getByRole('heading', { name: cat.unit.title, exact: true })).toHaveCount(0);
  await b.page.goto('/#/content'); await expect(b.page.getByRole('heading', { name: cat.unit.title, exact: true })).toBeVisible();
  await expect(b.page.getByRole('button', { name: '+ Thêm vòng đấu', exact: true })).toHaveCount(0);
  await expect(b.page.getByRole('button', { name: 'Sửa ' + cat.unit.title, exact: true })).toHaveCount(0);
  await expect(b.page.locator('.catalog-card').filter({ hasText: cat.unit.title })).toContainText('Bạn phụ trách vòng này');
});

test('teacher submits a lesson in the editor and only admin approval exposes it to students', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'), student = await actor(browser, 'student'); const cat = await catalog(admin.api);
  const title = 'Bài chờ duyệt ' + cat.topic.code, root = await lesson(a.api, cat, title);
  await a.page.goto('/#/content?area=lessons&item=' + root.id);
  await a.page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await a.page.getByRole('button', { name: 'Gửi duyệt bài học', exact: true }).click();
  await expect(a.page.getByRole('status')).toContainText('Đã gửi duyệt'); await expect(a.page.getByLabel('Tên bài học', { exact: true })).toHaveCount(0);
  expect((await student.api.get('/api/lessons/' + root.id)).status()).toBe(404);
  const pending = await (await a.api.get('/api/author/lessons/' + root.id)).json();
  await admin.page.goto('/#/reviews?review=' + pending.review.id); await expect(admin.page.locator('.review-detail > h2')).toHaveText(title);
  await admin.page.getByRole('button', { name: 'Duyệt và công bố', exact: true }).click(); await expect(admin.page.getByRole('status')).toContainText('Đã duyệt và công bố');
  const visible = await student.api.get('/api/lessons/' + root.id); expect(visible.status()).toBe(200); expect((await visible.json()).lesson.title).toBe(title);
  await a.page.reload(); await expect(a.page.getByText('Đã duyệt', { exact: true })).toBeVisible();
});

test('admin returns with a required reason and the teacher can edit and resubmit without rewriting history', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'); const cat = await catalog(admin.api), root = await lesson(a.api, cat, 'Bài cần sửa ' + cat.topic.code), pending = await submit(a.api, 'lessons', root.id);
  const before = await (await admin.api.get('/api/author/reviews/' + pending.review.id)).json();
  await admin.page.goto('/#/reviews?review=' + pending.review.id); await admin.page.getByRole('button', { name: 'Trả lại để chỉnh sửa', exact: true }).click(); await expect(admin.page.getByRole('alert')).toContainText('Cần ghi lý do');
  await admin.page.getByLabel('Nhận xét / lý do trả lại', { exact: true }).fill('Bổ sung hướng dẫn dễ hiểu hơn.'); await admin.page.getByRole('button', { name: 'Trả lại để chỉnh sửa', exact: true }).click(); await expect(admin.page.getByRole('status')).toContainText('Đã trả lại');
  await a.page.goto('/#/content?area=lessons&item=' + root.id); await expect(a.page.getByText('Bổ sung hướng dẫn dễ hiểu hơn.', { exact: true })).toBeVisible();
  await a.page.getByLabel('Tên bài học', { exact: true }).fill('Bài đã sửa ' + cat.topic.code); await a.page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã lưu bản nháp');
  await a.page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await a.page.getByRole('button', { name: 'Gửi duyệt bài học', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã gửi duyệt');
  const current = await (await a.api.get('/api/author/lessons/' + root.id)).json(); expect(current.review.id).not.toBe(pending.review.id);
  const old = await (await admin.api.get('/api/author/reviews/' + pending.review.id)).json(); expect(old.snapshot).toEqual(before.snapshot); expect(old.review.state).toBe('RETURNED');
  await a.page.setViewportSize({ width: 390, height: 844 }); await a.page.goto('/#/reviews?review=' + current.review.id); expect(await a.page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
});

test('lost submit acknowledgement replays one request and withdrawal unlocks the draft', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'); const cat = await catalog(admin.api), q = await question(a.api, cat, 9);
  let calls = 0, original = '';
  await a.page.route('**/api/author/questions/' + q.id + '/submit-review', async route => {
    const body = route.request().postDataJSON(); calls++;
    if (calls === 1) { original = body.requestId; expect((await route.fetch()).status()).toBe(201); return route.abort('failed'); }
    expect(body.requestId).toBe(original); return route.continue();
  });
  await a.page.goto('/#/content?area=questions&item=' + q.id); await a.page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await a.page.getByRole('button', { name: 'Gửi duyệt câu hỏi', exact: true }).click();
  await expect(a.page.getByRole('alert')).toContainText('Chưa kết nối được máy chủ'); await a.page.getByRole('button', { name: 'Thử lại thao tác', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã gửi duyệt'); expect(calls).toBe(2);
  const rows = await (await a.api.get('/api/author/reviews?state=ALL&size=50')).json(); const matches = rows.items.filter((r: { resourceId: number; kind: string }) => r.resourceId === q.id && r.kind === 'QUESTION'); expect(matches).toHaveLength(1);
  await a.page.getByRole('button', { name: 'Xem yêu cầu duyệt →', exact: true }).click(); await a.page.getByRole('button', { name: 'Rút yêu cầu', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã rút yêu cầu');
  await a.page.getByRole('button', { name: 'Mở nội dung hiện tại →', exact: true }).click(); await expect(a.page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeVisible();
});

test('new assignee can hydrate private lesson dependencies without getting author edit rights', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'), b = await actor(browser, 'b'); const cat = await catalog(admin.api);
  const material = await write(a.api, '/api/author/materials', { requestId: randomUUID(), kind: 'READING', title: 'Nguồn riêng', readingText: 'Linh gets up at seven.', assetId: null, altText: null, source: { label: 'Self authored', url: null, license: null } }, 'POST', 201); roots.push({ kind: 'materials', id: material.id });
  const root = await lesson(a.api, cat, 'Bài tiếp quản ' + cat.topic.code, material.draftVersionId);
  await admin.page.goto('/#/content?catalogEditor=' + cat.unit.id); await admin.page.getByRole('combobox', { name: 'Giáo viên phụ trách', exact: true }).selectOption(String(users.b.id)); await admin.page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click(); await expect(admin.page.getByRole('status').filter({ hasText: 'Đã lưu' })).toBeVisible();
  expect((await a.api.get('/api/author/lessons/' + root.id)).status()).toBe(404);
  await b.page.goto('/#/content?area=lessons&item=' + root.id); await expect(b.page.getByLabel('Tên bài học', { exact: true })).toHaveValue(root.draft.title);
  await b.page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(b.page.locator('.material-preview').last()).toContainText('Linh gets up at seven.');
  await expect(b.page.getByRole('button', { name: 'Gửi duyệt bài học', exact: true })).toBeEnabled();
  const snapshot = await (await b.api.get('/api/author/lessons/' + root.id)).json(); expect(snapshot.canEdit).toBe(true);
  const referenced = await (await b.api.get('/api/author/versions/questions/' + root.draft.questions[0].questionVersionId)).json();
  expect((await b.api.get('/api/author/questions/' + referenced.questionId)).status()).toBe(404);
});

test('a published lesson keeps its old learner version until the revised draft is approved', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'), student = await actor(browser, 'student'); const cat = await catalog(admin.api), root = await lesson(a.api, cat, 'Bản cũ ' + cat.topic.code), pending = await submit(a.api, 'lessons', root.id);
  await write(admin.api, '/api/author/reviews/' + pending.review.id + '/approve', { requestId: randomUUID(), baseRevision: 0, note: '' });
  const old = await (await student.api.get('/api/lessons/' + root.id)).json();
  await a.page.goto('/#/content?area=lessons&item=' + root.id); await a.page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true }).click(); await a.page.getByLabel('Tên bài học', { exact: true }).fill('Bản mới ' + cat.topic.code);
  await a.page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã lưu bản nháp'); await a.page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await a.page.getByRole('button', { name: 'Gửi duyệt bài học', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã gửi duyệt');
  expect(await (await student.api.get('/api/lessons/' + root.id)).json()).toEqual(old);
  const revised = await (await a.api.get('/api/author/lessons/' + root.id)).json(); await admin.page.goto('/#/reviews?review=' + revised.review.id); await admin.page.getByRole('button', { name: 'Duyệt và công bố', exact: true }).click(); await expect(admin.page.getByRole('status')).toContainText('Đã duyệt và công bố');
  expect((await (await student.api.get('/api/lessons/' + root.id)).json()).lesson.title).toBe('Bản mới ' + cat.topic.code);
});

test('review history remains readable after a withdrawn draft is discarded and the current-content link stays usable', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'); const cat = await catalog(admin.api), q = await question(a.api, cat, 10), pending = await submit(a.api, 'questions', q.id);
  const before = await (await a.api.get('/api/author/reviews/' + pending.review.id)).json();
  await write(a.api, '/api/author/reviews/' + pending.review.id + '/withdraw', { requestId: randomUUID(), baseRevision: 0, note: '' });
  await a.page.goto('/#/content?area=questions&item=' + q.id); await a.page.getByRole('button', { name: 'Bỏ bản nháp', exact: true }).click(); await a.page.getByRole('button', { name: 'Xác nhận bỏ bản nháp', exact: true }).click();
  await expect(a.page.getByRole('heading', { name: 'Câu hỏi học tập.', exact: true })).toBeVisible();
  await a.page.goto('/#/reviews?review=' + pending.review.id); await expect(a.page.getByText('Đã rút', { exact: true })).toBeVisible();
  const history = await (await a.api.get('/api/author/reviews/' + pending.review.id)).json(); expect(history.snapshot).toEqual(before.snapshot);
  await a.page.getByRole('button', { name: 'Mở nội dung hiện tại →', exact: true }).click(); await expect(a.page.getByText(/Bản nháp đã được bỏ và chưa có bản công bố/)).toBeVisible();
  await a.page.reload(); await expect(a.page.getByRole('heading', { name: 'Nội dung đã bỏ bản nháp', exact: true })).toBeVisible(); await a.page.getByRole('button', { name: 'Về danh sách', exact: true }).click(); await expect(a.page.getByRole('heading', { name: 'Câu hỏi học tập.', exact: true })).toBeVisible();
});

test('visible assignment and review entries guide admin and teacher through the complete workflow', async ({ browser }) => {
  const admin = await actor(browser, 'admin'), a = await actor(browser, 'a'), student = await actor(browser, 'student'); const cat = await catalog(admin.api, users.b.id);
  await admin.page.goto('/#/admin'); await admin.page.getByRole('button', { name: 'Phân công biên soạn', exact: true }).click();
  await expect(admin.page.getByRole('heading', { name: 'Phân công biên soạn.', exact: true })).toBeVisible();
  await admin.page.getByLabel('Tìm học liệu', { exact: true }).fill(cat.unit.title); await admin.page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await admin.page.getByRole('button', { name: 'Phân công ' + cat.unit.title, exact: true }).click();
  await expect(admin.page.getByRole('combobox', { name: 'Giảng viên phụ trách', exact: true })).toHaveValue(String(users.b.id));
  await expect(admin.page.getByLabel('Tên vòng đấu', { exact: true })).toHaveCount(0);
  await admin.page.getByRole('combobox', { name: 'Giảng viên phụ trách', exact: true }).selectOption(String(users.a.id));
  await admin.page.getByRole('button', { name: 'Lưu phân công', exact: true }).click(); await expect(admin.page.getByRole('status').filter({ hasText: 'Đã phân công' })).toBeVisible();
  const after = (await (await admin.api.get('/api/author/units/' + cat.unit.id)).json()).unit;
  expect(after.title).toBe(cat.unit.title); expect(after.roundNumber).toBe(cat.unit.roundNumber); expect(after.topicId).toBe(cat.topic.id); expect(after.assignedTeacherId).toBe(users.a.id);
  const title = 'Luồng giao biên soạn ' + cat.topic.code, root = await lesson(a.api, cat, title);
  await a.page.goto('/#/content'); await expect(a.page.getByRole('heading', { name: 'Vòng được giao.', exact: true })).toBeVisible();
  await expect(a.page.getByRole('button', { name: 'Phân công biên soạn', exact: true })).toHaveCount(0);
  await a.page.getByLabel('Tìm học liệu', { exact: true }).fill(cat.unit.title); await a.page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await a.page.locator('.catalog-card').filter({ hasText: cat.unit.title }).getByRole('button', { name: 'Soạn bài', exact: false }).click();
  await expect(a.page.getByRole('combobox', { name: 'Vòng đấu', exact: true })).toHaveValue(String(cat.unit.id)); await a.page.reload();
  await expect(a.page.getByRole('combobox', { name: 'Vòng đấu', exact: true })).toHaveValue(String(cat.unit.id));
  await a.page.getByRole('button', { name: '+ Thêm bài học', exact: true }).click(); await expect(a.page.getByRole('combobox', { name: 'Vòng đấu của bài học', exact: true })).toHaveValue(String(cat.unit.id));
  await a.page.getByRole('button', { name: 'Về danh sách', exact: true }).click();
  await a.page.getByRole('button', { name: 'Mở ' + title, exact: true }).click(); await a.page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await a.page.getByRole('button', { name: 'Gửi duyệt bài học', exact: true }).click(); await expect(a.page.getByRole('status')).toContainText('Đã gửi duyệt');
  await a.page.getByRole('button', { name: 'Bài gửi duyệt', exact: true }).click(); await expect(a.page.locator('.catalog-card').filter({ hasText: title })).toBeVisible();
  expect((await student.api.get('/api/lessons/' + root.id)).status()).toBe(404);
  await admin.page.getByRole('button', { name: 'Xem bài chờ duyệt', exact: true }).click();
  await admin.page.locator('.catalog-card').filter({ hasText: title }).getByRole('button', { name: /Xem yêu cầu/ }).click();
  await admin.page.getByRole('button', { name: 'Duyệt và công bố', exact: true }).click(); await expect(admin.page.getByRole('status')).toContainText('Đã duyệt và công bố');
  expect((await student.api.get('/api/lessons/' + root.id)).status()).toBe(200);
});
