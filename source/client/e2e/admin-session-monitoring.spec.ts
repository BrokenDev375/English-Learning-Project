import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { monitoredAccount, monitoringDetail, monitoringPage } from './admin-session-fixtures';

const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(l => l && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const monitor = (page: Page) => page.getByRole('region', { name: 'Giám sát hoạt động', exact: true });
const management = (page: Page) => page.getByRole('region', { name: 'Danh sách tài khoản', exact: true });
const rows = (page: Page) => monitor(page).locator('tbody tr');
async function write(page: Page, endpoint: string, data: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const result = await page.request.post(endpoint, { headers: { [csrf.headerName]: csrf.token }, data });
  expect(result.ok(), await result.text()).toBeTruthy(); return result.json();
}
async function admin(page: Page) {
  await page.goto('/'); await write(page, '/api/auth/login', { email: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD });
  await page.reload(); await page.goto('/#/admin'); await expect(monitor(page)).toBeVisible();
}
async function student(page: Page, email?: string) {
  await page.goto('/'); const address = email ?? `monitor-ui-${randomUUID()}@test.local`;
  if (!email) await write(page, '/api/auth/register', { email: address, password: 'MonitorTest@12345', fullName: 'Học viên kiểm tra phiên' });
  await write(page, '/api/auth/login', { email: address, password: 'MonitorTest@12345' }); await page.reload();
  await expect(page.getByText('Đã kết nối', { exact: true })).toBeVisible(); return address;
}
async function learn(page: Page) {
  await page.getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await page.getByRole('button', { name: /^Chơi (?:tiếp )?vòng 1 →$/ }).click();
  await page.getByRole('button', { name: /Chơi bài 1 · A day with Linh/ }).click();
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10);
}

test('monitor filters and pagination are independent of account CRUD and show totals before pagination', async ({ page }) => {
  const requests: URL[] = [];
  await page.route('**/api/admin/sessions?*', route => {
    const url = new URL(route.request().url()); requests.push(url);
    const filtered = url.searchParams.get('q') === 'Tìm riêng';
    const p = Number(url.searchParams.get('page'));
    const items = filtered ? [] : Array.from({ length: p ? 2 : 10 }, (_, i) => monitoredAccount(700 + p * 10 + i));
    return route.fulfill({ json: monitoringPage(items, filtered ? 0 : 12, p) });
  });
  await admin(page); await expect(rows(page)).toHaveCount(10);
  await expect(monitor(page).getByTestId('online-accounts')).toHaveText('12');
  await monitor(page).getByRole('button', { name: 'Sau', exact: true }).click(); await expect(rows(page)).toHaveCount(2);
  await monitor(page).getByRole('combobox', { name: 'Vai trò giám sát', exact: true }).selectOption('TEACHER');
  await monitor(page).getByRole('combobox', { name: 'Chức năng', exact: true }).selectOption('REVIEW');
  await monitor(page).getByLabel('Tìm người đang online', { exact: true }).fill('Tìm riêng');
  await monitor(page).getByRole('button', { name: 'Lọc hoạt động', exact: true }).click();
  await expect(rows(page)).toHaveCount(0); await expect(monitor(page)).toContainText('Không có tài khoản online phù hợp bộ lọc.');
  expect(requests.at(-1)?.searchParams.get('role')).toBe('TEACHER'); expect(requests.at(-1)?.searchParams.get('activity')).toBe('REVIEW'); expect(requests.at(-1)?.searchParams.get('page')).toBe('0');
  await expect(management(page).getByLabel('Tìm tài khoản', { exact: true })).toHaveValue('');
  await expect(management(page).getByRole('combobox', { name: 'Vai trò', exact: true })).toHaveValue('');
});

test('detail drawer supports keyboard focus, stale data, disconnected sessions and an expired reference', async ({ page }) => {
  const account = monitoredAccount(700, 'Chi tiết nhiều tab'); let mode = 'ok';
  const detail = monitoringDetail(account); detail.session.connected = false; detail.session.openConnections = 0; detail.tabs[0].visibility = 'HIDDEN'; detail.tabs[0].connected = false;
  await page.route('**/api/admin/sessions?*', route => route.fulfill({ json: monitoringPage([account]) }));
  await page.route('**/api/admin/sessions/' + account.sessions[0].sessionRef, route => mode === 'ok' ? route.fulfill({ json: detail }) : route.fulfill({ status: mode === 'expired' ? 404 : 503, json: { message: 'Chưa sẵn sàng.' } }));
  await admin(page); const open = rows(page).getByRole('button', { name: 'Xem phiên', exact: true }); await open.click();
  const drawer = page.getByRole('dialog', { name: 'Chi tiết phiên', exact: true });
  await expect(drawer).toContainText('Không có kết nối sống'); await expect(drawer).toContainText('Tab ở nền');
  await expect(drawer.getByRole('button', { name: 'Đóng', exact: true })).toBeFocused();
  await page.keyboard.press('Shift+Tab'); await expect(drawer.getByRole('combobox')).toBeFocused();
  await page.keyboard.press('Tab'); await expect(drawer.getByRole('button', { name: 'Đóng', exact: true })).toBeFocused();
  mode = 'error'; await page.evaluate(() => window.dispatchEvent(new Event('presence:changed')));
  await expect(drawer.getByRole('alert')).toContainText('Chưa cập nhật chi tiết phiên'); await expect(drawer).toContainText('Không có kết nối sống');
  mode = 'expired'; await drawer.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(drawer).toContainText('Phiên đã kết thúc.'); await expect(drawer).not.toContainText('Không có kết nối sống');
  await page.keyboard.press('Escape'); await expect(drawer).toHaveCount(0); await expect(open).toBeFocused();
});

