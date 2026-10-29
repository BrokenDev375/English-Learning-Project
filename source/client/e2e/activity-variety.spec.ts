import { test, expect, type APIRequestContext } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { provisionActivitySample, sampleQuestions } from './fixtures/activity-sample-data.mjs';
import { completeEarlierRounds, write } from './round-fixtures';
import type { Snapshot } from '../src/features/learning/types';

const env = Object.fromEntries(readFileSync('../../.env', 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#') && s.includes('=')).map(s => [s.slice(0, s.indexOf('=')), s.slice(s.indexOf('=') + 1).replace(/^(["'])(.*)\1$/, '$2')]));
let sample: Awaited<ReturnType<typeof provisionActivitySample>>;
let author: APIRequestContext;
const auxiliaryLessonIds: number[] = [];
const password = 'ActivityTest@12345';

test.beforeAll(async ({ playwright, baseURL }) => {
  author = await playwright.request.newContext({ baseURL });
  await write(author, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  const client = {
    get: async (path: string) => { const r = await author.get(path); expect(r.ok()).toBeTruthy(); return r.json(); },
    action: (_: string, path: string, body: object) => write(author, path, { ...body, requestId: randomUUID() }),
    async upload(_: string, filename: string) {
      const csrf = await (await author.get('/api/auth/csrf')).json();
      const response = await author.post('/api/assets', { headers: { [csrf.headerName]: csrf.token }, multipart: { requestId: randomUUID(), file: { name: filename, mimeType: 'audio/wav', buffer: readFileSync('../../source/server/src/main/resources/demo/materials/' + filename) } } });
      expect(response.ok(), await response.text()).toBeTruthy(); return response.json();
    },
    async publish(kind: string, id: number) { const p = await this.get(`/api/author/${kind}/${id}/preview`); expect(p.errors).toEqual([]); return write(author, `/api/author/${kind}/${id}/publish`, { requestId: randomUUID(), baseRevision: p.baseRevision, draftVersionId: p.draftVersionId, previewToken: p.previewToken }); }
  };
  sample = await provisionActivitySample(client, 'activity-e2e-' + Date.now());
});
test.afterAll(async () => {
  for (const id of auxiliaryLessonIds) { const root = await (await author.get('/api/author/lessons/' + id)).json(); await write(author, `/api/author/lessons/${id}/retire`, { requestId: randomUUID(), baseRevision: root.rootRevision }); }
  if (sample && author) { const root = await (await author.get('/api/author/lessons/' + sample.lessonId)).json(); await write(author, `/api/author/lessons/${sample.lessonId}/retire`, { requestId: randomUUID(), baseRevision: root.rootRevision }); }
  await author?.dispose();
});

async function student(request: APIRequestContext) {
  const email = 'activity-' + randomUUID() + '@test.local';
  await write(request, '/api/auth/register', { email, password, fullName: 'Activity learner' });
  await write(request, '/api/auth/login', { email, password });
  await completeEarlierRounds(request, sample.unitId);
  return write(request, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: sample.lessonId }) as Promise<Snapshot>;
}
function correct(s: Snapshot) {
  return s.items.map(item => {
    const key = Object.keys(sample.questionVersions).find(k => sample.questionVersions[k] === item.questionVersionId)!;
    const spec = sampleQuestions.find((q: { key: string }) => q.key === key)!;
    const a = spec.answerKey;
    const response = spec.type === 'SINGLE_CHOICE' ? { type: spec.type, optionId: a.correctOptionId } : spec.type === 'FILL_BLANK' ? { type: spec.type, values: a.blanks!.map((b: { blankId: string; acceptedAnswers: string[] }) => ({ blankId: b.blankId, value: b.acceptedAnswers[0] })) } : spec.type === 'MATCHING' ? { type: spec.type, pairs: a.pairs } : { type: spec.type, tokenIds: a.acceptedOrders![0] };
    return { attemptQuestionId: item.attemptQuestionId, response };
  });
}
async function read(request: APIRequestContext, id: number): Promise<Snapshot> { return (await request.get('/api/attempts/' + id)).json(); }

test('18 questions keep activity order, inline drafts and immutable results across reload and correction', async ({ page }) => {
  test.setTimeout(120000);
  const s = await student(page.request);
  expect(s.items).toHaveLength(18);
  expect(s.activities?.map(a => a.kind)).toEqual(['CHOICE', 'MATCHING', 'ORDERING', 'FILL', 'READING_CLOZE', 'READING_CHOICE', 'LISTENING_CHOICE']);
  for (const a of s.activities!) { const items = a.attemptQuestionIds.map(id => s.items.find(i => i.attemptQuestionId === id)!); expect(items[0].practiceRole).toBe('GUIDED'); expect(items.slice(1).every(i => i.practiceRole === 'INDEPENDENT')).toBe(true); }
  expect(JSON.stringify(s)).not.toMatch(/answerKey|correctOptionId|acceptedOrders|acceptedAnswers|explanation|transcript/);
  const snapshotBytes = Buffer.byteLength(JSON.stringify(s));
  expect(snapshotBytes).toBeLessThan(128 * 1024);
  const readStarted = performance.now(); await read(page.request, s.attemptId);
  console.log(`18-question snapshot: ${snapshotBytes} bytes; one local GET ${(performance.now() - readStarted).toFixed(1)} ms (not a load benchmark).`);
  const answers = correct(s);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const saved = await page.request.put(`/api/attempts/${s.attemptId}/draft`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: s.revision, answers: answers.map((a, i) => i >= 10 && i <= 12 ? { ...a, response: null } : a), playerState: { ...s.playerState, currentAttemptQuestionId: s.items[10].attemptQuestionId } } });
  expect(saved.ok(), await saved.text()).toBeTruthy();
  await page.goto('/#/learning?attempt=' + s.attemptId);
  await expect(page.locator('.question-nav button')).toHaveCount(18);
  await expect(page.getByRole('navigation', { name: 'Chọn trò chơi' }).getByRole('button')).toHaveCount(7);
  await page.getByLabel('Điền từ câu 11', { exact: true }).fill('umbrella');
  await page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true }).click();
  await expect(page.locator('.learning-panel .message.success')).toContainText('Đúng');
  await page.getByRole('navigation', { name: 'Các câu trong lượt làm', exact: true }).getByRole('button').nth(11).click();
  for (const [number, word] of [[12, 'bus'], [13, 'library']] as const) await page.getByLabel('Điền từ câu ' + number, { exact: true }).fill(word);
  await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ', { timeout: 20000 });
  await page.reload();
  for (const [number, word] of [[12, 'bus'], [13, 'library']] as const) await expect(page.getByLabel('Điền từ câu ' + number, { exact: true })).toHaveValue(word);
  await page.locator('.runner-main').screenshot({ path: '../../.test-state/activity-desktop.png' });
  await page.getByRole('navigation', { name: 'Các câu trong lượt làm', exact: true }).getByRole('button').nth(13).click();
  const reading = page.locator('.reading-choice-layout .learning-material details');
  await expect(reading).toHaveAttribute('open', '');
  await reading.getByText('Đọc đoạn văn · Thu gọn / Mở lại', { exact: true }).click();
  await expect(reading).not.toHaveAttribute('open', '');
  await reading.getByText('Đọc đoạn văn · Thu gọn / Mở lại', { exact: true }).click();
  await expect(reading).toHaveAttribute('open', '');
  await page.getByRole('navigation', { name: 'Các câu trong lượt làm', exact: true }).getByRole('button').nth(17).click();
  const audio = page.locator('.runner-main audio');
  await expect(audio).toBeVisible();
  await expect.poll(() => audio.evaluate((a: HTMLAudioElement) => a.readyState)).toBeGreaterThan(0);
  const media = s.materials.find(m => m.kind === 'AUDIO')!;
  expect((await page.request.get(media.asset!.url, { headers: { Range: 'bytes=0-15' } })).status()).toBe(206);
  await audio.evaluate(async (a: HTMLAudioElement) => { a.currentTime = 2; await a.play(); });
  await expect.poll(() => audio.evaluate((a: HTMLAudioElement) => a.currentTime)).toBeGreaterThan(2);
  await audio.evaluate((a: HTMLAudioElement) => a.pause());
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click();
  await expect(page.locator('.result-score')).toContainText('18/18 câu đúng');
  const before = await (await page.request.get(`/api/attempts/${s.attemptId}/result`)).json();
  await page.getByRole('button', { name: 'Chữa câu 12', exact: true }).click();
  await page.getByLabel('Điền từ câu 12', { exact: true }).fill('wrong');
  await page.getByRole('button', { name: 'Kiểm tra bài chữa', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Bài chữa:' })).toContainText('Chưa đúng');
  expect(await (await page.request.get(`/api/attempts/${s.attemptId}/result`)).json()).toEqual(before);
});

