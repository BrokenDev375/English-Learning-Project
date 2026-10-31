import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const compile = path => ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
const trackerCode = compile('../src/features/session/tracker.ts');
const tabCode = compile('../src/features/session/tab.ts');
const apiCode = compile('../src/api.ts');
const settle = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };
class ApiError extends Error { constructor(status, code) { super(code); this.status = status; this.code = code; } }
function fixture(options = {}) {
  let now = 0, serial = 0, epoch = 0, deadline = 300000, failed = false, generation = 'session-a', expiredTab = false;
  const timers = new Map(), calls = [], views = [], expired = [];
  const window = Object.assign(new EventTarget(), {
    setTimeout: (fn, delay) => { const id = ++serial; timers.set(id, { at: now + delay, fn }); return id; },
    clearTimeout: id => timers.delete(id),
    setInterval: (fn, delay) => { const id = ++serial; timers.set(id, { at: now + delay, fn, interval: delay }); return id; },
    clearInterval: id => timers.delete(id),
  });
  const document = Object.assign(new EventTarget(), { hidden: false });
  const location = { hash: options.hash ?? '#/home' };
  class Time extends Date { static now() { return now; } }
  const state = () => ({ sessionGeneration: generation, serverTime: new Date(now).toISOString(), lastInteractionAt: new Date(deadline - 300000).toISOString(), idleExpiresAt: new Date(deadline).toISOString(), warningAt: new Date(deadline - 30000).toISOString() });
  const api = async (path, method, body) => {
    calls.push({ path, body });
    if (failed) throw new ApiError(0, 'NETWORK_ERROR');
    if (now >= deadline) throw new ApiError(401, 'SESSION_IDLE_TIMEOUT');
    if (body?.attemptId && options.deniedAttempt) throw new ApiError(404, 'RESOURCE_NOT_FOUND');
    if (options.expireTab && body?.seq > 0 && !expiredTab) { expiredTab = true; throw new ApiError(409, 'TAB_EXPIRED'); }
    if (body?.interaction) deadline = now + 300000;
    return state();
  };
  const module = {};
  const ports = { api, ApiError, requestId: () => 'tab-' + ++serial, authenticationEpoch: () => epoch }, tab = {};
  new Function('require', 'exports', 'window', tabCode)(() => ports, tab, window);
  new Function('require', 'exports', 'window', 'document', 'navigator', 'location', 'Date', 'BroadcastChannel', trackerCode)(
    path => path === './tab' ? tab : ports, module,
    window, document, { onLine: true }, location, Time, undefined);
  window.addEventListener('session-expired', e => expired.push(e.detail));
  const tracker = new module.ActivityTracker(v => views.push(v)); tracker.start();
  async function advance(ms) {
    const until = now + ms;
    for (;;) {
      const next = [...timers].filter(([, t]) => t.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
      if (!next) break;
      const [id, t] = next; now = t.at; timers.delete(id);
      if (t.interval) timers.set(id, { ...t, at: now + t.interval });
      await t.fn(); await settle();
    }
    now = until; await settle();
  }
  return { tracker, calls, views, expired, timers, advance, window, location, fail: value => { failed = value; }, changeEpoch: () => epoch++, changeGeneration: () => { generation = 'session-b'; } };
}
{
  const f = fixture(); await settle(); await f.advance(270000);
  assert.equal(f.calls.filter(c => c.body?.interaction).length, 0);
  assert.equal(f.views.at(-1).warning, true); assert.equal(f.views.at(-1).remaining, 30);
  await f.tracker.continueWorking(); await settle(); assert.equal(f.views.at(-1).warning, false); f.tracker.stop();
}
{
  const f = fixture(); await settle(); await f.advance(15000);
  f.tracker.interaction({ isTrusted: false }); await f.advance(1000);
  assert.equal(f.calls.filter(c => c.body?.interaction).length, 0);
  f.tracker.interaction({ isTrusted: true }); await f.advance(0);
  for (let i = 0; i < 50; i++) f.tracker.interaction({ isTrusted: true });
  await f.advance(10000); assert.equal(f.calls.filter(c => c.body?.interaction).length, 2); f.tracker.stop();
}
{
  const f = fixture(); await settle(); f.fail(true); await f.advance(310000);
  assert.equal(f.expired.length, 0); assert.equal(f.views.at(-1).uncertain, true);
  f.fail(false); await f.tracker.check(); assert.equal(f.expired[0].code, 'SESSION_IDLE_TIMEOUT'); assert.equal(f.timers.size, 0);
}
{
  const f = fixture(); await settle(); await f.advance(300000);
  assert.equal(f.expired.length, 1); assert.equal(f.expired[0].code, 'SESSION_IDLE_TIMEOUT'); assert.equal(f.timers.size, 0);
}
{
  const f = fixture(); await settle(); const count = f.calls.length;
  f.changeEpoch(); await f.advance(20000); assert.equal(f.calls.length, count); assert.equal(f.expired.length, 0); f.tracker.stop();
}
{
  const f = fixture(); await settle(); const posts = f.calls.filter(c => c.body).length;
  f.changeGeneration(); await f.tracker.check(); assert.equal(f.expired[0].code, 'SESSION_CHANGED');
  assert.equal(f.calls.filter(c => c.body).length, posts);
}
{
  const f = fixture({ hash: '#/' }); await settle();
  assert.equal(f.calls.at(-1).body.activity, 'HOME');
  f.location.hash = '#/profile'; f.window.dispatchEvent(new Event('app-navigation')); await settle();
  assert.equal(f.calls.at(-1).body.activity, 'PROFILE');
  assert.equal(f.calls.at(-1).body.interaction, false); f.tracker.stop();
}
// An unauthorized Attempt context must not make an otherwise valid session unavailable.
{
  const f = fixture({ expireTab: true }); await settle(); const initial = f.calls.at(-1).body.tabId;
  await f.advance(16000);
  assert.notEqual(f.calls.at(-1).body.tabId, initial);
  assert.equal(f.calls.at(-1).body.seq, 0); assert.equal(f.calls.at(-1).body.interaction, false);
  f.tracker.interaction({ isTrusted: true }); await f.advance(0);
  assert.equal(f.calls.at(-1).body.interaction, true); assert.equal(f.expired.length, 0); f.tracker.stop();
}
{
  const f = fixture({ hash: '#/learning?attempt=42', deniedAttempt: true });
  await settle(); await f.advance(0);
  assert.equal(f.calls.at(-1).body.activity, 'OTHER');
  assert.equal(f.calls.at(-1).body.attemptId, undefined);
  assert.equal(f.calls.at(-1).body.interaction, false);
  assert.equal(f.views.at(-1).uncertain, false); assert.equal(f.expired.length, 0);
  f.location.hash = '#/account?attempt=42'; f.window.dispatchEvent(new Event('app-navigation')); await settle();
  assert.equal(f.calls.at(-1).body.activity, 'ACCOUNT'); assert.equal(f.calls.at(-1).body.attemptId, undefined);
  f.tracker.stop();
}
{
  const requests = [], events = [], pending = [];
  const window = Object.assign(new EventTarget(), { setTimeout, clearTimeout });
  window.addEventListener('session-expired', e => events.push(e));
  const fetch = (path) => { requests.push(path); return new Promise(resolve => pending.push(resolve)); };
  const module = {};
  new Function('exports', 'window', 'fetch', apiCode)(module, window, fetch);
  const old = module.api('/api/write', 'POST', {}); await settle(); module.advanceAuthentication();
  pending.shift()(Response.json({ token: 'old-token', headerName: 'X-CSRF-TOKEN' }));
  await assert.rejects(old, e => e.code === 'SESSION_CHANGED'); assert.deepEqual(requests, ['/api/auth/csrf']);
  const read = module.api('/api/private'); await settle(); module.advanceAuthentication();
  pending.shift()(Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 }));
  await assert.rejects(read, e => e.code === 'SESSION_CHANGED'); assert.equal(events.length, 0);
}
{
  const f = fixture({ hash: '#/account' }); await settle();
  assert.equal(f.calls.at(-1).body.activity, 'ACCOUNT', 'the account menu must not be OTHER');
  f.location.hash = '#/profile'; f.window.dispatchEvent(new Event('app-navigation')); await settle();
  assert.equal(f.calls.at(-1).body.activity, 'PROFILE', 'learning profile is a separate screen');
  f.location.hash = '#/learning?attempt=42'; f.window.dispatchEvent(new Event('app-navigation')); await settle();
  assert.equal(f.calls.at(-1).body.attemptId, 42);
  for (const [route, activity] of [['account', 'ACCOUNT'], ['profile', 'PROFILE']]) {
    f.location.hash = `#/${route}?attempt=42`; f.window.dispatchEvent(new Event('app-navigation')); await settle();
    assert.equal(f.calls.at(-1).body.activity, activity);
    assert.equal(f.calls.at(-1).body.attemptId, undefined, 'an old URL parameter must not turn another screen into LEARN');
  }
  assert.equal(f.calls.at(-1).body.interaction, false); f.tracker.stop();
}
console.log('PASS: 11 deterministic session scenarios, including distinct account and learning profile routes.');
