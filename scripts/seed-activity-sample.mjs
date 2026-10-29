import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, renameSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { provisionActivitySample, sampleVersion } from './activity-sample-data.mjs';

const origin = new URL(process.env.SAMPLE_BASE_URL ?? 'http://localhost:5176');
assert.ok(origin.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(origin.hostname) && origin.pathname === '/' && !origin.username && !origin.password, 'Chỉ dùng HTTP local.');
if (!process.argv.includes('--apply')) {
  console.log(`Kế hoạch ${sampleVersion} tại ${origin.origin}: tạo riêng 1 Unit/1 Lesson, 18 câu học + 4 câu ôn, 4 học liệu đọc + 1 audio. Không sửa bài cũ. Thêm --apply để thực hiện.`);
  process.exit(0);
}
let config = {};
if (!process.env.SAMPLE_AUTHOR_EMAIL || !process.env.SAMPLE_AUTHOR_PASSWORD) {
  config = Object.fromEntries(readFileSync(new URL('../.env', import.meta.url), 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#') && s.includes('=')).map(s => { const at = s.indexOf('='); return [s.slice(0, at), s.slice(at + 1).replace(/^(["'])(.*)\1$/, '$2')]; }));
}
const email = process.env.SAMPLE_AUTHOR_EMAIL ?? config.BOOTSTRAP_ADMIN_EMAIL;
const password = process.env.SAMPLE_AUTHOR_PASSWORD ?? config.BOOTSTRAP_ADMIN_PASSWORD;
assert.ok(email && password, 'Cần tài khoản tác giả qua SAMPLE_AUTHOR_EMAIL/PASSWORD hoặc cấu hình bootstrap admin local.');
const cookies = new Map();
async function call(path, method = 'GET', body, multipart = false) {
  let token;
  if (method !== 'GET') token = await call('/api/auth/csrf');
  const response = await fetch(new URL(path, origin), { method, redirect: 'error', signal: AbortSignal.timeout(30000),
    headers: { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; '), ...(body && !multipart ? { 'Content-Type': 'application/json' } : {}), ...(token ? { [token.headerName]: token.token } : {}) }, body: multipart ? body : body ? JSON.stringify(body) : undefined });
  response.headers.getSetCookie().forEach(line => { const pair = line.split(';')[0]; const at = pair.indexOf('='); cookies.set(pair.slice(0, at), pair.slice(at + 1)); });
  const data = await response.json();
  assert.ok(response.ok, `${method} ${path}: HTTP ${response.status} ${data.code ?? ''} ${JSON.stringify(data.fieldErrors ?? {})}`);
  return data;
}
await call('/api/auth/login', 'POST', { email, password });
const principal = await call('/api/auth/me');
const actor = principal.user ?? principal;
assert.equal(actor.role, 'ADMIN', 'Cần ADMIN để tạo vòng và xuất bản theo luồng phân công/duyệt V13.');
const scope = createHash('sha256').update(origin.origin + ':' + actor.id + ':' + sampleVersion).digest('hex').slice(0, 16);
const folder = fileURLToPath(new URL('../.test-state/activity-sample/', import.meta.url));
mkdirSync(folder, { recursive: true });
const statePath = folder + scope + '.json', lockPath = folder + scope + '.lock';
const lock = openSync(lockPath, 'wx');
try {
  let state;
  try { state = JSON.parse(readFileSync(statePath, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; state = { origin: origin.origin, actorId: actor.id, version: sampleVersion, namespace: 'activity-' + scope, actions: {} }; }
  assert.equal(state.origin, origin.origin); assert.equal(state.actorId, actor.id); assert.equal(state.version, sampleVersion);
  const save = () => { writeFileSync(statePath + '.tmp', JSON.stringify(state, null, 2)); renameSync(statePath + '.tmp', statePath); };
  const client = {
    get: path => call(path),
    async action(label, path, body) {
      let record = state.actions[label];
      if (record?.done) return record.response;
      if (!record) { record = state.actions[label] = { path, body: { ...body, requestId: randomUUID() } }; save(); }
      assert.equal(record.path, path);
      record.response = await call(record.path, 'POST', record.body); record.done = true; save(); return record.response;
    },
    async upload(label, filename) {
      let record = state.actions[label];
      if (record?.done) return record.response;
      if (!record) { record = state.actions[label] = { filename, requestId: randomUUID() }; save(); }
      assert.equal(record.filename, filename);
      const form = new FormData();
      form.set('requestId', record.requestId);
      form.set('file', new Blob([readFileSync(new URL('../source/server/src/main/resources/demo/materials/' + filename, import.meta.url))], { type: 'audio/wav' }), filename);
      record.response = await call('/api/assets', 'POST', form, true); record.done = true; save(); return record.response;
    },
    async publish(kind, id, label) {
      if (state.actions[label]) return this.action(label, `/api/author/${kind}/${id}/publish`);
      const preview = await call(`/api/author/${kind}/${id}/preview`);
      assert.equal(preview.errors.length, 0, JSON.stringify(preview.errors));
      return this.action(label, `/api/author/${kind}/${id}/publish`, { baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken });
    }
  };
  const result = await provisionActivitySample(client, state.namespace);
  console.log(`Đã có bài mẫu ${result.lessonId}, Unit ${result.unitId}, phiên bản ${result.lessonVersionId}. Vào chơi mở theo lộ trình hiện hành. Checkpoint: .test-state/activity-sample/${scope}.json`);
} finally { closeSync(lock); unlinkSync(lockPath); }
