// Opt-in local fixture: all content, attempts and rewards use authenticated HTTP APIs.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, renameSync, mkdirSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { units, students, source } from './demo-classroom-data.mjs';

const base = new URL(process.env.DEMO_BASE_URL ?? 'http://localhost:5176');
assert.ok(['localhost', '127.0.0.1'].includes(base.hostname) && base.protocol === 'http:' && base.pathname === '/' && !base.username && !base.password,
  'This fixture only accepts a local HTTP origin.');
const apply = process.argv.includes('--apply');
if (!apply) {
  console.log(`Plan for ${base.origin}: ${students.length} fictional students, 2 Units, 4 lessons, 36 questions, 4 reading materials; real submissions/rewards and saved drafts. Run with --apply.`);
  process.exit(0);
}
const folder = fileURLToPath(new URL('../.test-state/demo-classroom/', import.meta.url));
mkdirSync(folder, { recursive: true });
const scope = createHash('sha256').update(base.origin).digest('hex').slice(0, 12);
const statePath = `${folder}${scope}.json`, lockPath = `${folder}${scope}.lock`;
const lock = openSync(lockPath, 'wx');
try {
let state;
try { state = JSON.parse(readFileSync(statePath, 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; state = { version: 1, origin: base.origin, actions: {} }; }
assert.equal(state.origin, base.origin);
assert.equal(state.version, 1);
const save = () => { writeFileSync(statePath + '.tmp', JSON.stringify(state, null, 2)); renameSync(statePath + '.tmp', statePath); };
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const envPath = fileURLToPath(new URL('../.env', import.meta.url));
// Read credentials in memory; never log the environment or persist passwords/cookies.
const config = Object.fromEntries(readFileSync(envPath, 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#') && s.includes('='))
  .map(s => { const i = s.indexOf('='); return [s.slice(0, i), s.slice(i + 1).replace(/^(["'])(.*)\1$/, '$2')]; }));
const password = 'Demo@Test12345';

class Client {
  cookies = new Map();
  constructor(label) { this.label = label; }
  async call(path, method = 'GET', body, headers = {}) {
    const response = await fetch(new URL(path, base), {
      method, headers: { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '), ...headers,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000), redirect: 'error',
    });
    for (const line of response.headers.getSetCookie()) {
      const pair = line.split(';')[0], i = pair.indexOf('='); this.cookies.set(pair.slice(0, i), pair.slice(i + 1));
    }
    const data = await response.json();
    if (!response.ok) {
      const error = new Error(`${this.label}: ${method} ${path}: HTTP ${response.status} ${data.code ?? data.error?.code ?? ''} ${JSON.stringify(data.fieldErrors ?? data.errors ?? '')}`);
      error.status = response.status; throw error;
    }
    return data;
  }
  async mutate(path, method, body) {
    const csrf = await this.call('/api/auth/csrf');
    return this.call(path, method, body, { [csrf.headerName]: csrf.token });
  }
  async login(email, secret) { return this.mutate('/api/auth/login', 'POST', { email, password: secret }); }
  async action(label, path, body, method = 'POST') {
    const id = `${this.label}/${label}`;
    let record = state.actions[id];
    if (record?.done) return record.data;
    if (!record) {
      record = state.actions[id] = { path, method, body: { ...body, requestId: randomUUID() } };
      save(); // Keep the exact key/payload before sending, including when the response is lost.
    }
    assert.equal(record.path, path); assert.equal(record.method, method);
    record.data = await this.mutate(record.path, record.method, record.body);
    record.done = true; save(); return record.data;
  }
}

async function publish(client, kind, root, label) {
  // Persist the preview-derived payload through action() before the first publish request.
  const id = `${client.label}/${label}/publish`;
  if (state.actions[id]?.done) return state.actions[id].data;
  let preview;
  if (!state.actions[id]) {
    preview = await client.call(`/api/author/${kind}/${root.id}/preview`);
    assert.equal(preview.errors.length, 0, `${label}: invalid publication ${JSON.stringify(preview.errors)}`);
  }
  return client.action(`${label}/publish`, `/api/author/${kind}/${root.id}/publish`, preview && {
    baseRevision: preview.baseRevision, draftVersionId: preview.draftVersionId, previewToken: preview.previewToken,
  });
}

const original = JSON.parse(readFileSync(new URL('../source/server/src/main/resources/demo/curriculum-v1.json', import.meta.url), 'utf8'));
const questionData = new Map(original.lessons.flatMap(l => l.questions.map(q => [q.data.prompt, q.data])));
for (const unit of units) for (const lesson of unit.lessons) for (const q of lesson.questions) {
  questionData.set(`${lesson.title} — ${q.prompt}`, q);
}
function answer(item, wrong = false) {
  const data = questionData.get(item.question.prompt);
  assert.ok(data, `Unknown fixture question: ${item.question.prompt}`);
  const key = data.answerKey, type = item.question.type;
  switch (type) {
    case 'SINGLE_CHOICE': return { type, optionId: wrong ? item.question.content.options.find(o => o.id !== key.correctOptionId).id : key.correctOptionId };
    case 'FILL_BLANK': return { type, values: key.blanks.map(b => ({ blankId: b.blankId, value: wrong ? 'demo incorrect' : b.acceptedAnswers[0] })) };
    case 'MATCHING': return { type, pairs: key.pairs.map((p, i) => ({ leftId: p.leftId, rightId: wrong ? key.pairs[(i + 1) % key.pairs.length].rightId : p.rightId })) };
    case 'ORDERING': return { type, tokenIds: wrong ? [...key.acceptedOrders[0]].reverse() : key.acceptedOrders[0] };
    default: throw new Error(`Unsupported question type ${type}`);
  }
}
async function all(client, path) {
  const items = [];
  for (let page = 0; ; page++) {
    const result = await client.call(`${path}${path.includes('?') ? '&' : '?'}size=50&page=${page}`);
    items.push(...result.items); if (page + 1 >= result.totalPages) return items;
  }
}
async function createContent(teacher) {
  const created = [];
  for (const [unitIndex, spec] of units.entries()) {
    const topic = await teacher.action(`${spec.code}/topic`, '/api/author/topics', { code: spec.code, name: spec.title, description: spec.description });
    const objective = await teacher.action(`${spec.code}/objective`, '/api/author/objectives', { code: `${spec.code}-reading`, topicId: topic.id, name: `Đọc hiểu ${spec.title}`, description: spec.description });
    const unit = await teacher.action(`${spec.code}/unit`, '/api/author/units', { topicId: topic.id, title: spec.title, description: spec.description, stage: 1, sortOrder: 100 + unitIndex });
    for (const [lessonIndex, lesson] of spec.lessons.entries()) {
      const label = `${spec.code}/lesson-${lessonIndex}`;
      const material = await teacher.action(`${label}/material`, '/api/author/materials', {
        kind: 'READING', title: `[Demo] ${lesson.title}`, readingText: lesson.text, assetId: null, altText: null, source,
      });
      await publish(teacher, 'materials', material, `${label}/material`);
      const currentMaterial = await teacher.call(`/api/author/materials/${material.id}`);
      assert.ok(currentMaterial.publishedVersionId, 'Demo material must remain published.');
      const slots = [];
      for (const [index, q] of lesson.questions.entries()) {
        const question = await teacher.action(`${label}/question-${index}`, '/api/author/questions', {
          ...q, prompt: `${lesson.title} — ${q.prompt}`, difficulty: 'EASY', topicId: topic.id, objectiveId: objective.id,
          hint: 'Read the passage and look for the people, times and activities.', materialVersionId: currentMaterial.publishedVersionId, source,
        });
        slots.push({ questionVersionId: question.draftVersionId, practiceRole: ['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT'][Math.floor(index / 3)], position: index % 3 });
      }
      // Creating/publishing an earlier lesson can change the Unit revision.
      const currentUnit = (await all(teacher, '/api/author/units')).find(u => u.id === unit.id);
      assert.ok(currentUnit);
      const root = await teacher.action(`${label}/create`, '/api/author/lessons', {
        unitId: unit.id, unitBaseRevision: currentUnit.revision, title: `[Demo] ${lesson.title}`, description: spec.description,
        instructions: [{ kind: 'TEXT', text: 'Đọc đoạn văn. Trả lời câu hỏi, ghép cặp và sắp xếp từ. Bạn có thể dùng gợi ý ở phần hướng dẫn.' }],
        objectiveIds: [objective.id], questions: slots,
      });
      await publish(teacher, 'lessons', root, label);
      const current = await teacher.call(`/api/author/lessons/${root.id}`);
      assert.ok(current.publishedVersionId, 'Demo lesson must remain published.');
      assert.equal(current.published.questions.length, 9);
      created.push({ unitId: unit.id, lessonId: root.id, title: lesson.title });
    }
    console.log(`Ready: ${spec.title}`);
  }
  return created;
}

async function seedStudent(spec) {
  const client = new Client(spec.email);
  try { await client.login(spec.email, password); }
  catch (error) {
    if (error.status !== 401) throw error;
    await client.mutate('/api/auth/register', 'POST', { email: spec.email, password, fullName: spec.name });
    await client.login(spec.email, password);
  }
  const me = await client.call('/api/auth/me');
  assert.equal(me.role, 'STUDENT'); assert.equal(me.fullName, spec.name, 'Refuse to reuse an unrelated account.');
  // Join before submitting so the real worker can count this week's eligible XP.
  const league = await client.call('/api/me/league');
  await client.action(`league-${league.season.id}`, '/api/me/league/join', {});
  const rounds = await all(client, '/api/me/rounds');
  const lessons = [];
  for (const round of rounds) lessons.push(...(await client.call(`/api/units/${round.unit.id}`)).lessons);
  assert.ok(lessons.length >= spec.lessons);
  const results = [];
  for (const [index, lesson] of lessons.slice(0, spec.lessons).entries()) {
    const label = `learn-${lesson.id}`;
    const snapshot = await client.action(`${label}/start`, '/api/attempts', { mode: 'LEARN', lessonId: lesson.id });
    const answers = snapshot.items.map((item, i) => ({ attemptQuestionId: item.attemptQuestionId,
      response: answer(item, spec.wrongEvery > 0 && (i + index + 1) % spec.wrongEvery === 0) }));
    const result = await client.action(`${label}/submit`, `/api/attempts/${snapshot.attemptId}/submit`, {
      baseRevision: snapshot.revision, playerState: snapshot.playerState, answers,
    });
    assert.equal(result.answeredCount, result.totalQuestions); assert.equal(result.lessonCompleted, true);
    await client.action(`${label}/complete`, `/api/attempts/${snapshot.attemptId}/complete`, { baseRevision: result.submittedRevision });
    const stored = await client.call(`/api/attempts/${snapshot.attemptId}/result`);
    assert.equal(stored.submissionId, result.submissionId); assert.equal(stored.scorePercent, result.scorePercent);
    results.push({ attemptId: snapshot.attemptId, lessonId: lesson.id, scorePercent: result.scorePercent });
  }
  const next = lessons[spec.lessons];
  if (next) {
    const snapshot = await client.action('unfinished/start', '/api/attempts', { mode: 'LEARN', lessonId: next.id });
    await client.action('unfinished/save', `/api/attempts/${snapshot.attemptId}/draft`, {
      baseRevision: snapshot.revision, playerState: snapshot.playerState,
      answers: snapshot.items.map((item, i) => ({ attemptQuestionId: item.attemptQuestionId, response: i < 2 ? answer(item) : null })),
    }, 'PUT');
    const restored = await client.call(`/api/attempts/${snapshot.attemptId}`);
    assert.equal(restored.state, 'IN_PROGRESS'); assert.equal(restored.items.filter(i => i.response !== null).length, 2);
  }
  // Synchronize through the public services; never edit FIRST, ledgers or timestamps.
  for (let i = 0; i < 10; i++) {
    await client.mutate('/api/me/review/sync', 'POST', { requestId: randomUUID() });
    await client.mutate('/api/me/gamification/sync', 'POST', { requestId: randomUUID() });
    const summary = await client.call('/api/me/gamification');
    if (summary.backlog.pendingSubmissions === 0 && summary.backlog.deferredSubmissions === 0) break;
    assert.ok(i < 9, 'Reward backlog did not drain.'); await delay(300);
  }
  const summary = await client.call('/api/me/gamification');
  const history = await all(client, '/api/attempts');
  const membership = (await client.call('/api/me/league')).membership;
  const statistics = await client.call('/api/me/statistics');
  assert.ok(summary.totalXp > 0); assert.ok(membership.weeklyXp > 0);
  assert.ok(history.filter(a => a.state === 'SUBMITTED').length >= spec.lessons);
  assert.equal(statistics.rewards.totalXp, summary.totalXp);
  assert.equal(statistics.activity.submittedAttempts, history.filter(a => a.state === 'SUBMITTED').length);
  assert.equal(statistics.review.pendingSubmissions, 0);
  console.log(`${spec.name}: ${results.length} lessons, ${summary.totalXp} XP, weekly ${membership.weeklyXp}, ${next ? '1 draft' : 'all lessons completed'}`);
  return { email: spec.email, name: spec.name, totalXp: summary.totalXp, weeklyXp: membership.weeklyXp, results,
    submittedAttempts: statistics.activity.submittedAttempts, activeAttempts: statistics.activity.activeAttempts,
    completedLessons: statistics.activity.completedLessons, draft: Boolean(next), client };
}

  const teacher = new Client('teacher');
  const actor = await teacher.login(process.env.DEMO_TEACHER_EMAIL ?? config.DEMO_TEACHER_EMAIL ?? 'teacher@english.local',
    process.env.DEMO_TEACHER_PASSWORD ?? config.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345');
  assert.equal(actor.role, 'TEACHER');
  if (state.teacherId) assert.equal(state.teacherId, actor.id, 'Teacher identity changed; refusing to replay.');
  state.teacherId = actor.id; save();
  const created = await createContent(teacher);
  const reports = [];
  for (const spec of students) reports.push(await seedStudent(spec));
  const board = await reports[0].client.call('/api/me/leaderboard?size=50');
  for (const report of reports) assert.ok(board.items.some(row => row.displayName === report.name && row.weeklyXp === report.weeklyXp), `${report.name} missing from weekly board`);
  for (let i = 1; i < board.items.length; i++) assert.ok(board.items[i - 1].weeklyXp >= board.items[i].weeklyXp);
  const group = await reports[0].client.call('/api/me/league/leaderboard');
  assert.ok(group.items.length >= students.length, 'Expected demo members in a league group.');
  // Exercise server receipt replay as well as the local checkpoints.
  const first = reports[0], before = await all(first.client, '/api/attempts');
  const receipt = state.actions[`${first.email}/learn-${first.results[0].lessonId}/submit`];
  const replayed = await first.client.mutate(receipt.path, receipt.method, receipt.body);
  assert.equal(replayed.submissionId, receipt.data.submissionId);
  assert.equal((await all(first.client, '/api/attempts')).length, before.length);
  assert.equal((await first.client.call('/api/me/gamification')).totalXp, first.totalXp);
  const report = { checkedAt: new Date().toISOString(), origin: base.origin, content: { units: units.length, lessons: created.length, questions: 36, readingMaterials: 4 },
    students: reports.map(({ client, ...data }) => data), leaderboard: board, league: group };
  writeFileSync(`${folder}report.json`, JSON.stringify(report, null, 2));
  console.log('PASS: publications, submissions, drafts, statistics, rewards, both leaderboards and receipt replay. Report: .test-state/demo-classroom/report.json');
} finally {
  closeSync(lock); unlinkSync(lockPath);
}
