import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { mkdirSync, appendFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout } from 'node:timers/promises';
import { request } from '@playwright/test';

// Always owns a separate Compose project; never accepts a demo/shared API URL.
const root = fileURLToPath(new URL('../../../', import.meta.url));
const evidence = resolve(root, '.test-state');
mkdirSync(evidence, { recursive: true });
const log = resolve(evidence, 'm03-step6-persistence-docker.log');
writeFileSync(log, 'M03 isolated restart/recreate verification\n');
const env = { ...process.env, APP_PORT: '5175', API_PORT: '8082', DEMO_ENABLED: 'true',
  BOOTSTRAP_ADMIN_ENABLED: 'true', WS_ALLOWED_ORIGINS: 'http://127.0.0.1:5175,http://localhost:5175' };
const composeArgs = ['compose', '--project-name', 'english-learning-m03-acceptance', '--file', 'compose.yml'];
function docker(args) {
  const result = spawnSync('docker', [...composeArgs, ...args], { cwd: root, env, encoding: 'utf8', timeout: 600000 });
  appendFileSync(log, `\n> compose ${args.join(' ')}\n${result.stdout ?? ''}${result.stderr ?? ''}`);
  if (result.error || result.status !== 0) throw new Error(`Compose ${args[0]} failed; inspect ${log}`);
  return result.stdout.trim();
}
async function ready(since) {
  for (let i = 0; i < 90; i++) {
    const logs = docker(['logs', '--no-color', ...(since ? ['--since', since] : []), 'server']);
    if (logs.includes('M02 curriculum seed completed:')) return logs.match(/M02 curriculum seed completed: [^\r\n]+/g).at(-1);
    await setTimeout(2000);
  }
  throw new Error('Curriculum seed did not finish.');
}
const tables = ['topics', 'units', 'learning_objectives', 'lessons', 'lesson_versions', 'questions', 'question_versions',
  'materials', 'material_versions', 'assets', 'attempts', 'attempt_questions', 'attempt_answers',
  'answer_trials', 'submissions', 'question_exposures', 'lesson_progress', 'request_receipts'];
