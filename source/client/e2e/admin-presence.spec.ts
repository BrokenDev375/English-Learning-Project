import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const path = '/api/admin/statistics/presence';
async function write(page: Page, endpoint: string, data: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post(endpoint, { headers: { [csrf.headerName]: csrf.token }, data });
  expect(response.ok(), await response.text()).toBeTruthy(); return response.json();
}
async function login(page: Page, connected = true) {
  let ready = false; const pending = new Set<string>();
  page.on('websocket', ws => {
    ws.on('framesent', frame => { const m = JSON.parse(frame.payload.toString()); if (m.type === 'SUBSCRIBE' && m.resource === 'PRESENCE') pending.add(m.requestId); });
    ws.on('framereceived', frame => { const m = JSON.parse(frame.payload.toString()); if (m.type === 'ACK' && pending.has(m.requestId)) ready = true; });
  });
  await page.goto('/'); const user = await write(page, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD }); await page.reload();
  await page.goto('/#/admin'); await expect(page.getByLabel('Người đang online', { exact: true })).toHaveCount(1);
  if (connected) await expect.poll(() => ready).toBe(true);
  return user;
}
function panel(page: Page) { return page.getByLabel('Người đang online', { exact: true }); }
function users(page: Page) { return panel(page).getByRole('listitem'); }

