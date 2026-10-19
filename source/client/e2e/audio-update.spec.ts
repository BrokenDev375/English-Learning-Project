import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test.setTimeout(60000);
const key = () => randomUUID();
const source = { label: 'Audio update browser test', url: null, license: 'Local testing only' };
const envPath = resolve(process.cwd(), '../../.env');
const local = Object.fromEntries((existsSync(envPath) ? readFileSync(envPath, 'utf8') : '').split(/\r?\n/)
  .filter(line => line && !line.startsWith('#') && line.includes('='))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const cleanup = new WeakMap<Page, { kind: string; id: number }[]>();
test.afterEach(async ({ page }) => {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  for (const root of (cleanup.get(page) ?? []).reverse()) {
    const path = `/api/author/${root.kind}/${root.id}`;
    const current = await (await page.request.get(path)).json();
    if (current.status !== 'PUBLISHED') continue;
    const result = await page.request.post(path + '/retire', { headers: { [csrf.headerName]: csrf.token },
      data: { requestId: key(), baseRevision: current.rootRevision } });
    expect(result.ok(), await result.text()).toBe(true);
  }
});
async function fixture(page: Page) {
  const created: { kind: string; id: number }[] = [];
  cleanup.set(page, created);
  let csrf = await (await page.request.get('/api/auth/csrf')).json();
  const login = await page.request.post('/api/auth/login', { headers: { [csrf.headerName]: csrf.token },
    data: { email: process.env.E2E_TEACHER_EMAIL ?? local.DEMO_TEACHER_EMAIL ?? 'teacher@english.local',
      password: process.env.E2E_TEACHER_PASSWORD ?? local.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345' } });
  expect(login.status(), await login.text()).toBe(200);
  csrf = await (await page.request.get('/api/auth/csrf')).json();
  async function call(path: string, data?: unknown, method = 'POST') {
    const result = data === undefined ? await page.request.get(path) : await page.request.fetch(path, {
      method, data, headers: { [csrf.headerName]: csrf.token },
    });
    expect(result.ok(), await result.text()).toBe(true);
    return result.json();
  }
  async function publish(kind: string, id: number) {
    const preview = await call(`/api/author/${kind}/${id}/preview`);
    expect(preview.errors).toEqual([]);
    return call(`/api/author/${kind}/${id}/publish`, { requestId: key(), draftVersionId: preview.draftVersionId,
      baseRevision: preview.baseRevision, previewToken: preview.previewToken });
  }
  async function upload() {
    const result = await page.request.post('/api/assets', { headers: { [csrf.headerName]: csrf.token }, multipart: {
      requestId: key(), file: { name: 'daily-routine.wav', mimeType: 'audio/wav',
        buffer: readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/materials/daily-routine.wav')) },
    } });
    expect(result.status(), await result.text()).toBe(201);
    return (await result.json()).id as number;
  }
  const topic = await call('/api/author/topics', { requestId: key(), code: 'audio-' + key(), name: 'Audio browser test', description: '' });
  const objective = await call('/api/author/objectives', { requestId: key(), code: 'obj-' + key(), topicId: topic.id, name: 'Listen for time', description: '' });
  const unit = await call('/api/author/units', { requestId: key(), topicId: topic.id, title: 'Audio browser unit', description: '', stage: 1, sortOrder: 0 });
  const audio = { kind: 'AUDIO', title: 'Audio browser ' + key(), assetId: await upload(), transcript: 'Linh gets up at seven.', startSeconds: 0, endSeconds: null, source };
  let material = await call('/api/author/materials', { requestId: key(), ...audio });
  created.push({ kind: 'materials', id: material.id });
  material = await publish('materials', material.id);
  const oldAudio = material.publishedVersionId;
  const questions = [];
  for (const [index, role] of ['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT'].entries()) {
    const q = await call('/api/author/questions', { requestId: key(), type: 'SINGLE_CHOICE', topicId: topic.id, objectiveId: objective.id,
      difficulty: 'EASY', prompt: 'Audio question ' + index, content: { options: [{ id: 'a', text: 'Seven' }, { id: 'b', text: 'Eight' }] },
      answerKey: { correctOptionId: 'a' }, explanation: 'She gets up at seven.', hint: 'Listen for the time.', materialVersionId: oldAudio, source });
    created.push({ kind: 'questions', id: q.id });
    questions.push({ id: q.id as number, version: q.draftVersionId as number, role });
  }
  let lesson = await call('/api/author/lessons', { requestId: key(), unitId: unit.id, unitBaseRevision: 0,
    title: 'Audio browser lesson', description: 'Listen', instructions: [{ kind: 'TEXT', text: 'Listen for the time.' }], objectiveIds: [objective.id],
    questions: questions.map(q => ({ questionVersionId: q.version, practiceRole: q.role, position: 0 })) });
  created.push({ kind: 'lessons', id: lesson.id });
  lesson = await publish('lessons', lesson.id);
  const draft = await call(`/api/author/materials/${material.id}/draft`, { requestId: key(), baseRevision: material.rootRevision });
  await call(`/api/author/materials/${material.id}/draft`, { requestId: key(), ...audio, assetId: await upload(),
    draftVersionId: draft.draftVersionId, baseRevision: draft.draft.revision }, 'PUT');
  material = await publish('materials', material.id);
  return { material, questions, lesson, oldAudio, call };
}

test('audio selection follows lesson dependencies and fits a mobile screen', async ({ page }) => {
  const f = await fixture(page);
  await page.goto(`/#/content?area=materials&item=${f.material.id}`);
  const panel = page.getByRole('region', { name: 'Cập nhật audio đồng loạt' });
  await panel.getByRole('button', { name: 'Cập nhật audio cho câu hỏi/bài học', exact: true }).click();
  const q = panel.getByRole('group', { name: 'Câu hỏi liên quan' }).getByRole('checkbox').first();
  const lesson = panel.getByRole('group', { name: 'Bài học liên quan' }).getByRole('checkbox');
  await expect(q).toBeChecked(); await expect(lesson).toBeChecked();
  await q.uncheck(); await expect(lesson).not.toBeChecked();
  await lesson.check(); await expect(q).toBeChecked();
  const apply = panel.getByRole('button', { name: 'Áp dụng cho 3 câu hỏi và 1 bài học', exact: true });
  await expect(apply).toBeDisabled();
  await panel.getByRole('checkbox', { name: 'Tôi đã kiểm tra transcript', exact: false }).check();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  expect(await q.evaluate(element => element.getBoundingClientRect().height)).toBeLessThanOrEqual(22);
  expect(await q.evaluate(element => getComputedStyle(element.parentElement!).flexDirection)).toBe('row');
  await page.screenshot({ path: 'test-results/audio-update-mobile.png', fullPage: true });
  await apply.click();
  await expect(panel.getByRole('status')).toContainText('Đã cập nhật audio cho 3 câu hỏi và 1 bài học.');
  for (const question of f.questions) {
    const current = await f.call(`/api/author/questions/${question.id}`);
    expect(current.published.materialVersionId).toBe(f.material.publishedVersionId);
    expect((await f.call(`/api/author/questions/${question.id}/versions/${question.version}`)).materialVersionId).toBe(f.oldAudio);
  }
  const current = await f.call(`/api/author/lessons/${f.lesson.id}`);
  expect(current.published.versionNo).toBe(2);
  expect((await f.call(`/api/author/lessons/${f.lesson.id}/versions/${f.lesson.publishedVersionId}`)).versionNo).toBe(1);
});

test('retry after a lost audio update response replays the same command', async ({ page }) => {
  const f = await fixture(page);
  await page.goto(`/#/content?area=materials&item=${f.material.id}`);
  const panel = page.getByRole('region', { name: 'Cập nhật audio đồng loạt' });
  await panel.getByRole('button', { name: 'Cập nhật audio cho câu hỏi/bài học', exact: true }).click();
  await panel.getByRole('checkbox', { name: 'Tôi đã kiểm tra transcript', exact: false }).check();
  let command = '', calls = 0;
  await page.route(`**/api/author/materials/${f.material.id}/audio-update`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    calls++;
    if (calls === 1) {
      command = route.request().postData()!;
      const response = await route.fetch();
      expect(response.status(), await response.text()).toBe(200);
      return route.abort('failed');
    }
    expect(route.request().postData()).toBe(command);
    return route.continue();
  });
  await panel.getByRole('button', { name: 'Áp dụng cho 3 câu hỏi và 1 bài học', exact: true }).click();
  await expect(panel.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true })).toBeDisabled();
  await panel.getByRole('button', { name: 'Thử lại cập nhật audio', exact: true }).click();
  await expect(panel.getByRole('status')).toContainText('Đã cập nhật audio cho 3 câu hỏi và 1 bài học.');
  expect(calls).toBe(2);
  for (const question of f.questions) expect((await f.call(`/api/author/questions/${question.id}`)).published.versionNo).toBe(2);
  expect((await f.call(`/api/author/lessons/${f.lesson.id}`)).published.versionNo).toBe(2);
});
