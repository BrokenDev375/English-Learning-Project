import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, renameSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const defaultFile = join(root, 'statics/dataset/demo-questions-a1-a2.json');
const types = ['SINGLE_CHOICE', 'FILL_BLANK', 'MATCHING', 'ORDERING'];
const roles = ['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT'];
const hash = value => createHash('sha256').update(value).digest('hex');
const nonempty = value => typeof value === 'string' && value.trim().length > 0;
const unique = values => new Set(values).size === values.length;
const sameIds = (a, b) => unique(a) && unique(b) && a.length === b.length && a.every(id => b.includes(id));
function exact(value, keys, label) {
  assert.ok(value && typeof value === 'object' && !Array.isArray(value), `${label}: cần object.`);
  assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), `${label}: trường không đúng định dạng.`);
}
function items(values, label, maximum = 6) {
  assert.ok(Array.isArray(values) && values.length >= 2 && values.length <= maximum, `${label}: cần 2–${maximum} mục.`);
  values.forEach(value => {
    exact(value, ['id', 'text'], label);
    assert.ok(nonempty(value.id) && value.id.length <= 40 && nonempty(value.text) && value.text.length <= 500, `${label}: mã/nội dung không hợp lệ.`);
  });
  assert.ok(unique(values.map(value => value.id)), `${label}: mã trùng.`);
  return values.map(value => value.id);
}