test('polling still refreshes with a live socket and hidden documents wait until visible', async ({ page }) => {
  let calls = 0, name = 'Trước lần poll';
  await page.route('**/api/admin/sessions?*', route => { calls++; return route.fulfill({ json: monitoringPage([monitoredAccount(700, name)]) }); });
  await page.clock.install(); await admin(page); await expect(rows(page)).toContainText(name);
  await expect(page.getByText('Đã kết nối', { exact: true })).toBeVisible();
  name = 'Sau lần poll'; await page.clock.fastForward(15000); await expect(rows(page)).toContainText(name);
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')); });
  const before = calls; await page.clock.fastForward(30000); expect(calls).toBe(before);
  name = 'Sau khi hiện lại'; await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')); });
  await expect(rows(page)).toContainText(name);
});

test('filter changes and repeated invalidations keep one GET in flight and discard obsolete data', async ({ page }) => {
  let release = () => {}, calls = 0;
  await page.route('**/api/admin/sessions?*', async route => {
    calls++;
    if (calls === 1) { await new Promise<void>(resolve => { release = resolve; }); await route.fulfill({ json: monitoringPage([monitoredAccount(700, 'Dữ liệu bộ lọc cũ')]) }); }
    else await route.fulfill({ json: monitoringPage([monitoredAccount(701, 'Dữ liệu bộ lọc mới')]) });
  });
  await admin(page); await expect.poll(() => calls).toBe(1);
  await monitor(page).getByRole('combobox', { name: 'Chức năng', exact: true }).selectOption('REVIEW');
  await page.evaluate(() => { for (let i = 0; i < 8; i++) window.dispatchEvent(new Event('presence:changed')); }); expect(calls).toBe(1);
  release(); await expect(rows(page)).toContainText('Dữ liệu bộ lọc mới'); await expect(monitor(page)).not.toContainText('Dữ liệu bộ lọc cũ');
});

test('locking an active learner revokes both devices while another account remains online; unlock requires fresh login', async ({ browser, page }) => {
  test.setTimeout(60000);
  const a = await browser.newContext({ baseURL: test.info().project.use.baseURL }), b = await browser.newContext({ baseURL: test.info().project.use.baseURL }), other = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const learner = await a.newPage(), device = await b.newPage(), unrelated = await other.newPage();
    const email = await student(learner); await student(device, email); await student(unrelated); await learn(learner); await admin(page);
    const row = rows(page).filter({ hasText: email }); await expect(row).toContainText('Học bài'); await expect(row).toContainText('2 phiên');
    const own = rows(page).filter({ hasText: env.BOOTSTRAP_ADMIN_EMAIL }); await expect(own.getByRole('button', { name: 'Khóa', exact: true })).toBeDisabled();
    await row.getByRole('button', { name: 'Xem phiên', exact: true }).click();
    const drawer = page.getByRole('dialog', { name: 'Chi tiết phiên', exact: true });
    await expect(drawer.getByRole('combobox').locator('option')).toHaveCount(2);
    await drawer.getByRole('combobox').selectOption({ index: 1 }); await expect(drawer).toContainText('Các tab được ghi nhận');
    await page.screenshot({ path: resolve('../../.test-state/admin-session-detail-desktop.png') });
    await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: resolve('../../.test-state/admin-session-detail-mobile.png') });
    await drawer.getByRole('button', { name: 'Đóng', exact: true }).click();
    await row.getByRole('button', { name: 'Khóa', exact: true }).click();
    const confirmation = page.getByRole('dialog', { name: 'Khóa tài khoản?', exact: true }); await confirmation.getByRole('button', { name: 'Hủy', exact: true }).click();
    await expect(learner.locator('.question-nav button')).toHaveCount(10);
    await row.getByRole('button', { name: 'Khóa', exact: true }).click(); await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(row).toHaveCount(0); await expect(learner.getByLabel('Email', { exact: true })).toBeVisible(); await expect(device.getByLabel('Email', { exact: true })).toBeVisible();
    expect((await unrelated.request.get('/api/auth/me')).status()).toBe(200);
    await management(page).getByLabel('Tìm tài khoản', { exact: true }).fill(email); await management(page).getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    const crud = management(page).getByRole('row').filter({ hasText: email }); await expect(crud).toContainText('Đã khóa');
    await crud.getByRole('button', { name: 'Mở khóa', exact: true }).click(); await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click(); await expect(crud).toContainText('Hoạt động');
    expect((await device.request.get('/api/auth/me')).status()).toBe(401); await student(device, email); await expect(rows(page).filter({ hasText: email })).toHaveCount(1);
  } finally { await a.close(); await b.close(); await other.close(); }
});