test('mobile inline fields survive a concurrent server draft and explicit revision conflict', async ({ page }) => {
  test.setTimeout(120000);
  await page.setViewportSize({ width: 390, height: 844 });
  const s = await student(page.request);
  await page.goto('/#/learning?attempt=' + s.attemptId);
  await page.getByRole('navigation', { name: 'Các câu trong lượt làm', exact: true }).getByRole('button').nth(11).click();
  let release!: () => void, reached!: () => void;
  const gate = new Promise<void>(r => { release = r; }); const pending = new Promise<void>(r => { reached = r; });
  await page.route(`**/api/attempts/${s.attemptId}/draft`, async route => { reached(); await gate; await route.continue(); });
  await page.getByLabel('Điền từ câu 12', { exact: true }).fill('local-bus');
  await pending;
  // Hold the browser save while an independent HTTP request commits the other draft.
  const current = await read(page.request, s.attemptId);
  const remote = correct(current); const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const r = await page.request.put(`/api/attempts/${s.attemptId}/draft`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: current.revision, answers: remote, playerState: { ...current.playerState, currentAttemptQuestionId: s.items[11].attemptQuestionId } } });
  expect(r.ok(), await r.text()).toBeTruthy();
  release();
  await expect(page.getByRole('heading', { name: 'Đối chiếu bài làm', exact: true })).toBeVisible({ timeout: 20000 });
  await expect(page.getByLabel('Điền từ câu 12', { exact: true })).toHaveValue('local-bus');
  page.once('dialog', d => d.accept());
  await page.getByRole('button', { name: 'Dùng bản máy chủ', exact: true }).click();
  await expect(page.getByLabel('Điền từ câu 12', { exact: true })).toHaveValue('bus');
  await expect(page.getByLabel('Điền từ câu 13', { exact: true })).toHaveValue('library');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '../../.test-state/activity-mobile.png', fullPage: true });
});

