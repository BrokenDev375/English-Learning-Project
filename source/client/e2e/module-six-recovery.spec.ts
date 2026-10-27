import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';

async function student(page: Page) {
  const email = `m06-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: { email, password: 'LearningTest@12345', fullName: 'Học viên M06' } })).status()).toBe(201);
  await page.goto('/#/learning'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningTest@12345'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click();
  await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click(); await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10); return email;
}
const status = (page: Page) => page.locator('.learning-status');
async function snapshot(page: Page) { const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1]; const r = await page.request.get('/api/attempts/' + id); expect(r.status()).toBe(200); return r.json(); }

test('M05 rewards and league share the M06 attempt socket and recover subscriptions together', async ({ page }) => {
  test.setTimeout(60000);
  const connections: { sent: Record<string, unknown>[]; received: Record<string, unknown>[]; close: () => void }[] = [];
  await page.routeWebSocket('**/ws/events', socket => {
    const server = socket.connectToServer();
    const connection = { sent: [] as Record<string, unknown>[], received: [] as Record<string, unknown>[],
      close: () => { socket.close({ code: 1001, reason: 'test reconnect' }); server.close({ code: 1001 }); } };
    socket.onMessage(message => { connection.sent.push(JSON.parse(message.toString())); server.send(message); });
    server.onMessage(message => { connection.received.push(JSON.parse(message.toString())); socket.send(message); });
    connections.push(connection);
  });
  await student(page); const before = await snapshot(page);
  const subscribed = (index: number) => connections[index]?.sent.filter(message => message.type === 'SUBSCRIBE') ?? [];
  await expect.poll(() => subscribed(0).length).toBe(3);
  expect(connections).toHaveLength(1);
  expect(subscribed(0).filter(message => message.resource).map(message => message.resource).sort()).toEqual(['GAMIFICATION', 'LEAGUE']);
  expect(subscribed(0).find(message => message.attemptId)).toMatchObject({ attemptId: before.attemptId });
  connections[0].close();
  await expect.poll(() => connections.length).toBe(2);
  await expect.poll(() => subscribed(1).length).toBe(3);
  expect(subscribed(1).filter(message => message.resource).map(message => message.resource).sort()).toEqual(['GAMIFICATION', 'LEAGUE']);
  expect(subscribed(1).find(message => message.attemptId)).toMatchObject({ attemptId: before.attemptId });
  await expect(page.locator('.connection')).toContainText('Đã kết nối');
  await page.locator('.answer-fields').getByRole('radio').last().check();
  await expect(status(page)).toContainText('Đã lưu trên máy chủ');
  await expect.poll(() => connections[1].received.some(message => message.type === 'ATTEMPT_CHANGED' && message.attemptId === before.attemptId)).toBe(true);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const join = await page.request.post('/api/me/league/join', { headers: { [csrf.headerName]: csrf.token }, data: { requestId: crypto.randomUUID() } });
  expect(join.ok(), await join.text()).toBeTruthy();
  await expect.poll(() => connections[1].received.some(message => message.type === 'LEAGUE_CHANGED' || message.type === 'LEADERBOARD_CHANGED'), { timeout: 20000 }).toBe(true);
  await page.getByRole('button', { name: 'Trang chủ', exact: true }).click();
  await expect(page.locator('.reward-strip')).toContainText('XP hôm nay');
  expect(connections).toHaveLength(2);
});

test('offline edits commit locally; reload after reconnection restores and autosaves the exact draft', async ({ page, context }) => {
  await student(page); const before = await snapshot(page); await context.setOffline(true);
  await page.locator('.answer-fields').getByRole('radio').last().check();
  await expect(page.getByRole('button', { name: 'Mở gợi ý', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true })).toBeDisabled();
  await expect(status(page)).toContainText('Đã lưu tại máy'); const url = page.url();
  await context.setOffline(false); await page.reload();
  await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked();
  await expect(status(page)).toContainText('Đã lưu trên máy chủ'); expect(page.url()).toBe(url);
  const after = await snapshot(page); expect(after.attemptId).toBe(before.attemptId); expect(after.items.map((i: { questionVersionId: number }) => i.questionVersionId)).toEqual(before.items.map((i: { questionVersionId: number }) => i.questionVersionId));
  expect(after.items[0].response.optionId).toBe(after.items[0].question.content.options.at(-1).id);
});

test('journal survives a draft commit with lost response and reload, replaying the same key and payload', async ({ page }) => {
  await student(page); let original: unknown; let calls = 0;
  await page.route('**/api/attempts/*/draft', async route => {
    calls++; if (calls === 1) { original = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(200); return route.abort(); }
    expect(route.request().postDataJSON()).toEqual(original); return route.continue();
  });
  await page.locator('.answer-fields').getByRole('radio').first().check(); await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible();
  await page.reload(); await expect(status(page)).toContainText('Đã lưu trên máy chủ');
  expect(calls).toBe(2); expect((await snapshot(page)).revision).toBe(1);
});

test('offline confirmed submit stays pending across reload and creates one immutable result', async ({ page, context }) => {
  await student(page); const before = await snapshot(page); await context.setOffline(true);
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click();
  await expect(status(page)).toContainText('Chờ nộp'); await expect(page.locator('.answer-fields').getByRole('radio').first()).toBeDisabled();
  await context.setOffline(false); await page.reload(); await expect(page.locator('.result-score')).toBeVisible();
  const result = await (await page.request.get(`/api/attempts/${before.attemptId}/result`)).json();
  await page.reload(); await expect(page.locator('.result-score')).toBeVisible(); expect(await (await page.request.get(`/api/attempts/${before.attemptId}/result`)).json()).toEqual(result);
});

test('presence counts two authenticated tabs as one user with two connections and isolates other sessions', async ({ page, context }) => {
  await student(page); const tab = await context.newPage(); await tab.goto(page.url());
  await expect.poll(async () => (await (await page.request.get('/api/me/connections')).json()).activeConnectionCount).toBe(2);
  await tab.close(); await expect.poll(async () => (await (await page.request.get('/api/me/connections')).json()).activeConnectionCount).toBe(1);
  const body = await (await page.request.get('/api/me/connections')).json(); expect(body.online).toBe(true); expect(JSON.stringify(body)).not.toMatch(/sessionId|socketId|email/);
});

test('failed IndexedDB writes never announce a local save or send an unjournaled mutation', async ({ page }) => {
  await student(page); let writes = 0; await page.route('**/api/attempts/*/draft', route => { writes++; return route.continue(); });
  await page.evaluate(() => {
    const original = IDBDatabase.prototype.transaction;
    IDBDatabase.prototype.transaction = function (...args: Parameters<IDBDatabase['transaction']>) { if (args[1] === 'readwrite') throw new DOMException('Test quota', 'QuotaExceededError'); return original.apply(this, args); };
  });
  await page.locator('.answer-fields').getByRole('radio').last().check(); await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Chưa lưu được tại máy' })).toBeVisible(); expect(writes).toBe(0);
  await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked(); await expect(status(page)).not.toContainText('Đã lưu trên máy chủ');
});

test('socket ACK loss triggers reconnect while HTTP learning continues', async ({ page }) => {
  let connections = 0;
  await page.routeWebSocket('**/ws/events', ws => {
    const number = ++connections; const server = ws.connectToServer();
    server.onMessage(data => { const message = JSON.parse(data.toString()) as { type: string }; if (number === 1 && message.type === 'ACK') return; ws.send(data); });
  });
  await student(page); const id = (await snapshot(page)).attemptId;
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await page.locator('.answer-fields').getByRole('radio').first().check(); await expect(status(page)).toContainText('Đã lưu trên máy chủ');
  expect((await snapshot(page)).attemptId).toBe(id);
  await expect(page.locator('.connection')).toContainText('Đã kết nối', { timeout: 20000 }); expect(connections).toBeGreaterThanOrEqual(2);
});

test('create committed without ACK persists through reload and does not allocate a second Attempt', async ({ page }) => {
  // Start lesson 1, then create a distinct LEARN intent for lesson 2 in the same unlocked round.
  await student(page); await page.goto('/#/learning?unit=1&lesson=2');
  await expect(page.getByRole('button', { name: 'Bắt đầu chơi', exact: true })).toBeEnabled();
  let payload: unknown; let calls = 0;
  await page.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    if (++calls === 1) { payload = route.request().postDataJSON(); expect((await route.fetch()).status()).toBe(201); return route.abort(); }
    expect(route.request().postDataJSON()).toEqual(payload); return route.continue();
  });
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click(); await expect(page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt' })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt' }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10);
  expect(calls).toBe(2); expect((await (await page.request.get('/api/attempts')).json()).totalElements).toBe(2);
});

for (const reloadWhileWaiting of [false, true]) test(`another tab selecting B never opens pending A${reloadWhileWaiting ? ', including recovery after reload' : ''}`, async ({ page, context }) => {
  await student(page);
  await page.goto('/#/learning?unit=1&lesson=2');
  await expect(page.getByRole('button', { name: 'Bắt đầu chơi', exact: true })).toBeEnabled();
  // Both lesson introductions are mounted before A creates its shared journal entry.
  const tab = await context.newPage(); await tab.goto('/#/learning?unit=1&lesson=1');
  await expect(tab.getByRole('button', { name: 'Tiếp tục làm', exact: true })).toBeEnabled();
  let bodyA: { requestId: string }, attemptA = 0;
  await page.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    bodyA = route.request().postDataJSON();
    const response = await route.fetch(); expect(response.status()).toBe(201);
    attemptA = (await response.json()).attemptId; await route.abort();
  });
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true })).toBeVisible();
  let release!: () => void, started!: () => void, failRecovery = reloadWhileWaiting;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const recoveryStarted = new Promise<void>(resolve => { started = resolve; });
  const posted: { requestId: string; lessonId: number }[] = [];
  await tab.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const body = route.request().postDataJSON(); posted.push(body);
    if (body.requestId === bodyA.requestId) {
      started(); await gate;
      if (failRecovery) { failRecovery = false; return route.abort(); }
    }
    return route.continue();
  });
  try {
    await tab.getByRole('button', { name: 'Tiếp tục làm', exact: true }).click();
    await recoveryStarted;
    await expect(tab.getByRole('status').filter({ hasText: 'Đang xác nhận và mở lượt làm bạn đã chọn' })).toBeVisible();
    expect(tab.url()).not.toContain('attempt=');
  } finally { release(); }
  if (reloadWhileWaiting) {
    await expect(tab.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true })).toBeVisible();
    await tab.reload();
    await tab.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true }).click();
  }
  await expect(tab.locator('.question-nav button')).toHaveCount(10);
  expect((await snapshot(tab)).attemptId).not.toBe(attemptA);
  expect(posted.filter(row => row.requestId !== bodyA.requestId).map(row => row.lessonId)).toEqual([1]);
  expect((await (await tab.request.get('/api/attempts')).json()).totalElements).toBe(2);
  await tab.close();
});

test('a legacy PRACTICE command committed before the menu removal still replays from its journal', async ({ page }) => {
  await student(page); const user = await (await page.request.get('/api/auth/me')).json();
  const body = { requestId: crypto.randomUUID(), mode: 'PRACTICE', topicId: 1, difficulty: 'EASY', count: 3 };
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const committed = await page.request.post('/api/attempts', { headers: { [csrf.headerName]: csrf.token }, data: body }); expect(committed.status()).toBe(201);
  const original = await committed.json();
  // Model the PREPARED journal left by the old UI after a lost server response.
  await page.evaluate(({ userId, body }) => new Promise<void>((resolve, reject) => {
    const open = indexedDB.open('english-learning-recovery', 1); open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const db = open.result, tx = db.transaction('commands', 'readwrite');
      tx.objectStore('commands').put({ key: `${userId}:${body.requestId}`, version: 1, userId, kind: 'create', body, status: 'PREPARED', queuedAt: Date.now() });
      tx.oncomplete = () => { db.close(); resolve(); }; tx.onerror = () => reject(tx.error);
    };
  }), { userId: user.id, body });
  let replayed = 0;
  await page.route('**/api/attempts', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    replayed++; expect(route.request().postDataJSON()).toEqual(body); return route.continue();
  });
  await page.goto('/#/learning?tab=practice'); await page.reload();
  await expect(page.locator('.practice-form')).toHaveCount(0);
  await page.getByRole('button', { name: 'Gửi lại yêu cầu tạo lượt', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(3);
  expect((await snapshot(page)).attemptId).toBe(original.attemptId); expect(replayed).toBe(1);
  expect((await (await page.request.get('/api/attempts')).json()).totalElements).toBe(2);
});

for (const corruption of ['command', 'snapshot']) test(`corrupt persisted ${corruption} is quarantined instead of dispatching an arbitrary URL`, async ({ page }) => {
  await student(page); let forbidden = 0; page.on('request', r => { if (r.url().endsWith('/api/auth/logout')) forbidden++; });
  await page.evaluate(corruption => new Promise<void>((resolve, reject) => {
    const open = indexedDB.open('english-learning-recovery', 1); open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const db = open.result; const tx = db.transaction('workingDrafts', 'readwrite'); const store = tx.objectStore('workingDrafts'); const get = store.getAll();
      get.onsuccess = () => { const row = get.result[0]; if (corruption === 'snapshot') row.state.snapshot.items = null; else row.state.pending = { kind: 'draft', path: '/api/auth/logout', generation: row.generation, body: { requestId: crypto.randomUUID(), baseRevision: 0 } }; store.put(row); };
      tx.oncomplete = () => { db.close(); resolve(); }; tx.onerror = () => reject(tx.error);
    };
  }), corruption);
  await page.reload(); await expect(page.getByRole('alert').filter({ hasText: 'cách ly' })).toBeVisible();
  await expect(page.locator('.question-nav button')).toHaveCount(10); expect(forbidden).toBe(0);
});

test('cache retention removes excess clean copies while preserving an old dirty working copy', async ({ page }) => {
  await student(page);
  await page.evaluate(() => new Promise<void>((resolve, reject) => {
    const open = indexedDB.open('english-learning-recovery', 1); open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const db = open.result; const tx = db.transaction('workingDrafts', 'readwrite'); const store = tx.objectStore('workingDrafts'); const get = store.getAll();
      get.onsuccess = () => {
        const row = get.result[0];
        for (let n = 0; n < 102; n++) store.put({ ...row, key: 'seed-clean:' + n, updatedAt: Date.now() - 100000 - n });
        const dirty = structuredClone(row); dirty.key = 'seed-dirty'; dirty.updatedAt = 1; dirty.state.pending = { kind: 'draft' }; store.put(dirty);
      };
      tx.oncomplete = () => { db.close(); resolve(); }; tx.onerror = () => reject(tx.error);
    };
  }));
  await page.reload(); await expect(page.locator('.question-nav button')).toHaveCount(10);
  const keys = await page.evaluate(() => new Promise<string[]>((resolve, reject) => {
    const open = indexedDB.open('english-learning-recovery', 1); open.onerror = () => reject(open.error);
    open.onsuccess = () => { const db = open.result; const tx = db.transaction('workingDrafts', 'readonly'); const get = tx.objectStore('workingDrafts').getAllKeys(); tx.oncomplete = () => { resolve(get.result.map(String)); db.close(); }; };
  }));
  expect(keys).toContain('seed-dirty'); expect(keys.filter(k => k.startsWith('seed-clean:')).length).toBeLessThan(102);
});

test('without Web Locks, reload offers the previous local copy instead of sharing its writer', async ({ page, context }) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'locks', { value: undefined, configurable: true }));
  await student(page); const previous = await page.evaluate(() => sessionStorage.getItem('learning-writer'));
  await context.setOffline(true); await page.locator('.answer-fields').getByRole('radio').last().check(); await expect(status(page)).toContainText('Đã lưu tại máy');
  await context.setOffline(false); await page.reload();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('learning-writer'))).not.toBe(previous);
  const recover = page.getByRole('button', { name: /^Khôi phục bản / }); await expect(recover).toBeVisible(); page.once('dialog', d => d.accept()); await recover.click();
  await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked(); await expect(status(page)).toContainText('Đã lưu trên máy chủ');
});
