import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateDataset, importDataset, localOrigin, createApiClient } from './seed-demo-questions.mjs';

const dataset = JSON.parse(readFileSync(new URL('../statics/dataset/demo-questions-a1-a2.json', import.meta.url), 'utf8'));
const origin = 'http://localhost:15343', actor = { id: 1, role: 'ADMIN' };
function fakeApi() {
  let id = 100, failed = false;
  const receipts = new Map(), resources = new Map(), writes = [], previews = [];
  return {
    writes, previews, resources,
    loseResponseAt: null,
    async call(path, method = 'GET', body) {
      if (method === 'GET') {
        if (path.endsWith('/preview')) {
          previews.push(path); const lesson = resources.get(path.replace('/preview', ''));
          return { errors: [], baseRevision: 0, draftVersionId: lesson.draftVersionId, previewToken: 'preview-once' };
        }
        const resource = resources.get(path); assert.ok(resource, `Không có ${path}`);
        return structuredClone(path.includes('/units/') ? { unit: resource } : resource);
      }
      writes.push(structuredClone({ path, body }));
      if (receipts.has(body.requestId)) {
        const saved = receipts.get(body.requestId); assert.deepEqual({ path, body }, saved.request);
        return structuredClone(saved.response);
      }
      let result;
      if (path.endsWith('/publish')) {
        const resource = resources.get(path.replace('/publish', ''));
        resource.publishedVersionId = resource.draftVersionId; resource.draftVersionId = null;
        result = resource;
      } else if (path.endsWith('/submit') || path.endsWith('/approve')) {
        const resource = resources.get(path.replace(/\/(submit|approve)$/, ''));
        assert.equal(body.baseRevision, resource.revision); resource.revision++;
        resource.state = path.endsWith('/submit') ? 'PENDING' : 'READY'; result = resource;
      } else {
        result = { ...structuredClone(body), id: ++id, revision: 0, draftVersionId: id + 1000, publishedVersionId: null };
        if (path.endsWith('/contest-papers')) result.state = 'DRAFT';
        resources.set(`${path}/${id}`, result);
      }
      receipts.set(body.requestId, { request: structuredClone({ path, body }), response: structuredClone(result) });
      if (!failed && path === this.loseResponseAt) { failed = true; throw new Error('Simulated lost acknowledgement after commit'); }
      return structuredClone(result);
    }
  };
}
async function fixture(run) {
  const parent = fileURLToPath(new URL('../.test-state/', import.meta.url));
  mkdirSync(parent, { recursive: true });
  const dir = mkdtempSync(join(parent, 'demo-question-test-'));
  try { await run(dir); } finally {
    assert.equal(dirname(resolve(dir)), resolve(parent), 'Chỉ xóa thư mục test vừa tạo trong .test-state.');
    rmSync(dir, { recursive: true, force: true });
  }
}

test('dataset has 60 learner questions, 12 review variants and answer keys in all four formats', () => {
  const summary = validateDataset(dataset);
  assert.equal(summary.length, 3);
  assert.equal(summary.reduce((sum, set) => sum + set.learning, 0), 60);
  assert.equal(summary.reduce((sum, set) => sum + set.review, 0), 12);
  for (const set of summary) assert.deepEqual(Object.values(set.types), [6, 6, 6, 6]);
  const broken = structuredClone(dataset); broken.sets[0].questions[0].answerKey.correctOptionId = 'missing';
  assert.throws(() => validateDataset(broken), /Đáp án không thuộc/);
  const badPairs = structuredClone(dataset); badPairs.sets[0].questions[2].answerKey.pairs[1].rightId = 'r0';
  assert.throws(() => validateDataset(badPairs), /Ghép cặp/);
  const badOrder = structuredClone(dataset); badOrder.sets[0].questions[3].answerKey.acceptedOrders[0].pop();
  assert.throws(() => validateDataset(badOrder), /Thứ tự/);
});