test('lost response after a committed lock is reconciled without sending the mutation twice', async ({ browser, page }) => {
  const context = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  try {
    const learner = await context.newPage(); const email = await student(learner); await admin(page);
    let writes = 0;
    await page.route('**/api/admin/users/*/status', async route => { writes++; const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('failed'); });
    await rows(page).filter({ hasText: email }).getByRole('button', { name: 'Khóa', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.getByRole('status').filter({ hasText: 'Đã đối soát' })).toBeVisible(); expect(writes).toBe(1);
    await expect(learner.getByLabel('Email', { exact: true })).toBeVisible();
  } finally { await context.close(); }
});

test('unknown lock result stays pending until a GET confirms status and business rejection keeps the dialog', async ({ page }) => {
  const account = monitoredAccount(700, 'Chờ xác minh'); let unknown = true, reject = false;
  await page.route('**/api/admin/sessions?*', route => route.fulfill({ json: monitoringPage([account]) }));
  await admin(page);
  await page.route('**/api/admin/users?*', route => unknown ? route.fulfill({ status: 503, json: { message: 'Chưa kết nối.' } }) : route.fulfill({ json: { items: [{ id: 700, email: account.user.email, fullName: account.user.fullName, role: 'STUDENT', status: 'ACTIVE', createdAt: new Date().toISOString() }], totalElements: 1, totalPages: 1, page: 0, size: 50 } }));
  await page.route('**/api/admin/users/700/status', route => reject ? route.fulfill({ status: 409, json: { code: 'LAST_ACTIVE_ADMIN', message: 'Cần giữ ít nhất một quản trị viên hoạt động.' } }) : route.abort('failed'));
  await rows(page).getByRole('button', { name: 'Khóa', exact: true }).click(); const modal = page.getByRole('dialog');
  await modal.getByRole('button', { name: 'Xác nhận', exact: true }).click(); await expect(modal.getByRole('alert')).toContainText('có thể đã hoàn tất');
  await expect(modal.getByRole('button', { name: 'Kiểm tra trạng thái', exact: true })).toBeVisible();
  unknown = false; await modal.getByRole('button', { name: 'Kiểm tra trạng thái', exact: true }).click(); await expect(modal.getByRole('alert')).toContainText('chưa ghi nhận thay đổi');
  reject = true; await modal.getByRole('button', { name: 'Xác nhận', exact: true }).click(); await expect(modal.getByRole('alert')).toContainText('ít nhất một quản trị viên'); await expect(rows(page)).toHaveCount(1);
  await page.keyboard.press('Escape'); await expect(modal).toHaveCount(0);
});

test('a late lock response cannot start reconciliation after the authentication lifecycle has ended', async ({ page }) => {
  const account = monitoredAccount(700, 'Phản hồi vòng đăng nhập cũ'); let release = () => {}, received = false;
  await page.route('**/api/admin/sessions?*', route => route.fulfill({ json: monitoringPage([account]) }));
  await page.route('**/api/admin/users/700/status', async route => {
    received = true; await new Promise<void>(resolve => { release = resolve; }); await route.fulfill({ status: 503, json: { message: 'Phản hồi muộn.' } });
  });
  await admin(page); await expect(management(page).locator('tbody tr').first()).toBeVisible();
  await rows(page).getByRole('button', { name: 'Khóa', exact: true }).click(); await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect.poll(() => received).toBe(true);
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: 'SESSION_REVOKED' } })));
  await expect(page.getByLabel('Email', { exact: true })).toBeVisible();
  const unwanted = page.waitForRequest(r => new URL(r.url()).pathname === '/api/admin/users', { timeout: 1000 }).then(() => true, () => false);
  release(); expect(await unwanted).toBe(false); await expect(page.getByRole('dialog')).toHaveCount(0);
});
