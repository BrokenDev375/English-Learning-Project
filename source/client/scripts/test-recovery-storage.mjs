import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import ts from 'typescript';
import { chromium } from '@playwright/test';

// Actual storage/journal modules, real browser IndexedDB; only HTTP is controlled.
const compile = path => ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
const modules = {
  database: compile('../src/features/learning/storage/database.ts'),
  journal: compile('../src/features/learning/storage/journal.ts'),
  validation: compile('../src/features/learning/storage/validation.ts'),
};
const server = createServer((_, response) => { response.setHeader('Content-Type', 'text/html'); response.end('<!doctype html><title>Recovery storage tests</title>'); });
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
let browser;
let passed = 0;
async function check(name, run) {
  const context = await browser.newContext();
  try {
    const page = await context.newPage(); await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.evaluate(modules => {
      const api = { requestId: () => crypto.randomUUID(), ApiError: class extends Error { constructor(status) { super('Controlled HTTP error'); this.status = status; } }, api: async () => { throw new Error('Unexpected HTTP'); } };
      const database = {}, journal = {}, validation = {};
      new Function('require', 'exports', modules.database)(() => api, database);
      new Function('require', 'exports', modules.validation)(() => ({}), validation);
      new Function('require', 'exports', modules.journal)(path => path.endsWith('/database') ? database : api, journal);
      database.activateStorage(7);
      window.recovery = { api, database, journal, validation };
    }, modules);
    await run(page); passed++; console.log(`PASS: ${name}`);
  } finally { await context.close(); }
}
try {
  browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH });
  await check('pruning cannot erase a concurrently committed dirty draft', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, validation } = window.recovery, now = Date.now();
      const rows = Array.from({ length: 101 }, (_, n) => {
        const playerState = { currentAttemptQuestionId: 1, audioPositions: [] };
        const snapshot = { attemptId: n + 1, mode: 'PRACTICE', state: 'IN_PROGRESS', revision: 0, lessonVersionId: null,
          createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), submittedAt: null, submissionId: null,
          items: [{ attemptQuestionId: 1, questionVersionId: 1, originKey: crypto.randomUUID(), objectiveId: 1, difficulty: 'EASY', practiceRole: 'INDEPENDENT', position: 0,
            wasSeenBefore: false, hintAvailable: false, hintOpened: false, feedbackOpened: false,
            question: { type: 'SINGLE_CHOICE', prompt: 'Choose.', content: { options: [{ id: 'a', text: 'A' }, { id: 'b', text: 'B' }] } }, materialVersionId: null, response: null }], materials: [], playerState, lesson: null };
        return { key: `7:${n + 1}:other-writer`, userId: 7, version: 1, attemptId: n + 1, writerId: 'other-writer', generation: 0, updatedAt: now - n,
          state: { snapshot, draft: { answers: [{ attemptQuestionId: 1, response: null }], playerState }, result: null, pending: null, pendingAck: null, conflict: null, hints: {}, feedback: {} } };
      });
      if (!rows.every(row => validation.validRecoveryState(row.state))) throw new Error('Invalid clean fixture');
      const target = structuredClone(rows.at(-1)); target.generation++; target.updatedAt = now + 100;
      target.state.draft.answers[0].response = { type: 'SINGLE_CHOICE', optionId: 'b' };
      if (!validation.validRecoveryState(target.state)) throw new Error('Invalid dirty fixture');
      await db.writeRows(7, db.userEpoch(), { workingDrafts: rows });
      const original = IDBDatabase.prototype.transaction; let once = true, writer;
      IDBDatabase.prototype.transaction = function (...args) {
        const transaction = original.apply(this, args);
        if (once && args[0] === 'workingDrafts') {
          once = false;
          // The competing writer commits after the first pruning transaction.
          // Separate read/delete transactions would then erase its fresh answer.
          transaction.addEventListener('complete', () => { writer = db.writeRows(7, db.userEpoch(), { workingDrafts: [target] }); });
        }
        return transaction;
      };
      try { await db.pruneCleanDrafts(7); await writer; } finally { IDBDatabase.prototype.transaction = original; }
      const remaining = await db.readRows('workingDrafts', 7), retained = remaining.find(row => row.key === target.key);
      await db.pruneCleanDrafts(7);
      return { answer: retained?.state.draft.answers[0].response?.optionId, generation: retained?.generation,
        retainedAfterNextPrune: (await db.readRows('workingDrafts', 7)).some(row => row.key === target.key), count: remaining.length };
    });
    assert.equal(result.answer, 'b'); assert.equal(result.generation, 1); assert.equal(result.retainedAfterNextPrune, true);
    assert.ok(result.count <= 101);
  });
  await check('selected B is persisted while A awaits HTTP; only B is returned', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, journal, api } = window.recovery;
      const a = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 11 }, b = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 22 };
      await db.writeRows(7, db.userEpoch(), { commands: [{ key: '7:' + a.requestId, userId: 7, version: 1, kind: 'create', body: a, status: 'PREPARED' }] });
      const posted = []; let release, started;
      const waiting = new Promise(resolve => { started = resolve; });
      api.api = async (path, method, body) => {
        if (method === 'GET') return { attemptId: Number(path.split('/').at(-1)) };
        posted.push(structuredClone(body));
        if (body.requestId === a.requestId) { started(); await new Promise(resolve => { release = resolve; }); }
        return { attemptId: body.lessonId + 100, lessonId: body.lessonId };
      };
      const job = journal.durableCall(7, 'create', b); await waiting;
      const pending = await db.readRows('commands', 7), selected = journal.latestCreateCommand(pending).body.requestId;
      release(); const answer = await job;
      return { lesson: answer.lessonId, posted: posted.map(row => row.lessonId), selectedB: selected === b.requestId,
        pendingB: pending.some(row => row.body.requestId === b.requestId), remaining: (await db.readRows('commands', 7)).length };
    });
    assert.deepEqual(result, { lesson: 22, posted: [11, 22], selectedB: true, pendingB: true, remaining: 0 });
  });
  await check('failed recovery keeps B for reload and replays immutable A before B', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, journal, api } = window.recovery;
      const a = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 11 }, b = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 22 };
      await db.writeRows(7, db.userEpoch(), { commands: [{ key: '7:' + a.requestId, userId: 7, version: 1, kind: 'create', body: a, status: 'PREPARED' }] });
      const posted = []; let fail = true;
      api.api = async (path, method, body) => {
        if (method === 'GET') return { attemptId: Number(path.split('/').at(-1)) };
        posted.push(structuredClone(body)); if (fail) throw new api.ApiError(0);
        return { attemptId: body.lessonId + 100, lessonId: body.lessonId };
      };
      let failed = false; try { await journal.durableCall(7, 'create', b); } catch { failed = true; }
      const stored = await db.readRows('commands', 7), recovered = journal.latestCreateCommand(stored).body;
      fail = false; const reply = await journal.durableCall(7, 'create', recovered);
      return { failed, pending: stored.length, recoveredB: JSON.stringify(recovered) === JSON.stringify(b), lesson: reply.lessonId,
        ids: posted.map(row => row.requestId), expectedIds: [a.requestId, a.requestId, b.requestId], immutableA: JSON.stringify(posted[0]) === JSON.stringify(posted[1]) };
    });
    assert.equal(result.failed, true); assert.equal(result.pending, 2); assert.equal(result.recoveredB, true);
    assert.equal(result.lesson, 22); assert.deepEqual(result.ids, result.expectedIds); assert.equal(result.immutableA, true);
  });
  await check('a request ID cannot be reused with a different create payload', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, journal, api } = window.recovery, a = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 11 };
      await db.writeRows(7, db.userEpoch(), { commands: [{ key: '7:' + a.requestId, userId: 7, version: 1, kind: 'create', body: a, status: 'PREPARED' }] });
      let calls = 0, rejected = false; api.api = async () => { calls++; };
      try { await journal.durableCall(7, 'create', { ...a, lessonId: 22 }); } catch { rejected = true; }
      return { calls, rejected, lesson: (await db.readRows('commands', 7))[0].body.lessonId };
    });
    assert.deepEqual(result, { calls: 0, rejected: true, lesson: 11 });
  });
  await check('definitively rejected older create does not replace or block B', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, journal, api } = window.recovery, a = { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 11 };
      await db.writeRows(7, db.userEpoch(), { commands: [{ key: '7:' + a.requestId, userId: 7, version: 1, kind: 'create', body: a, status: 'PREPARED' }] });
      api.api = async (path, method, body) => { if (method === 'GET') return { attemptId: 122 }; if (body.lessonId === 11) throw new api.ApiError(404); return { attemptId: 122, lessonId: body.lessonId }; };
      return (await journal.durableCall(7, 'create', { requestId: crypto.randomUUID(), mode: 'LEARN', lessonId: 22 })).lessonId;
    });
    assert.equal(result, 22);
  });
  await check('review sync still replays its pending payload before clearing it', async page => {
    const result = await page.evaluate(async () => {
      const { database: db, journal, api } = window.recovery, original = { requestId: crypto.randomUUID() };
      await db.writeRows(7, db.userEpoch(), { commands: [{ key: '7:' + original.requestId, userId: 7, version: 1, kind: 'review-sync', body: original, status: 'PREPARED' }] });
      let sent; api.api = async (_path, _method, body) => { sent = body; return { remainingSubmissions: 0 }; };
      const reply = await journal.durableCall(7, 'review-sync', { requestId: crypto.randomUUID() });
      return { originalSent: JSON.stringify(sent) === JSON.stringify(original), remaining: reply.remainingSubmissions, pending: (await db.readRows('commands', 7)).length };
    });
    assert.deepEqual(result, { originalSent: true, remaining: 0, pending: 0 });
  });
  console.log(`PASS: ${passed} browser IndexedDB/journal scenarios.`);
} finally { await browser?.close(); await new Promise(resolve => server.close(resolve)); }