/** Preflight before authentication or writes; backend remains the authority for publication. */
export function validateDataset(dataset) {
  exact(dataset, ['schemaVersion', 'datasetVersion', 'source', 'sets'], 'dataset');
  assert.equal(dataset.schemaVersion, 1, 'Chỉ hỗ trợ schemaVersion=1.');
  assert.match(dataset.datasetVersion, /^[a-z0-9][a-z0-9.-]{1,60}$/);
  exact(dataset.source, ['label', 'url', 'license'], 'source');
  assert.ok(nonempty(dataset.source.label) && dataset.source.label.length <= 200);
  assert.ok(dataset.source.url === null || /^https?:\/\//.test(dataset.source.url));
  assert.ok(dataset.source.license === null || (nonempty(dataset.source.license) && dataset.source.license.length <= 200));
  assert.ok(Array.isArray(dataset.sets) && dataset.sets.length > 0 && unique(dataset.sets.map(set => set.key)), 'Chủ đề rỗng hoặc trùng mã.');
  const prompts = new Set();
  return dataset.sets.map(set => {
    exact(set, ['key', 'title', 'level', 'description', 'reading', 'questions'], 'set');
    assert.match(set.key, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    assert.ok(set.key.length <= 24 && nonempty(set.title) && set.title.length <= 140 && nonempty(set.level));
    assert.ok(nonempty(set.description) && set.description.length <= 1000);
    exact(set.reading, ['title', 'text'], 'reading');
    assert.ok(nonempty(set.reading.title) && set.reading.title.length <= 140 && nonempty(set.reading.text) && set.reading.text.length <= 12000);
    assert.ok(Array.isArray(set.questions) && set.questions.length <= 100 && unique(set.questions.map(q => q.key)));
    const counts = Object.fromEntries(roles.map(role => [role, 0]));
    for (const q of set.questions) {
      exact(q, ['key', 'type', 'prompt', 'reading', 'content', 'answerKey', 'explanation', 'practiceRole', 'difficulty', 'hint'], 'question');
      assert.match(q.key, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
      assert.ok(types.includes(q.type) && roles.includes(q.practiceRole) && ['EASY', 'MEDIUM', 'HARD'].includes(q.difficulty));
      const signature = JSON.stringify([q.prompt, q.content, q.reading ? set.reading.text : null]);
      assert.ok(nonempty(q.prompt) && q.prompt.length <= 2000 && !prompts.has(signature), `Câu hỏi trống/quá dài/trùng nội dung: ${q.prompt}`);
      prompts.add(signature);
      assert.ok(nonempty(q.explanation) && q.explanation.length <= 4000 && typeof q.reading === 'boolean');
      assert.ok(q.hint === null || (nonempty(q.hint) && q.hint.length <= 2000));
      if (q.practiceRole === 'GUIDED') assert.ok(nonempty(q.hint), 'GUIDED cần hint.');
      counts[q.practiceRole]++;
      const c = q.content, a = q.answerKey;
      if (q.type === 'SINGLE_CHOICE') {
        exact(c, ['options'], 'choice.content'); exact(a, ['correctOptionId'], 'choice.answerKey');
        assert.ok(items(c.options, 'options').includes(a.correctOptionId), 'Đáp án không thuộc lựa chọn.');
      } else if (q.type === 'FILL_BLANK') {
        exact(c, ['parts'], 'fill.content'); exact(a, ['blanks', 'caseSensitive', 'normalizeWhitespace'], 'fill.answerKey');
        assert.ok(Array.isArray(c.parts) && c.parts.length <= 21 && Array.isArray(a.blanks));
        const blanks = [];
        for (const part of c.parts) {
          if (part.kind === 'TEXT') { exact(part, ['kind', 'text'], 'part'); assert.ok(typeof part.text === 'string' && part.text.length <= 2000); }
          else { exact(part, ['kind', 'blankId'], 'part'); assert.equal(part.kind, 'BLANK'); assert.ok(nonempty(part.blankId) && part.blankId.length <= 40); blanks.push(part.blankId); }
        }
        assert.ok(c.parts.some(p => p.kind === 'TEXT' && nonempty(p.text)) && blanks.length >= 1 && blanks.length <= 5);
        assert.ok(sameIds(blanks, a.blanks.map(b => b.blankId)), 'Đáp án ô trống thiếu/trùng/thừa.');
        for (const blank of a.blanks) {
          exact(blank, ['blankId', 'acceptedAnswers'], 'blank');
          assert.ok(Array.isArray(blank.acceptedAnswers) && blank.acceptedAnswers.length >= 1 && blank.acceptedAnswers.length <= 10 && blank.acceptedAnswers.every(v => nonempty(v) && v.length <= 200));
        }
        assert.ok(typeof a.caseSensitive === 'boolean' && typeof a.normalizeWhitespace === 'boolean');
      } else if (q.type === 'MATCHING') {
        exact(c, ['leftItems', 'rightItems'], 'matching.content'); exact(a, ['pairs'], 'matching.answerKey');
        const left = items(c.leftItems, 'leftItems'), right = items(c.rightItems, 'rightItems');
        assert.ok(Array.isArray(a.pairs)); a.pairs.forEach(p => exact(p, ['leftId', 'rightId'], 'pair'));
        assert.ok(sameIds(left, a.pairs.map(p => p.leftId)) && sameIds(right, a.pairs.map(p => p.rightId)), 'Ghép cặp không đủ hoặc không một-một.');
      } else {
        exact(c, ['tokens'], 'ordering.content'); exact(a, ['acceptedOrders'], 'ordering.answerKey');
        const tokens = items(c.tokens, 'tokens', 20);
        assert.ok(c.tokens.every(t => t.text.length <= 100));
        assert.ok(Array.isArray(a.acceptedOrders) && a.acceptedOrders.length >= 1 && a.acceptedOrders.length <= 10 && a.acceptedOrders.every(order => Array.isArray(order) && sameIds(tokens, order)), 'Thứ tự đáp án không khớp token.');
      }
    }
    assert.ok(roles.every(role => counts[role] > 0), 'Mỗi bài cần đủ GUIDED, INDEPENDENT và REVIEW_VARIANT.');
    return { key: set.key, title: set.title, level: set.level, learning: counts.GUIDED + counts.INDEPENDENT, review: counts.REVIEW_VARIANT, types: Object.fromEntries(types.map(type => [type, set.questions.filter(q => q.type === type).length])) };
  });
}

export function localOrigin(value) {
  const url = new URL(value);
  assert.ok(url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname) && url.pathname === '/' && !url.search && !url.hash && !url.username && !url.password, 'Chỉ nạp qua HTTP localhost, không có path/query/credentials.');
  return url.origin;
}

export function createApiClient(origin, fetchImpl = fetch) {
  const cookies = new Map();
  return async function call(path, method = 'GET', body) {
    assert.ok(path.startsWith('/api/'), 'Chỉ gọi API nội bộ.');
    const csrf = method === 'GET' ? null : await call('/api/auth/csrf');
    let response;
    try {
      response = await fetchImpl(new URL(path, origin), { method, redirect: 'error', signal: AbortSignal.timeout(30000), headers: {
        Cookie: [...cookies].map(([name, value]) => `${name}=${value}`).join('; '),
        ...(body ? { 'Content-Type': 'application/json' } : {}), ...(csrf ? { [csrf.headerName]: csrf.token } : {})
      }, body: body ? JSON.stringify(body) : undefined });
    } catch {
      throw new Error(`Không nhận được phản hồi cho ${method} ${path}. Giữ checkpoint và chạy lại cùng lệnh; không đổi requestId.`);
    }
    for (const line of response.headers.getSetCookie()) {
      const pair = line.split(';')[0], at = pair.indexOf('=');
      cookies.set(pair.slice(0, at), pair.slice(at + 1));
    }
    let data;
    try { data = await response.json(); } catch { throw new Error(`${method} ${path}: phản hồi không phải JSON (HTTP ${response.status}).`); }
    if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}, ${data.code ?? 'API_ERROR'}. ${JSON.stringify(data.fieldErrors ?? {})}`);
    return data;
  };
}

function questionId(version, set, key) {
  const hex = hash(`${version}:${set}:${key}`);
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

/** Execute through a journal that retains the exact request before the HTTP call. */
export async function importDataset({ dataset, origin, actor, api, target = 'all', checkpointRoot = join(root, '.test-state/demo-questions'), progress = console.log }) {
  validateDataset(dataset); origin = localOrigin(origin);
  assert.equal(actor.role, 'ADMIN', 'Dùng ADMIN để tạo vòng, xuất bản và duyệt bộ đề theo quy trình hiện hành.');
  assert.ok(Number.isSafeInteger(actor.id) && actor.id > 0);
  assert.ok(['all', 'learning', 'contests'].includes(target));
  const digest = hash(JSON.stringify(dataset));
  const scope = hash(`${origin}:${actor.id}:${dataset.datasetVersion}`).slice(0, 16);
  mkdirSync(checkpointRoot, { recursive: true });
  const statePath = join(checkpointRoot, `${scope}.json`), lockPath = join(checkpointRoot, `${scope}.lock`);
  let lock;
  try { lock = openSync(lockPath, 'wx'); } catch (error) {
    if (error.code === 'EEXIST') throw new Error(`Một lần nạp khác đang giữ ${lockPath}. Chỉ xóa lock sau khi xác minh tiến trình cũ đã dừng; giữ file JSON.`);
    throw error;
  }
  try {
    let state;
    try { state = JSON.parse(readFileSync(statePath, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw error; state = { origin, actorId: actor.id, version: dataset.datasetVersion, digest, actions: {} }; }
    assert.equal(state.origin, origin); assert.equal(state.actorId, actor.id); assert.equal(state.version, dataset.datasetVersion);
    assert.equal(state.digest, digest, 'JSON đã đổi sau lần nạp trước. Khôi phục file cũ để hoàn tất; muốn bộ mới hãy đổi datasetVersion.');
    const save = () => { writeFileSync(`${statePath}.tmp`, JSON.stringify(state, null, 2) + '\n', { flush: true }); renameSync(`${statePath}.tmp`, statePath); };
    async function action(label, path, makeBody) {
      let record = state.actions[label];
      if (!record) {
        const body = typeof makeBody === 'function' ? await makeBody() : makeBody;
        record = state.actions[label] = { path, body: { ...body, requestId: randomUUID() }, done: false }; save();
      }
      assert.equal(record.path, path, 'Đường dẫn không khớp checkpoint.');
      if (!record.done) { record.response = await api(path, 'POST', record.body); record.done = true; save(); }
      return record.response;
    }
    const report = { origin, version: dataset.datasetVersion, checkpoint: statePath, sets: [] };
    for (const [index, set] of dataset.sets.entries()) {
      const result = { key: set.key, title: set.title };
      const label = name => `${set.key}/${name}`;
      if (target !== 'contests') {
        const topic = await action(label('topic'), '/api/author/topics', { code: `dq-${scope}-${set.key}`, name: `[Demo] ${set.title}`, description: set.description });
        const objective = await action(label('objective'), '/api/author/objectives', { topicId: topic.id, code: `dq-${scope}-${set.key}-skills`, name: `${set.title} — ${set.level}`, description: set.description });
        const unit = await action(label('unit'), '/api/author/units', { topicId: topic.id, title: `[Demo] ${set.title}`, description: set.description, stage: 1, sortOrder: 100 + index });
        const material = await action(label('reading'), '/api/author/materials', { kind: 'READING', title: `[Demo] ${set.reading.title}`, readingText: set.reading.text, assetId: null, altText: null, source: dataset.source });
        const positions = Object.fromEntries(roles.map(role => [role, 0])), slots = [];
        for (const q of set.questions) {
          const question = await action(label(`question/${q.key}`), '/api/author/questions', { type: q.type, topicId: topic.id, objectiveId: objective.id, difficulty: q.difficulty,
            prompt: q.prompt, content: q.content, answerKey: q.answerKey, explanation: q.explanation, hint: q.hint, materialVersionId: q.reading ? material.draftVersionId : null, source: dataset.source });
          slots.push({ questionVersionId: question.draftVersionId, practiceRole: q.practiceRole, position: positions[q.practiceRole]++ });
        }
        const lesson = await action(label('lesson'), '/api/author/lessons', async () => {
          const current = await api(`/api/author/units/${unit.id}`);
          return { unitId: unit.id, unitBaseRevision: current.unit.revision, title: `[Demo] ${set.title} — 20 câu`, description: `${set.description} Có thêm 4 biến thể ôn tập.`,
            instructions: [{ kind: 'TEXT', text: 'Làm 20 câu gồm trắc nghiệm, điền từ, ghép cặp và sắp xếp từ. Bốn câu đầu có gợi ý; đọc đoạn văn khi câu hỏi yêu cầu. Nộp đủ đáp án để hoàn thành bài.' }], objectiveIds: [objective.id], questions: slots };
        });
        const published = await action(label('publish'), `/api/author/lessons/${lesson.id}/publish`, async () => {
          const preview = await api(`/api/author/lessons/${lesson.id}/preview`);
          assert.deepEqual(preview.errors, [], `Bài ${set.key} chưa đủ điều kiện xuất bản: ${JSON.stringify(preview.errors)}`);
          return { baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken };
        });
        const current = await api(`/api/author/lessons/${lesson.id}`);
        assert.equal(current.publishedVersionId, published.publishedVersionId, 'Phiên bản đã công bố thay đổi. Kiểm tra thủ công, script không ghi đè.');
        assert.ok(current.publishedVersionId, 'Không tìm thấy bài công bố; kiểm tra đúng DB/checkpoint.');
        result.unitId = unit.id; result.lessonId = lesson.id; result.lessonVersionId = published.publishedVersionId;
      }
      if (target !== 'learning') {
        const paper = await action(label('paper'), '/api/author/contest-papers', { title: `[Demo] ${set.title} — ${set.level}`, description: `${set.description} Bộ đề demo dùng chung nội dung với bài học; có giải thích đáp án.`,
          questions: set.questions.filter(q => q.practiceRole !== 'REVIEW_VARIANT').map(q => ({ id: questionId(dataset.datasetVersion, set.key, q.key), type: q.type, prompt: q.prompt,
            context: q.reading ? set.reading.text : null, difficulty: q.difficulty, content: q.content, answerKey: q.answerKey, explanation: q.explanation })) });
        const pending = await action(label('paper-submit'), `/api/author/contest-papers/${paper.id}/submit`, { baseRevision: paper.revision });
        await action(label('paper-approve'), `/api/author/contest-papers/${paper.id}/approve`, { baseRevision: pending.revision });
        const current = await api(`/api/author/contest-papers/${paper.id}`);
        assert.equal(current.title, paper.title, 'Tên bộ đề thay đổi; kiểm tra đúng DB/checkpoint.');
        assert.equal(current.state, 'READY', 'Bộ đề không còn READY. Script không sửa hoặc duyệt lại nội dung đã bị thay đổi.');
        assert.equal(current.questions.length, set.questions.filter(q => q.practiceRole !== 'REVIEW_VARIANT').length);
        result.paperId = paper.id;
      }
      report.sets.push(result); progress(`Đã xác minh ${set.title}: ${result.lessonId ? `Lesson ${result.lessonId}` : ''}${result.lessonId && result.paperId ? ', ' : ''}${result.paperId ? `bộ đề ${result.paperId} READY` : ''}.`);
    }
    return report;
  } finally { closeSync(lock); unlinkSync(lockPath); }
}

function configFile() {
  try { return Object.fromEntries(readFileSync(join(root, '.env'), 'utf8').split(/\r?\n/).filter(line => line.trim() && !line.trimStart().startsWith('#') && line.includes('=')).map(line => {
    const at = line.indexOf('='); return [line.slice(0, at).trim(), line.slice(at + 1).trim().replace(/^(["'])(.*)\1$/, '$2')];
  })); } catch (error) { if (error.code === 'ENOENT') return {}; throw error; }
}

async function main(args) {
  const options = { apply: false, target: 'all', file: defaultFile };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--apply') options.apply = true;
    else if (['--file', '--target', '--base-url'].includes(args[i])) {
      const key = args[i].slice(2); assert.ok(args[i + 1] && !args[i + 1].startsWith('--'), `Thiếu giá trị ${args[i]}.`); options[key] = args[++i];
    } else if (args[i] === '--help') {
      console.log('node scripts/seed-demo-questions.mjs [--file path.json] [--target all|learning|contests] [--base-url http://localhost:5173] [--apply]\nMặc định chỉ kiểm tra JSON. --apply cần ADMIN (DEMO_SEED_EMAIL/PASSWORD hoặc BOOTSTRAP_ADMIN_* trong .env).'); return;
    } else throw new Error(`Tham số không hỗ trợ: ${args[i]}`);
  }
  assert.ok(['all', 'learning', 'contests'].includes(options.target), '--target phải là all, learning hoặc contests.');
  const dataset = JSON.parse(readFileSync(resolve(options.file), 'utf8'));
  const summary = validateDataset(dataset);
  console.log(`${dataset.datasetVersion}: ${summary.length} chủ đề; đích ${options.target}.`);
  for (const set of summary) console.log(`  ${set.title} (${set.level}): ${set.learning} câu học/đề thi + ${set.review} câu ôn.`);
  if (!options.apply) { console.log('Chỉ kiểm tra dữ liệu, chưa gọi API. Thêm --base-url URL --apply để nạp.'); return; }
  assert.ok(options['base-url'], 'Cần --base-url để chọn rõ DB đích.');
  const origin = localOrigin(options['base-url']);
  const config = configFile();
  const email = process.env.DEMO_SEED_EMAIL ?? process.env.BOOTSTRAP_ADMIN_EMAIL ?? config.BOOTSTRAP_ADMIN_EMAIL;
  const password = process.env.DEMO_SEED_PASSWORD ?? process.env.BOOTSTRAP_ADMIN_PASSWORD ?? config.BOOTSTRAP_ADMIN_PASSWORD;
  assert.ok(email && password, 'Cần DEMO_SEED_EMAIL/PASSWORD hoặc BOOTSTRAP_ADMIN_EMAIL/PASSWORD trong .env.');
  const api = createApiClient(origin);
  await api('/api/auth/login', 'POST', { email, password });
  const me = await api('/api/auth/me');
  const report = await importDataset({ dataset, origin, actor: me.user ?? me, api, target: options.target });
  const reportPath = join(dirname(report.checkpoint), `${basename(report.checkpoint, '.json')}-${options.target}-report.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n', { flush: true });
  console.log(`Hoàn tất. Mở ${origin}/#/contests?area=papers để chọn bộ đề; bài học mở theo lộ trình vòng hiện hành.\nKết quả local: ${reportPath}`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(`Lỗi: ${error.message}`); process.exitCode = 1; });
}