test('full import publishes 3 lessons and approves 3 reusable papers; second run writes nothing', async () => fixture(async checkpointRoot => {
  const server = fakeApi();
  const options = { dataset, origin, actor, api: server.call.bind(server), checkpointRoot, progress: () => {} };
  const first = await importDataset(options), count = server.writes.length;
  assert.equal(first.sets.length, 3);
  assert.ok(first.sets.every(s => s.lessonId && s.lessonVersionId && s.paperId));
  for (const set of first.sets) {
    const lesson = server.resources.get(`/api/author/lessons/${set.lessonId}`);
    assert.equal(lesson.questions.filter(q => q.practiceRole !== 'REVIEW_VARIANT').length, 20);
    assert.equal(lesson.questions.filter(q => q.practiceRole === 'REVIEW_VARIANT').length, 4);
    const paper = server.resources.get(`/api/author/contest-papers/${set.paperId}`);
    assert.equal(paper.questions.length, 20); assert.equal(paper.state, 'READY');
    assert.equal(new Set(paper.questions.map(q => q.id)).size, 20);
    assert.ok(paper.questions.some(q => q.context?.includes('school') || q.context?.includes('market') || q.context?.includes('Town')));
    assert.ok(paper.questions.every(q => !('hint' in q) && !('practiceRole' in q)));
  }
  const second = await importDataset(options);
  assert.deepEqual(second, first); assert.equal(server.writes.length, count); assert.equal(server.previews.length, 3);
  const changed = structuredClone(dataset); changed.sets[0].title += ' updated';
  await assert.rejects(importDataset({ ...options, dataset: changed }), /JSON đã đổi/);
  assert.equal(server.writes.length, count);
}));

test('lost publish acknowledgement replays exact request without asking for a new preview', async () => fixture(async checkpointRoot => {
  const server = fakeApi(), original = server.call.bind(server);
  const api = async (path, method, body) => {
    if (path.endsWith('/publish')) server.loseResponseAt = path;
    return original(path, method, body);
  };
  const options = { dataset, origin, actor, api, target: 'learning', checkpointRoot, progress: () => {} };
  await assert.rejects(importDataset(options), /Simulated lost/);
  const interrupted = server.writes.find(w => w.path.endsWith('/publish'));
  await importDataset(options);
  const retry = server.writes.filter(w => w.path === interrupted.path);
  assert.equal(retry.length, 2); assert.deepEqual(retry[0], retry[1]);
  assert.equal(server.previews.filter(p => p === interrupted.path.replace('/publish', '/preview')).length, 1);
}));

test('contests-only can be extended with learning without duplicating papers; non-admin is rejected', async () => fixture(async checkpointRoot => {
  const server = fakeApi(), options = { dataset, origin, actor, api: server.call.bind(server), checkpointRoot, progress: () => {} };
  await importDataset({ ...options, target: 'contests' });
  assert.equal(server.writes.length, 9);
  const papers = server.writes.filter(w => w.path === '/api/author/contest-papers');
  await importDataset(options);
  assert.equal(server.writes.filter(w => w.path === '/api/author/contest-papers').length, papers.length);
  await assert.rejects(importDataset({ ...options, actor: { id: 2, role: 'TEACHER' } }), /Dùng ADMIN/);
}));

test('local target enforcement and HTTP client retain cookies and fetch CSRF without redirects', async () => {
  for (const url of ['https://example.com', 'http://localhost/path', 'http://user:pass@localhost', 'http://localhost/?x=1']) assert.throws(() => localOrigin(url));
  const calls = [];
  const api = createApiClient(origin, async (url, options) => {
    calls.push({ path: url.pathname, ...options });
    const csrf = url.pathname.endsWith('/csrf');
    return { ok: true, status: 200, headers: { getSetCookie: () => csrf ? ['SESSION=opaque; HttpOnly'] : [] }, json: async () => csrf ? { headerName: 'X-CSRF-TOKEN', token: 'csrf' } : { id: 1 } };
  });
  await api('/api/auth/login', 'POST', { email: 'test@example.local', password: 'placeholder' });
  assert.equal(calls.length, 2); assert.equal(calls[1].headers.Cookie, 'SESSION=opaque');
  assert.equal(calls[1].headers['X-CSRF-TOKEN'], 'csrf'); assert.equal(calls[1].redirect, 'error');
});