test('lost submit response replays the same 18-answer command after the server commits', async ({ page }) => {
  test.setTimeout(120000);
  const s = await student(page.request); const answers = correct(s);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.put(`/api/attempts/${s.attemptId}/draft`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: s.revision, answers, playerState: s.playerState } })).ok()).toBe(true);
  await page.goto('/#/learning?attempt=' + s.attemptId);
  await expect(page.locator('.question-nav button')).toHaveCount(18);
  const sent: string[] = []; let committed: unknown;
  await page.route(`**/api/attempts/${s.attemptId}/submit`, async route => {
    sent.push(route.request().postData()!);
    if (sent.length === 1) { const response = await route.fetch(); expect(response.ok()).toBe(true); committed = await response.json(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true }).click();
  await expect(page.locator('.result-score')).toContainText('18/18 câu đúng');
  expect(sent).toHaveLength(2); expect(sent[1]).toBe(sent[0]);
  expect(await (await page.request.get(`/api/attempts/${s.attemptId}/result`)).json()).toEqual(committed);
});

test('author configures all seven activities and previews inline blanks through the editor', async ({ page }) => {
  test.setTimeout(120000);
  await write(page.request, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  await page.goto('/#/content?area=lessons&item=new');
  await page.getByRole('combobox', { name: 'Unit của bài học', exact: true }).selectOption(String(sample.unitId));
  await page.getByLabel('Tên bài học', { exact: true }).fill('Activity editor preview');
  await page.getByRole('checkbox', { name: 'Hiểu và sử dụng tiếng Anh trong sinh hoạt', exact: true }).check();
  await page.getByLabel('Nội dung khối 1', { exact: true }).fill('Read and answer each activity.');
  await page.getByRole('button', { name: 'Thiết lập bảy hoạt động', exact: true }).click();
  let offset = 0;
  for (const [section, count] of [3, 2, 2, 3, 3, 3, 2].entries()) {
    const card = page.locator('.activity-editor').nth(section);
    for (let at = 0; at < count; at++) await card.locator('.catalog-fields select').nth(at).selectOption(String(sample.questionVersions[sampleQuestions[offset + at].key]));
    offset += count;
  }
  await page.getByRole('button', { name: 'Tạo ánh xạ ô trống', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã ánh xạ 3 ô.' })).toBeVisible();
  await page.getByRole('combobox', { name: 'Câu hỏi để thêm', exact: true }).selectOption(String(sample.questionVersions['review-choice']));
  await page.getByRole('button', { name: 'Thêm vào bài', exact: true }).click();
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.locator('.message.success[role="status"]')).toContainText('Đã lưu bản nháp');
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.locator('.material-preview .inline-reading input')).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Xuất bản bài học', exact: true })).toBeEnabled();
  await page.locator('.material-preview').screenshot({ path: '../../.test-state/activity-author-preview.png' });
});

test('author omits optional kinds and publishes a fifteen-question lesson with custom counts', async ({ page }) => {
  test.setTimeout(120000);
  await write(page.request, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  await page.goto('/#/content?area=lessons&item=new');
  await page.getByRole('combobox', { name: 'Unit của bài học', exact: true }).selectOption(String(sample.unitId));
  await page.getByLabel('Tên bài học', { exact: true }).fill('Flexible fifteen questions');
  await page.getByRole('checkbox', { name: 'Hiểu và sử dụng tiếng Anh trong sinh hoạt', exact: true }).check();
  await page.getByLabel('Nội dung khối 1', { exact: true }).fill('Choose the activities for this lesson.');
  await page.getByRole('button', { name: 'Thiết lập bảy hoạt động', exact: true }).click();
  await page.getByRole('button', { name: 'Bỏ phần Ghép cặp', exact: true }).click();
  await page.getByRole('button', { name: 'Bỏ phần Sắp xếp từ', exact: true }).click();
  await page.getByLabel('Số câu phần Trắc nghiệm', { exact: true }).selectOption('4', { timeout: 10000 });
  const keys = [
    ['choice-guide', 'choice-time', 'choice-place', 'review-choice'],
    ['fill-guide', 'fill-days', 'fill-verb'],
    ['cloze-guide', 'cloze-bus', 'cloze-library'],
    ['reading-guide', 'reading-purpose', 'reading-schedule'],
    ['listening-guide', 'listening-main']
  ];
  for (const [section, ids] of keys.entries()) {
    const card = page.locator('.activity-editor').nth(section);
    for (const [at, key] of ids.entries()) await card.locator('.catalog-fields select').nth(at).selectOption(String(sample.questionVersions[key]));
  }
  await expect(page.getByRole('status').filter({ hasText: 'Đang phân bổ 15 câu' })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo ánh xạ ô trống', exact: true }).click();
  await page.getByRole('combobox', { name: 'Câu hỏi để thêm', exact: true }).selectOption(String(sample.questionVersions['review-fill']));
  await page.getByRole('button', { name: 'Thêm vào bài', exact: true }).click();
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/author/lessons');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  const created = await (await response).json();
  auxiliaryLessonIds.push(created.id);
  const preview = await (await author.get(`/api/author/lessons/${created.id}/preview`)).json();
  expect(preview.errors).toEqual([]);
  await write(author, `/api/author/lessons/${created.id}/publish`, { requestId: randomUUID(), baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken });
  await student(page.request);
  const s = await write(page.request, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: created.id }) as Snapshot;
  expect(s.items).toHaveLength(15);
  expect(s.activities?.map(a => a.kind)).toEqual(['CHOICE', 'FILL', 'READING_CLOZE', 'READING_CHOICE', 'LISTENING_CHOICE']);
  expect(s.activities?.map(a => a.attemptQuestionIds.length)).toEqual([4, 3, 3, 3, 2]);
  await page.goto('/#/learning?attempt=' + s.attemptId);
  // Switching accounts through API changes the cookie; reload React's in-memory user.
  await page.reload();
  await expect(page.locator('.question-nav button')).toHaveCount(15);
  await expect(page.getByRole('navigation', { name: 'Chọn trò chơi' }).getByRole('button')).toHaveCount(5);
  const result = await write(page.request, `/api/attempts/${s.attemptId}/submit`, { requestId: randomUUID(), baseRevision: s.revision, answers: correct(s), playerState: s.playerState });
  expect(result.totalQuestions).toBe(15);
  expect(result.correctCount).toBe(15);
});

test('publication rejects a foreign inline mapping and read API keeps old pinned layout', async ({ page }) => {
  test.setTimeout(120000);
  const s = await student(page.request);
  const root = await (await author.get('/api/author/lessons/' + sample.lessonId)).json();
  const draftRoot = await write(author, `/api/author/lessons/${root.id}/draft`, { requestId: randomUUID(), baseRevision: root.rootRevision });
  const d = draftRoot.draft;
  d.activities[4].passages[1].parts.find((p: { kind: string }) => p.kind === 'BLANK').questionVersionId = d.activities[0].questionVersionIds[0];
  const csrf = await (await author.get('/api/auth/csrf')).json();
  const response = await author.put(`/api/author/lessons/${root.id}/draft`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), draftVersionId: d.id, baseRevision: d.revision, title: d.title, description: d.description, instructions: d.instructions, objectiveIds: d.objectiveIds, questions: d.questions, activities: d.activities } });
  expect(response.ok()).toBeTruthy();
  const preview = await (await author.get(`/api/author/lessons/${root.id}/preview`)).json();
  expect(preview.errors.some((e: { code: string }) => e.code === 'INVALID_ACTIVITY_LAYOUT')).toBe(true);
  const denied = await author.post(`/api/author/lessons/${root.id}/publish`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken } });
  expect(denied.status()).toBe(400);
  const current = await (await author.get('/api/author/lessons/' + sample.lessonId)).json();
  const restored = await author.put(`/api/author/lessons/${root.id}/draft`, { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), draftVersionId: current.draft.id, baseRevision: current.draft.revision, title: d.title + ' — cập nhật', description: d.description, instructions: d.instructions, objectiveIds: d.objectiveIds, questions: d.questions, activities: root.published.activities } });
  expect(restored.ok(), await restored.text()).toBeTruthy();
  const nextPreview = await (await author.get(`/api/author/lessons/${root.id}/preview`)).json(); expect(nextPreview.errors).toEqual([]);
  const published = await write(author, `/api/author/lessons/${root.id}/publish`, { requestId: randomUUID(), baseRevision: nextPreview.baseRevision, draftVersionId: nextPreview.draftVersionId, previewToken: nextPreview.previewToken });
  const pinned = await read(page.request, s.attemptId);
  expect(pinned.items).toEqual(s.items); expect(pinned.activities).toEqual(s.activities);
  expect(pinned.lessonVersionId).toBe(s.lessonVersionId); expect(pinned.lesson?.lesson.title).toBe(s.lesson?.lesson.title);
  const fresh = await student(page.request);
  expect(fresh.lessonVersionId).toBe(published.publishedVersionId); expect(fresh.lessonVersionId).not.toBe(s.lessonVersionId);
});
