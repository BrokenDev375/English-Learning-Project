import { test, expect } from '@playwright/test';
import { resolve } from 'node:path';

test('default 300-second idle expires with live heartbeat, context reporting and HTTP polling @idle-default', async ({ page }) => {
  test.skip(!process.env.E2E_DEFAULT_IDLE_TESTS, 'Run separately on an isolated stack with default 300/30 settings.');
  test.setTimeout(360000);
  const email = `idle-default-${Date.now()}@test.local`, password = 'SessionTest@12345';
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: { email, password, fullName: 'Kiểm tra timeout mặc định' } })).status()).toBe(201);
  let heartbeats = 0, polls = 0; const activities: { interaction: boolean }[] = [];
  page.on('websocket', socket => socket.on('framesent', frame => { if (JSON.parse(frame.payload.toString()).type === 'HEARTBEAT') heartbeats++; }));
  page.on('request', r => { if (new URL(r.url()).pathname === '/api/auth/activity') activities.push(r.postDataJSON()); });
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
  const state = await (await page.request.get('/api/auth/session')).json();
  expect(Date.parse(state.idleExpiresAt) - Date.parse(state.lastInteractionAt)).toBe(300000);
  expect(Date.parse(state.idleExpiresAt) - Date.parse(state.warningAt)).toBe(30000);
  const start = Date.now(), remaining = Date.parse(state.idleExpiresAt) - Date.parse(state.serverTime);
  const timer = setInterval(() => { polls++; void page.request.get('/api/auth/me').catch(() => {}); }, 5000);
  try {
    const warning = page.getByRole('region', { name: 'Trạng thái phiên làm việc' });
    await expect(warning).toContainText('Phiên làm việc sắp hết hạn', { timeout: 285000 });
    await page.screenshot({ path: resolve('../../.test-state/session-default-warning.png') });
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 45000 });
    expect(Date.now() - start).toBeGreaterThanOrEqual(remaining - 2000);
    expect((await page.request.get('/api/auth/me')).status()).toBe(401);
    expect(heartbeats).toBeGreaterThanOrEqual(10); expect(polls).toBeGreaterThanOrEqual(50);
    expect(activities.length).toBeGreaterThan(10); expect(activities.every(a => !a.interaction)).toBe(true);
    await expect(page.getByText(/hết hạn do không tương tác/)).toBeVisible();
    await test.info().attach('default-idle-evidence', { body: JSON.stringify({ configuredMs: 300000, elapsedMs: Date.now() - start, heartbeats, polls, activityReports: activities.length }), contentType: 'application/json' });
  } finally { clearInterval(timer); }
});
