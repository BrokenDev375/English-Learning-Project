import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

// Execute the actual manager module against deterministic clock/transport/auth ports.
const code = ts.transpileModule(readFileSync(new URL('../src/features/connection/manager.ts', import.meta.url), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
class ApiError extends Error { constructor(status) { super('test'); this.status = status; } }
function fixture(authenticated = true, authenticate = async () => { if (!authenticated) throw new ApiError(401); return { id: 7 }; }) {
  let now = 0, serial = 0;
  const timers = new Map(); const sockets = [];
  const window = Object.assign(new EventTarget(), {
    setTimeout: (fn, delay) => { const id = ++serial; timers.set(id, { at: now + delay, fn }); return id; },
    clearTimeout: id => timers.delete(id),
    setInterval: (fn, delay) => { const id = ++serial; timers.set(id, { at: now + delay, fn, interval: delay }); return id; },
    clearInterval: id => timers.delete(id),
  });
  class Socket {
    static OPEN = 1; readyState = 1; sent = [];
    constructor() { sockets.push(this); }
    send(data) { this.sent.push(JSON.parse(data)); }
    message(message) { this.onmessage?.({ data: JSON.stringify({ version: 1, ...message }) }); }
    async close(code = 1000) { if (this.readyState === 3) return; this.readyState = 3; await this.onclose?.({ code }); }
  }
  const module = {};
  new Function('require', 'exports', 'window', 'location', 'WebSocket', code)(() => ({ ApiError, requestId: () => 'request-' + ++serial, api: authenticate }), module, window, { protocol: 'http:', host: 'test.local' }, Socket);
  async function advance(ms) {
    const until = now + ms;
    for (;;) {
      const item = [...timers].filter(([, t]) => t.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
      if (!item) break;
      const [id, timer] = item; now = timer.at; timers.delete(id);
      if (timer.interval) timers.set(id, { ...timer, at: now + timer.interval });
      await timer.fn(); await Promise.resolve();
    }
    now = until;
  }
  return { module, sockets, window, advance, timers };
}

{
  const f = fixture(); const manager = new f.module.ConnectionManager(7, () => 0); manager.start();
  const socket = f.sockets[0]; socket.message({ type: 'CONNECTED', userId: 7 });
  socket.message({ type: 'ACK', requestId: socket.sent.at(-1).requestId }); assert.equal(f.module.connectionSnapshot(), 'connected');
  let invalidation; f.window.addEventListener('attempt-invalidation', e => { invalidation = e.detail; });
  manager.subscribe(42); socket.message({ type: 'ACK', requestId: socket.sent.at(-1).requestId }); assert.equal(invalidation.attemptId, 42);
  await f.advance(25000); assert.equal(socket.sent.at(-1).type, 'HEARTBEAT'); await f.advance(10000); assert.equal(f.module.connectionSnapshot(), 'interrupted');
  await f.advance(749); assert.equal(f.sockets.length, 1); await f.advance(1); assert.equal(f.sockets.length, 2); manager.stop();
}
{
  const f = fixture(); const manager = new f.module.ConnectionManager(7, () => 0); manager.start();
  for (let attempt = 0; attempt < 8; attempt++) {
    await f.advance(10000); const wait = Math.min(30000, 1000 * 2 ** Math.min(attempt, 5)) * 0.75;
    const count = f.sockets.length; await f.advance(wait - 1); assert.equal(f.sockets.length, count); await f.advance(1); assert.equal(f.sockets.length, count + 1);
  }
  manager.stop(); const count = f.sockets.length; await f.advance(100000); assert.equal(f.sockets.length, count);
}
{
  const f = fixture(false); const manager = new f.module.ConnectionManager(7, () => 0); let expired = 0; f.window.addEventListener('session-expired', () => expired++); manager.start();
  await f.sockets[0].close(1008); assert.equal(f.module.connectionSnapshot(), 'auth-required'); assert.equal(expired, 1); await f.advance(60000); assert.equal(f.sockets.length, 1); manager.stop();
}
{
  const f = fixture(); const manager = new f.module.ConnectionManager(7, () => 1); manager.start(); const old = f.sockets[0];
  old.message({ type: 'CONNECTED', userId: 7 }); old.message({ type: 'ACK', requestId: 'wrong' }); assert.equal(f.module.connectionSnapshot(), 'connecting');
  await f.advance(10000); assert.equal(f.module.connectionSnapshot(), 'interrupted'); await f.advance(1000); assert.equal(f.sockets.length, 2);
  old.message({ type: 'CONNECTED', userId: 7 }); assert.equal(old.sent.length, 1); manager.stop();
}
{
  const f = fixture(false); const manager = new f.module.ConnectionManager(7, () => 0); manager.start();
  await f.sockets[0].close(1006); assert.equal(f.module.connectionSnapshot(), 'auth-required'); await f.advance(60000); assert.equal(f.sockets.length, 1); manager.stop();
}
for (const outcome of ['401', 'success']) {
  let resolveAuth, rejectAuth;
  const pendingAuth = new Promise((resolve, reject) => { resolveAuth = resolve; rejectAuth = reject; });
  const f = fixture(true, () => pendingAuth), manager = new f.module.ConnectionManager(7, () => 0);
  let expired = 0; f.window.addEventListener('session-expired', () => expired++); manager.start();
  const oldClose = f.sockets[0].close(1006);
  f.window.dispatchEvent(new Event('focus'));
  const replacement = f.sockets[1]; replacement.message({ type: 'CONNECTED', userId: 7 });
  replacement.message({ type: 'ACK', requestId: replacement.sent.at(-1).requestId });
  assert.equal(f.module.connectionSnapshot(), 'connected');
  const timerCount = f.timers.size;
  if (outcome === '401') rejectAuth(new ApiError(401)); else resolveAuth({ id: 7 });
  await oldClose;
  assert.equal(f.module.connectionSnapshot(), 'connected');
  assert.equal(expired, 0); assert.equal(replacement.readyState, 1);
  assert.equal(f.timers.size, timerCount, 'obsolete auth must not add a retry timer');
  manager.stop();
}
console.log('PASS: 7 deterministic manager scenarios, including late 401/success after a replacement connection.');