test('monitoring API binds real document tabs, deduplicates users and invalidates context without changing online counts', async ({ browser, page }) => {
  await login(page);
  const context = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const learner = await context.newPage(); await learner.goto('/');
    const email = `monitor-${randomUUID()}@test.local`, password = 'Presence@12345';
    await write(learner, '/api/auth/register', { email, password, fullName: 'Giám sát nhiều tab' });
    await write(learner, '/api/auth/login', { email, password }); await learner.reload();
    await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible();
    const tab = await context.newPage(); await tab.goto('/'); await expect(tab.getByText('Đã kết nối', { exact: true })).toBeVisible();
    const endpoint = '/api/admin/sessions?q=' + encodeURIComponent(email);
    const read = async () => (await page.request.get(endpoint)).json();
    await expect.poll(async () => (await read()).items[0]?.tabCount).toBe(2);
    const before = await read(); expect(before.totalElements).toBe(1); expect(before.summary.connectedAccounts).toBe(1);
    expect(before.items[0].validSessionCount).toBe(1); expect(before.items[0].openConnections).toBe(2);
    const ref = before.items[0].sessions[0].sessionRef;
    const detail = await page.request.get('/api/admin/sessions/' + ref);
    expect(detail.headers()['cache-control']).toContain('no-store');
    const body = await detail.json(); expect(body.tabs).toHaveLength(2); expect(body.tabs.every((t: { connected: boolean }) => t.connected)).toBe(true);
    expect(JSON.stringify(body)).not.toMatch(/JSESSIONID|securityVersion|passwordHash|answers|selection_json/);
    expect((await learner.request.get('/api/admin/sessions/' + ref)).status()).toBe(403);
    const refresh = page.waitForResponse(r => r.url().endsWith(path) && r.status() === 200);
    await tab.goto('/#/profile'); await refresh;
    await expect.poll(async () => (await read()).items[0]?.primaryContext.activity).toBe('PROFILE');
    expect((await read()).summary.connectedAccounts).toBe(1);
    await tab.close();
    await expect.poll(async () => (await read()).items[0]?.openConnections).toBe(1);
    await learner.goto('/#/home');
    await learner.getByRole('button', { name: 'Vào chơi', exact: true }).click();
    await learner.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click();
    await learner.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click();
    await learner.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
    await expect(learner.locator('.question-nav button')).toHaveCount(10);
    await expect.poll(async () => (await read()).items[0]?.primaryContext.lessonTitle).toContain('A day with Linh');
    const learning = (await read()).items[0].primaryContext;
    expect(learning.activity).toBe('LEARN'); expect(learning.roundNumber).toBe(1); expect(learning.attemptId).toBeGreaterThan(0);
  } finally { await context.close(); }
});
test('one compact online list updates through WebSocket and never duplicates people or panels after repeated tab changes', async ({ browser, page }) => {
  const admin = await login(page); await expect(users(page)).toHaveCount(1); await expect(users(page)).toContainText(admin.fullName);
  await expect(panel(page).getByRole('button')).toHaveCount(0); await expect(page.getByRole('button', { name: 'Tải lại', exact: true })).toHaveCount(0);
  await expect(page.getByText('HOẠT ĐỘNG HIỆN TẠI', { exact: true })).toHaveCount(0);
  await expect(panel(page)).not.toContainText('Mỗi tài khoản'); await expect(panel(page)).not.toContainText('Cập nhật lúc');
  const learnerContext = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const learner = await learnerContext.newPage(); await learner.goto('/'); const email = 'online-list-' + randomUUID() + '@test.local', password = 'Presence@12345';
    await write(learner, '/api/auth/register', { email, password, fullName: 'Học viên đang online' }); await write(learner, '/api/auth/login', { email, password }); await learner.goto('/');
    await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible(); expect((await learner.request.get(path)).status()).toBe(403);
    await expect(learner.getByLabel('Người đang online', { exact: true })).toHaveCount(0);
    await expect(users(page)).toHaveCount(2);
    for (let i = 0; i < 3; i++) {
      const tab = await learnerContext.newPage(); await tab.goto('/'); await expect(tab.getByText('Đã kết nối', { exact: true })).toBeVisible();
      await expect(panel(page)).toHaveCount(1); await expect(users(page)).toHaveCount(2); await expect(users(page).filter({ hasText: 'Học viên đang online' })).toHaveCount(1);
      await tab.close(); await expect(users(page)).toHaveCount(2);
    }
    await page.screenshot({ path: resolve(process.cwd(), '../../.test-state/admin-online-list-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: resolve(process.cwd(), '../../.test-state/admin-online-list-mobile.png'), fullPage: true });
    await page.getByRole('button', { name: 'Trang chủ', exact: true }).click(); await expect(panel(page)).toHaveCount(0);
    await page.getByRole('button', { name: 'Thống kê học tập', exact: true }).click(); await expect(panel(page)).toHaveCount(0);
    await page.getByRole('button', { name: 'Quản lý tài khoản', exact: true }).click(); await expect(panel(page)).toHaveCount(1); await expect(users(page)).toHaveCount(2);
  } finally { await learnerContext.close(); }
  await expect(users(page)).toHaveCount(1);
});
test('a failed automatic update keeps the previous list and the next event replaces it without adding another panel', async ({ page }) => {
  let failure = false, name = 'Danh sách cũ', calls = 0;
  await page.route('**' + path, route => { calls++; return failure ? route.fulfill({ status: 503, json: { message: 'Tạm thời chưa sẵn sàng.' } }) : route.fulfill({ json: { generatedAt: new Date().toISOString(), connectedAccounts: 1, openConnections: 1, basis: 'OPEN_AUTHENTICATED_SOCKETS', users: [{ id: 1, fullName: name, role: 'ADMIN' }] } }); });
  await login(page); await expect(users(page)).toContainText('Danh sách cũ');
  failure = true; await page.evaluate(() => window.dispatchEvent(new Event('presence:changed')));
  await expect(panel(page).getByRole('alert')).toHaveText('Chưa thể cập nhật danh sách.'); await expect(users(page)).toContainText('Danh sách cũ');
  failure = false; name = 'Danh sách mới'; await page.evaluate(() => window.dispatchEvent(new Event('presence:changed')));
  await expect(panel(page).getByRole('alert')).toHaveCount(0); await expect(users(page)).toContainText('Danh sách mới'); await expect(panel(page)).toHaveCount(1); await expect(users(page)).toHaveCount(1);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible(); const before = calls;
  await page.evaluate(() => window.dispatchEvent(new Event('presence:changed'))); await expect(panel(page)).toHaveCount(0); expect(calls).toBe(before);
});
test('an unavailable socket uses automatic HTTP fallback without refresh buttons or invented empty data', async ({ page }) => {
  let failed = true;
  await page.routeWebSocket('**/ws/events', ws => ws.close());
  await page.route('**' + path, route => failed ? route.fulfill({ status: 503, json: { message: 'Thử lại sau.' } }) : route.fulfill({ json: { generatedAt: new Date().toISOString(), connectedAccounts: 1, openConnections: 1, basis: 'OPEN_AUTHENTICATED_SOCKETS', users: [{ id: 1, fullName: 'Người đang kết nối', role: 'ADMIN' }] } }));
  await page.clock.install(); await login(page, false); await expect(panel(page).getByRole('alert')).toHaveText('Chưa tải được danh sách online.'); await expect(panel(page)).not.toContainText('Chưa có ai online');
  failed = false; await page.clock.fastForward(30000); await expect(panel(page).getByRole('alert')).toHaveCount(0); await expect(users(page)).toContainText('Người đang kết nối'); await expect(panel(page).getByRole('button')).toHaveCount(0);
});
test('the online list reconciles current users after a WebSocket reconnect', async ({ browser, page }) => {
  let blocked = false, disconnect = () => {}, subscriptions = 0;
  await page.routeWebSocket('**/ws/events', ws => {
    if (blocked) { ws.close(); return; }
    disconnect = () => ws.close(); const server = ws.connectToServer();
    ws.onMessage(data => { const message = JSON.parse(data.toString()); if (message.type === 'SUBSCRIBE' && message.resource === 'PRESENCE') subscriptions++; server.send(data); });
  });
  await login(page); await expect(users(page)).toHaveCount(1); blocked = true; disconnect(); await expect(page.locator('.connection')).toContainText('Đang kết nối lại');
  const learnerContext = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const learner = await learnerContext.newPage(); await learner.goto('/'); const email = 'online-reconnect-' + randomUUID() + '@test.local', password = 'Presence@12345';
    await write(learner, '/api/auth/register', { email, password, fullName: 'Học viên kết nối lại' }); await write(learner, '/api/auth/login', { email, password }); await learner.goto('/'); await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible();
    blocked = false; await page.evaluate(() => window.dispatchEvent(new Event('online')));
    await expect(page.locator('.connection')).toContainText('Đã kết nối', { timeout: 10000 }); await expect(users(page)).toHaveCount(2); await expect(users(page).filter({ hasText: 'Học viên kết nối lại' })).toHaveCount(1); expect(subscriptions).toBeGreaterThanOrEqual(2);
  } finally { await learnerContext.close(); }
  await expect(users(page)).toHaveCount(1);
});
