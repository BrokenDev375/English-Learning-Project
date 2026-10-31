import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { resolve } from 'node:path';

const password = 'SessionTest@12345';
async function login(page: Page, email: string) {
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
}
async function student(page: Page) {
  const email = `session-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  expect((await page.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: { email, password, fullName: 'Kiểm tra phiên' } })).status()).toBe(201);
  const registered = page.waitForResponse(r => r.url().endsWith('/api/auth/activity') && r.status() === 200);
  await page.goto('/#/home'); await login(page, email); await registered; return email;
}
async function lesson(page: Page) {
  await page.getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click();
  await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click();
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10);
}
test.describe('session recovery @idle-short', () => {
  test.skip(!process.env.E2E_IDLE_TESTS, 'Requires isolated stack SESSION_IDLE_TIMEOUT_SECONDS=20 and WARNING=5.');
  test.setTimeout(75000);
  test('warning follows the server and Continue renews only after ACK, desktop and mobile', async ({ page }) => {
    await student(page);
    const original = await (await page.request.get('/api/auth/session')).json();
    expect(Date.parse(original.idleExpiresAt) - Date.parse(original.lastInteractionAt)).toBe(20000);
    const warning = page.getByRole('region', { name: 'Trạng thái phiên làm việc' });
    await expect(warning).toContainText('Phiên làm việc sắp hết hạn', { timeout: 23000 });
    await page.screenshot({ path: resolve('../../.test-state/session-warning-desktop.png') });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: resolve('../../.test-state/session-warning-mobile.png') });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await warning.getByRole('button', { name: 'Tiếp tục làm việc' }).click();
    await expect(warning).toHaveCount(0);
    const next = await (await page.request.get('/api/auth/session')).json();
    expect(Date.parse(next.idleExpiresAt)).toBeGreaterThan(Date.parse(original.idleExpiresAt));
  });
  test('heartbeats and GETs cannot keep the session alive, then login works with the expired cookie', async ({ page }) => {
    const email = await student(page);
    const poll = setInterval(() => { void page.request.get('/api/auth/me').catch(() => {}); }, 1000);
    try { await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 30000 }); }
    finally { clearInterval(poll); }
    await expect(page.getByText(/hết hạn do không tương tác/)).toBeVisible();
    expect((await page.request.get('/api/auth/session')).status()).toBe(401);
    await login(page, email); expect((await page.request.get('/api/auth/session')).status()).toBe(200);
  });
  test('audio playback and position updates cannot renew the idle deadline', async ({ page }) => {
    await student(page); await lesson(page);
    const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1];
    const snapshot = await (await page.request.get(`/api/attempts/${id}`)).json();
    const material = snapshot.materials.find((m: { kind: string }) => m.kind === 'AUDIO');
    expect(material).toBeTruthy();
    const index = snapshot.items.findIndex((i: { materialVersionId: number }) => i.materialVersionId === material.materialVersionId);
    expect(index).toBeGreaterThanOrEqual(0); await page.locator('.question-nav button').nth(index).click();
    const audio = page.locator('audio').first();
    await audio.evaluate(async element => {
      const player = element as HTMLAudioElement, counters = window as unknown as { idleAudioTicks: number };
      counters.idleAudioTicks = 0; player.addEventListener('timeupdate', () => { counters.idleAudioTicks++; });
    });
    await page.getByRole('button', { name: 'Nghe lần 1/2', exact: true }).click();
    await expect.poll(() => audio.evaluate(element => { const player = element as HTMLAudioElement; return player.currentTime > 0 && !player.paused; })).toBe(true);
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 35000 });
    expect(await page.evaluate(() => (window as unknown as { idleAudioTicks: number }).idleAudioTicks)).toBeGreaterThan(10);
    expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  });
  test('offline edits survive real server idle expiration and same-user reauthentication', async ({ page, context }) => {
    const email = await student(page);
    await lesson(page);
    const url = page.url();
    await context.setOffline(true);
    await page.locator('.answer-fields').getByRole('radio').last().check();
    await expect(page.locator('.learning-status')).toContainText('Đã lưu tại máy');
    await page.waitForTimeout(22000); // Real isolated backend clock, not browser fake time.
    await context.setOffline(false);
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 20000 });
    await login(page, email); await expect(page).toHaveURL(url);
    await expect(page.locator('.answer-fields').getByRole('radio').last()).toBeChecked();
    await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  });
  test('submit committed without ACK survives idle and reauthentication with the same pending key and immutable result', async ({ page }) => {
    const email = await student(page); await lesson(page);
    const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1];
    let original: unknown, allowAck = false, calls = 0;
    await page.route('**/api/attempts/*/submit', async route => {
      const body = route.request().postDataJSON(); calls++;
      if (!original) { original = body; expect((await route.fetch()).status()).toBe(200); }
      else expect(body).toEqual(original);
      if (allowAck) await route.continue(); else await route.abort('failed');
    });
    await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
    await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible();
    const before = await (await page.request.get(`/api/attempts/${id}/result`)).json();
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 35000 });
    allowAck = true; await login(page, email); await expect(page.locator('.result-score')).toBeVisible();
    expect(calls).toBeGreaterThanOrEqual(2);
    expect(await (await page.request.get(`/api/attempts/${id}/result`)).json()).toEqual(before);
    await page.reload(); await expect(page.locator('.result-score')).toBeVisible();
    expect(await (await page.request.get(`/api/attempts/${id}/result`)).json()).toEqual(before);
  });
  test('interaction in one tab renews the shared HTTP session for another tab', async ({ page, context }) => {
    await student(page); const tab = await context.newPage(); await tab.goto('/#/profile');
    await expect(tab.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
    const first = await (await tab.request.get('/api/auth/session')).json();
    for (let i = 0; i < 3; i++) { await page.waitForTimeout(7000); await page.bringToFront(); await page.keyboard.press('Tab'); }
    await expect.poll(async () => {
      const r = await tab.request.get('/api/auth/session'); if (r.status() !== 200) return false;
      const s = await r.json(); return Date.parse(s.idleExpiresAt) > Date.parse(first.idleExpiresAt);
    }).toBe(true);
    await expect(tab.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible(); await tab.close();
  });
  test('a real interaction renews only its own device while another session of the same account expires', async ({ page, browser }) => {
    const email = await student(page);
    const other = await browser.newContext({ baseURL: test.info().project.use.baseURL });
    try {
      const device = await other.newPage(); await device.goto('/'); await login(device, email);
      for (let i = 0; i < 4; i++) { await page.waitForTimeout(6000); await page.bringToFront(); await page.keyboard.press('Tab'); }
      await expect(device.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 10000 });
      expect((await page.request.get('/api/auth/me')).status()).toBe(200);
      expect((await device.request.get('/api/auth/me')).status()).toBe(401);
    } finally { await other.close(); }
  });
  test('a stalled IndexedDB completion cannot hold the expired UI open forever', async ({ page }) => {
    await student(page); await lesson(page); await page.waitForTimeout(750);
    await page.evaluate(() => {
      const original = IDBDatabase.prototype.transaction;
      IDBDatabase.prototype.transaction = function (...args: Parameters<IDBDatabase['transaction']>) {
        const tx = original.apply(this, args);
        if (args[1] === 'readwrite') tx.addEventListener('complete', event => event.stopImmediatePropagation());
        return tx;
      };
    });
    await expect(page.getByLabel('Email', { exact: true })).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Có dữ liệu chưa xác nhận lưu được tại máy/)).toBeVisible();
  });
  test('expired tab metadata is registered again without replaying the rejected activity', async ({ page }) => {
    await student(page);
    const bodies: { tabId: string; seq: number; interaction: boolean }[] = []; let rejected = false;
    await page.route('**/api/auth/activity', async route => {
      const body = route.request().postDataJSON(); bodies.push(body);
      if (!rejected && body.seq > 0) {
        rejected = true;
        await route.fulfill({ status: 409, json: { code: 'TAB_EXPIRED', message: 'Test tab metadata expired' } }); return;
      }
      await route.continue();
    });
    await page.keyboard.press('Tab');
    await expect.poll(() => bodies.some(body => body.seq === 0)).toBe(true);
    const registration = bodies.find(body => body.seq === 0)!;
    expect(registration.interaction).toBe(false);
    expect(registration.tabId).not.toBe(bodies[0].tabId);
    await page.keyboard.press('Tab');
    await expect.poll(() => bodies.some(body => body.tabId === registration.tabId && body.interaction)).toBe(true);
    expect((await page.request.get('/api/auth/session')).status()).toBe(200);
  });
});
