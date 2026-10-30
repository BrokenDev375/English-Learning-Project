// Opt-in upgrade of the first baseline lesson; creates no Unit, learner or attempt.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, renameSync, mkdirSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const origin = new URL(process.env.BASELINE_BASE_URL ?? 'http://localhost:5176');
assert.ok(origin.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(origin.hostname) && origin.pathname === '/' && !origin.username && !origin.password);
if (!process.argv.includes('--apply')) {
  console.log(`Kế hoạch tại ${origin.origin}: nâng bài “A day with Linh — Morning routine” thành 18 câu: 6 trắc nghiệm, 4 ghép cặp, 6 điền từ, 2 nghe. Tái dùng 10 câu/học liệu cũ, bổ sung 8 câu; giữ 5 câu ôn. Không tạo vòng/tài khoản/lượt học. Thêm --apply để thực hiện.`);
  process.exit(0);
}
const config = Object.fromEntries(readFileSync(new URL('../.env', import.meta.url), 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#') && s.includes('=')).map(s => { const i = s.indexOf('='); return [s.slice(0, i), s.slice(i + 1).replace(/^("|')(.*)\1$/, '$2')]; }));
const cookies = new Map();
async function call(path, method = 'GET', body) {
  const csrf = method === 'GET' ? null : await call('/api/auth/csrf');
  const r = await fetch(new URL(path, origin), { method, redirect: 'error', signal: AbortSignal.timeout(30000), headers: { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; '), ...(body ? { 'Content-Type': 'application/json' } : {}), ...(csrf ? { [csrf.headerName]: csrf.token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  r.headers.getSetCookie().forEach(line => { const p = line.split(';')[0], i = p.indexOf('='); cookies.set(p.slice(0, i), p.slice(i + 1)); });
  const data = await r.json();
  assert.ok(r.ok, `${method} ${path}: ${r.status} ${JSON.stringify(data)}`);
  return data;
}
await call('/api/auth/login', 'POST', { email: config.DEMO_TEACHER_EMAIL, password: config.DEMO_TEACHER_PASSWORD });
const me = await call('/api/auth/me');
const actor = me.user ?? me;
assert.equal(actor.role, 'TEACHER');
const folder = fileURLToPath(new URL('../.test-state/baseline-flex/', import.meta.url));
mkdirSync(folder, { recursive: true });
const scope = createHash('sha256').update(origin.origin + ':' + actor.id + ':beginner-flex-1').digest('hex').slice(0, 16);
const path = folder + scope + '.json', lockPath = folder + scope + '.lock';
const lock = openSync(lockPath, 'wx');
try {
  let state;
  try { state = JSON.parse(readFileSync(path, 'utf8')); }
  catch (e) { if (e.code !== 'ENOENT') throw e; state = { origin: origin.origin, actorId: actor.id, actions: {} }; }
  assert.equal(state.origin, origin.origin); assert.equal(state.actorId, actor.id);
  const save = () => { writeFileSync(path + '.tmp', JSON.stringify(state, null, 2)); renameSync(path + '.tmp', path); };
  async function action(label, path, method, body) {
    let record = state.actions[label];
    if (!record) { record = state.actions[label] = { path, method, body: { ...body, requestId: randomUUID() } }; save(); }
    assert.equal(record.path, path); assert.equal(record.method, method);
    if (!record.done) { record.response = await call(record.path, record.method, record.body); record.done = true; save(); }
    return record.response;
  }
  if (!state.original) {
    const page = await call('/api/author/lessons?q=' + encodeURIComponent('A day with Linh — Morning routine'));
    const candidates = page.items.filter(x => x.title === 'A day with Linh — Morning routine');
    assert.equal(candidates.length, 1, 'Cần đúng một bài nền gốc; không tự chọn bài khác.');
    const root = await call('/api/author/lessons/' + candidates[0].id);
    assert.ok(!root.draft && root.published && !root.published.activities?.length, 'Bài có chỉnh sửa riêng; không ghi đè.');
    assert.equal(root.published.questions.filter(q => q.practiceRole !== 'REVIEW_VARIANT').length, 10);
    state.original = root; save();
  }
  const original = state.original, lessonId = original.id;
  let current = await call('/api/author/lessons/' + lessonId);
  // Publication may have committed even if its response was lost. Replay the
  // persisted command before comparing the current published version.
  if (state.actions.publish && !state.actions.publish.done) {
    await action('publish', `/api/author/lessons/${lessonId}/publish`, 'POST');
    current = await call('/api/author/lessons/' + lessonId);
  }
  if (state.actions.publish?.done) {
    assert.equal(current.publishedVersionId, state.actions.publish.response.publishedVersionId, 'Bài đã đổi sau lần nâng cấp; không ghi đè.');
    console.log(`Đã nâng bài ${lessonId}, phiên bản ${current.publishedVersionId}; giữ nguyên kết quả lần chạy trước.`);
  } else {
    assert.equal(current.publishedVersionId, original.publishedVersionId, 'Phiên bản bài đã đổi; cần đối soát.');
    const started = await action('start-draft', `/api/author/lessons/${lessonId}/draft`, 'POST', { baseRevision: original.rootRevision });
    if (!state.preview) { state.preview = await call(`/api/author/lessons/${lessonId}/preview`); save(); }
    const data = state.preview.data, lesson = original.published;
    const byId = new Map(data.questions.map(q => [q.id, q]));
    const materials = new Map(data.materials.map(m => [m.id, m]));
    const anchor = data.questions[0];
    const audio = data.materials.find(m => m.kind === 'AUDIO'); assert.ok(audio);
    const source = { label: 'English Learning — bổ sung vòng đầu', url: null, license: 'Dùng học tập trong dự án' };
    const choice = (prompt, options, correct, explanation, materialVersionId = null) => ({ type: 'SINGLE_CHOICE', prompt, content: { options: options.map((text, i) => ({ id: 'o' + i, text })) }, answerKey: { correctOptionId: 'o' + correct }, explanation, materialVersionId });
    const fill = (prompt, before, answer, after) => ({ type: 'FILL_BLANK', prompt, content: { parts: [{ kind: 'TEXT', text: before }, { kind: 'BLANK', blankId: 'word' }, { kind: 'TEXT', text: after }] }, answerKey: { blanks: [{ blankId: 'word', acceptedAnswers: [answer] }], caseSensitive: false, normalizeWhitespace: true }, explanation: `Đáp án là “${answer}”.`, materialVersionId: null });
    const extras = [
      ['CHOICE', choice('Which activity do you usually do before breakfast?', ['Brush your teeth', 'Eat dinner', 'Go to bed', 'Watch the sunset'], 0, 'Brush your teeth là đánh răng, một hoạt động buổi sáng.')],
      ['CHOICE', choice('Choose the correct sentence about a daily routine.', ['She walks to school every day.', 'She walk to school every day.', 'She walking to school every day.', 'She to walk school every day.'], 0, 'Chủ ngữ she dùng walks trong hiện tại đơn.')],
      ['CHOICE', choice('Which phrase means “mỗi buổi sáng”?', ['Every morning', 'Last night', 'Next week', 'At midnight'], 0, 'Every morning nghĩa là mỗi buổi sáng.')],
      ['MATCHING', { type: 'MATCHING', prompt: 'Match the routine phrases with their meanings.', content: { leftItems: [{ id: 'l1', text: 'have breakfast' }, { id: 'l2', text: 'get dressed' }, { id: 'l3', text: 'leave home' }], rightItems: [{ id: 'r1', text: 'ăn sáng' }, { id: 'r2', text: 'mặc quần áo' }, { id: 'r3', text: 'rời nhà' }] }, answerKey: { pairs: [{ leftId: 'l1', rightId: 'r1' }, { leftId: 'l2', rightId: 'r2' }, { leftId: 'l3', rightId: 'r3' }] }, explanation: 'Have breakfast: ăn sáng; get dressed: mặc quần áo; leave home: rời nhà.', materialVersionId: null }],
      ['FILL', fill('Complete the phrase with “up”.', 'I wake ', 'up', ' at six every day.')],
      ['FILL', fill('Use the present-simple form of “brush”.', 'Nam ', 'brushes', ' his teeth after breakfast.')],
      ['FILL', fill('Use the present-simple form of “study”.', 'We ', 'study', ' English at school every Monday.')],
      ['LISTENING_CHOICE', choice('Listen. Where does Linh eat breakfast?', ['At school', 'At home', 'On the bus', 'At a café'], 1, 'Audio nói “She eats breakfast at home”.', audio.id)]
    ];
    const groups = { CHOICE: [], MATCHING: [], FILL: [], LISTENING_CHOICE: [] };
    for (const slot of lesson.questions.filter(s => s.practiceRole !== 'REVIEW_VARIANT')) {
      const q = byId.get(slot.questionVersionId); assert.ok(q);
      const kind = q.type === 'MATCHING' ? 'MATCHING' : q.type === 'FILL_BLANK' ? 'FILL' : materials.get(q.materialVersionId)?.kind === 'AUDIO' ? 'LISTENING_CHOICE' : 'CHOICE';
      groups[kind].push(slot.questionVersionId);
    }
    for (const [i, [kind, spec]] of extras.entries()) {
      const q = await action('question-' + i, '/api/author/questions', 'POST', { ...spec, topicId: anchor.topicId, objectiveId: anchor.objectiveId, difficulty: 'EASY', hint: null, source });
      groups[kind].push(q.draftVersionId);
    }
    assert.deepEqual(Object.values(groups).map(ids => ids.length), [6, 4, 6, 2]);
    const positions = { GUIDED: 0, INDEPENDENT: 0, REVIEW_VARIANT: 0 };
    const activities = Object.entries(groups).map(([kind, ids]) => ({ id: kind.toLowerCase(), kind, questionVersionIds: ids, passages: [] }));
    const questions = activities.flatMap(a => a.questionVersionIds.map((id, i) => ({ questionVersionId: id, practiceRole: i === 0 ? 'GUIDED' : 'INDEPENDENT', position: 0 }))).concat(lesson.questions.filter(q => q.practiceRole === 'REVIEW_VARIANT')).map(s => ({ ...s, position: positions[s.practiceRole]++ }));
    const draft = started.draft;
    await action('update-draft', `/api/author/lessons/${lessonId}/draft`, 'PUT', { draftVersionId: draft.id, baseRevision: draft.revision, title: lesson.title, description: '18 câu làm quen thói quen hằng ngày: trắc nghiệm, ghép cặp, điền từ và nghe. Chưa yêu cầu xếp từ hoặc hai phần đọc nâng cao.', instructions: [{ kind: 'TEXT', text: 'Bốn phần: 6 trắc nghiệm, 4 ghép cặp, 6 điền từ và 2 nghe chọn đáp án. Mỗi phần bắt đầu bằng một câu có hướng dẫn. Hoàn thành các câu rồi nộp bài.' }], objectiveIds: lesson.objectiveIds, questions, activities });
    if (!state.actions.publish) {
      const preview = await call(`/api/author/lessons/${lessonId}/preview`);
      assert.deepEqual(preview.errors, [], JSON.stringify(preview.errors));
      await action('publish', `/api/author/lessons/${lessonId}/publish`, 'POST', { baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken });
    } else await action('publish', `/api/author/lessons/${lessonId}/publish`, 'POST');
    console.log(`Đã nâng bài ${lessonId}, phiên bản ${state.actions.publish.response.publishedVersionId}: 18 câu, bốn dạng. Checkpoint: .test-state/baseline-flex/${scope}.json`);
  }
} finally { closeSync(lock); unlinkSync(lockPath); }