function counts() {
  const sql = tables.map(t => `SELECT '${t}', COUNT(*) FROM ${t}`).join(';');
  // Credentials stay inside the MySQL container environment, never command output.
  const rows = docker(['exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B -e "$1"', 'm03-counts', sql]);
  return Object.fromEntries(rows.split(/\r?\n/).map(row => {
    const [name, count] = row.split('\t'); return [name, Number(count)];
  }));
}
function response(item) {
  const c = item.question.content;
  switch (item.question.type) {
    case 'SINGLE_CHOICE': return { type: 'SINGLE_CHOICE', optionId: c.options[0].id };
    case 'FILL_BLANK': return { type: 'FILL_BLANK', values: c.parts.filter(p => p.kind === 'BLANK').map(p => ({ blankId: p.blankId, value: 'demo' })) };
    case 'MATCHING': return { type: 'MATCHING', pairs: c.leftItems.map((left, i) => ({ leftId: left.id, rightId: c.rightItems[i].id })) };
    default: throw new Error('Unsupported question type');
  }
}
const credentials = { email: `m03-persistence-${randomUUID()}@test.local`, password: `Test@${randomUUID()}` };
let client;
let csrf;
async function call(method, path, data, status = 200) {
  const r = await client.fetch(path, { method, data, headers: method === 'GET' ? {} : { [csrf.headerName]: csrf.token } });
  assert.equal(r.status(), status, `${method} ${path}: expected ${status}, got ${r.status()}`);
  return r;
}
async function json(method, path, data, status) { return (await call(method, path, data, status)).json(); }
async function login(register = false) {
  await client?.dispose();
  client = await request.newContext({ baseURL: 'http://127.0.0.1:5175', timeout: 30000 });
  csrf = await json('GET', '/api/auth/csrf');
  if (register) await json('POST', '/api/auth/register', { ...credentials, fullName: 'M03 persistence acceptance' }, 201);
  await json('POST', '/api/auth/login', credentials);
  csrf = await json('GET', '/api/auth/csrf');
}
const summary = { source: spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).stdout.trim(), checkpoints: [] };
let started = false;
try {
  started = true;
  docker(['up', '-d', '--build', '--wait']);
  await ready();
  await login(true);
  const units = await json('GET', '/api/units');
  const unit = await json('GET', `/api/units/${units.items[0].id}`);
  assert.ok(unit.lessons.length >= 2);
  let completed = await json('POST', '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: unit.lessons[0].id }, 201);
  const completedPath = `/api/attempts/${completed.attemptId}`;
  for (const item of completed.items.filter(i => i.practiceRole === 'GUIDED')) {
    await json('POST', `${completedPath}/items/${item.attemptQuestionId}/check`, {
      requestId: randomUUID(), baseRevision: completed.revision, response: response(item),
    });
    completed = await json('GET', completedPath);
  }
  const submit = { requestId: randomUUID(), baseRevision: completed.revision,
    answers: completed.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: response(i) })), playerState: completed.playerState };
  const result = await json('POST', `${completedPath}/submit`, submit);
  assert.equal(result.lessonCompleted, true);
  completed = await json('GET', completedPath);
  // A correction must also survive without changing the immutable result.
  await json('POST', `${completedPath}/items/${completed.items[0].attemptQuestionId}/retry`, {
    requestId: randomUUID(), baseRevision: completed.revision, response: response(completed.items[0]),
  });
  let draft = await json('POST', '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: unit.lessons[1].id }, 201);
  const draftPath = `/api/attempts/${draft.attemptId}`;
  const audio = draft.materials.find(m => m.kind === 'AUDIO');
  assert.ok(audio?.asset?.url, 'Seed lesson must include persisted audio');
  const save = { requestId: randomUUID(), baseRevision: draft.revision,
    answers: draft.items.map((i, n) => ({ attemptQuestionId: i.attemptQuestionId, response: n === 0 ? response(i) : null })),
    playerState: { currentAttemptQuestionId: draft.items[0].attemptQuestionId,
      audioPositions: [{ materialVersionId: audio.materialVersionId, seconds: 2 }] } };
  const ack = await json('PUT', `${draftPath}/draft`, save);
  const progressPath = `/api/me/learning-progress?unitId=${units.items[0].id}`;
  async function snapshot() {
    return { completed: await json('GET', completedPath), draft: await json('GET', draftPath),
      result: await json('GET', `${completedPath}/result`), progress: await json('GET', progressPath), counts: counts() };
  }
  const baseline = await snapshot();
  assert.deepEqual(baseline.result, result);
  assert.equal(baseline.progress.items.find(p => p.lessonId === unit.lessons[0].id).state, 'COMPLETED');
  assert.equal(baseline.progress.items.find(p => p.lessonId === unit.lessons[1].id).state, 'STARTED');
  assert.equal(baseline.counts.submissions >= 1, true);
  const audioHash = createHash('sha256').update(await (await call('GET', audio.asset.url)).body()).digest('hex');
  const beforeIds = Object.fromEntries(['mysql', 'server', 'client'].map(s => [s, docker(['ps', '-q', s])]));
  for (const stage of ['restart', 'recreate']) {
    console.log(`Checking ${stage}: database, media, drafts, result, progress and receipts...`);
    const since = new Date().toISOString();
    if (stage === 'restart') docker(['restart', 'server']);
    else docker(['up', '-d', '--force-recreate', '--no-build', '--wait']);
    const seed = await ready(since);
    // Exercise the old cookie before explicitly signing in again.
    const old = await client.get(draftPath);
    assert.equal(old.status(), 401, 'Backend restart must invalidate its in-memory session');
    await login();
    assert.deepEqual(await snapshot(), baseline, `${stage} changed persisted learning/content data`);
    for (const [method, path, payload, expected] of [
      ['PUT', `${draftPath}/draft`, save, ack], ['POST', `${completedPath}/submit`, submit, result],
    ]) {
      const replay = await call(method, path, payload);
      assert.equal(replay.headers()['idempotent-replayed'], 'true');
      assert.deepEqual(await replay.json(), expected);
    }
    assert.deepEqual(counts(), baseline.counts, 'Replay duplicated rows');
    assert.equal(createHash('sha256').update(await (await call('GET', audio.asset.url)).body()).digest('hex'), audioHash);
    const range = await client.get(audio.asset.url, { headers: { Range: 'bytes=0-15' } });
    assert.equal(range.status(), 206); assert.equal((await range.body()).length, 16);
    const afterIds = Object.fromEntries(['mysql', 'server', 'client'].map(s => [s, docker(['ps', '-q', s])]));
    if (stage === 'restart') assert.deepEqual(afterIds, beforeIds);
    else for (const s of ['mysql', 'server', 'client']) assert.notEqual(afterIds[s], beforeIds[s]);
    summary.checkpoints.push({ stage, status: 'PASS', oldSessionStatus: old.status(), seed,
      counts: baseline.counts, identicalSnapshots: true, receiptReplay: true, mediaHashAndRange: true,
      containerIds: afterIds });
    console.log(`PASS: ${stage}; login restored exact snapshots/results/progress; seed/replay did not add rows.`);
  }
  summary.status = 'PASS';
} catch (error) {
  summary.status = 'FAIL'; summary.error = error.message;
  throw error;
} finally {
  await client?.dispose();
  if (started) {
    try { docker(['stop']); }
    catch (error) { summary.status = 'FAIL'; summary.stopError = error.message; process.exitCode = 1; }
  }
  writeFileSync(resolve(evidence, 'm03-step6-persistence.json'), JSON.stringify(summary, null, 2) + '\n');
}
