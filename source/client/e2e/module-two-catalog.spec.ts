import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const envPath = resolve(process.cwd(), '../../.env');
const localEnv = Object.fromEntries((existsSync(envPath) ? readFileSync(envPath, 'utf8') : '').split(/\r?\n/)
  .filter(line => line && !line.startsWith('#') && line.includes('='))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
let authorEmail: string;
test.beforeAll(async ({ request }) => {
  authorEmail = 'e2e-m02-' + Date.now() + '@english.local';
  const csrf = async () => await (await request.get('/api/auth/csrf')).json() as { token: string; headerName: string };
  let token = await csrf();
  const login = await request.post('/api/auth/login', { headers: { [token.headerName]: token.token }, data: {
    email: process.env.E2E_ADMIN_EMAIL ?? localEnv.BOOTSTRAP_ADMIN_EMAIL,
    password: process.env.E2E_ADMIN_PASSWORD ?? localEnv.BOOTSTRAP_ADMIN_PASSWORD,
  } });
  expect(login.status()).toBe(200); token = await csrf();
  const created = await request.post('/api/auth/register', { headers: { [token.headerName]: token.token },
    data: { email: authorEmail, password: 'Teacher@12345', fullName: 'Tác giả kiểm thử M02' } });
  expect(created.status()).toBe(201);
  const user = await created.json() as { id: number };
  const promoted = await request.patch('/api/admin/users/' + user.id + '/role', {
    headers: { [token.headerName]: token.token }, data: { role: 'ADMIN' },
  });
  expect(promoted.status()).toBe(200);
});

async function authorLogin(page: Page, email = authorEmail) {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('Teacher@12345');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Quản lý học liệu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quản lý học liệu.' })).toBeVisible();
}
const unique = () => 'e2e-m02-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7);

test('author creates topic, objective and unit, edits and reloads saved content', async ({ page }) => {
  await authorLogin(page);
  const code = unique(), name = 'Chủ đề kiểm thử ' + code;
  await page.getByRole('tab', { name: 'Chủ đề', exact: true }).click();
  await page.getByRole('button', { name: '+ Thêm chủ đề', exact: true }).click();
  await page.getByLabel('Mã chủ đề', { exact: true }).fill(code);
  await page.getByLabel('Tên chủ đề', { exact: true }).fill(name);
  await page.getByLabel('Mô tả', { exact: true }).fill('Danh mục kiểm thử giao diện M02.');
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toContainText('Đã lưu ' + name);
  await page.getByRole('tab', { name: 'Mục tiêu học', exact: true }).click();
  await page.getByRole('button', { name: '+ Thêm mục tiêu học', exact: true }).click();
  await page.getByRole('combobox', { name: 'Chủ đề', exact: true }).selectOption({ label: name });
  await page.getByLabel('Mã mục tiêu', { exact: true }).fill(code + '-objective');
  await page.getByLabel('Tên mục tiêu', { exact: true }).fill('Dùng từ mới trong ngữ cảnh');
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toContainText('Đã lưu Dùng từ mới');
  await page.getByRole('tab', { name: 'Vòng đấu', exact: true }).click();
  await page.getByRole('button', { name: '+ Thêm vòng đấu', exact: true }).click();
  await page.getByRole('combobox', { name: 'Chủ đề', exact: true }).selectOption({ label: name });
  await page.getByLabel('Tên vòng đấu', { exact: true }).fill('Unit kiểm thử ' + code);
  await expect(page.getByLabel('Số thứ tự vòng', { exact: true })).toHaveValue('');
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toContainText('Đã lưu Unit kiểm thử');
  await page.getByLabel('Tìm học liệu', { exact: true }).fill(code);
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa Unit kiểm thử ' + code, exact: true }).click();
  await page.getByLabel('Tên vòng đấu', { exact: true }).fill('Unit đã sửa ' + code);
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Unit đã sửa ' + code, exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Quản lý học liệu', exact: true }).click();
  await page.getByLabel('Tìm học liệu', { exact: true }).fill(code);
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Unit đã sửa ' + code, exact: true })).toBeVisible();
});

test('lost response after commit retries the same request without creating a second topic', async ({ page }) => {
  await authorLogin(page);
  await page.getByRole('tab', { name: 'Chủ đề', exact: true }).click();
  await page.getByRole('button', { name: '+ Thêm chủ đề', exact: true }).click();
  const code = unique();
  await page.getByLabel('Mã chủ đề', { exact: true }).fill(code);
  await page.getByLabel('Tên chủ đề', { exact: true }).fill('Kiểm thử mất xác nhận ' + code);
  let originalId: string | undefined, calls = 0;
  await page.route('**/api/author/topics', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const request = route.request().postDataJSON() as { requestId: string };
    calls++;
    if (calls === 1) {
      originalId = request.requestId;
      const committed = await route.fetch();
      expect(committed.status()).toBe(201);
      return route.abort('failed');
    }
    expect(request.requestId).toBe(originalId);
    return route.continue();
  });
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Chưa kết nối được máy chủ');
  await expect(page.getByLabel('Tên chủ đề', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Thử lưu lại', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toContainText('Đã lưu Kiểm thử mất xác nhận');
  const result = await page.request.get('/api/author/topics?q=' + code);
  expect((await result.json() as { totalElements: number }).totalElements).toBe(1);
  expect(calls).toBe(2);
});

test('stale editor preserves input and does not overwrite a newer change', async ({ page }) => {
  await authorLogin(page);
  await page.getByRole('tab', { name: 'Chủ đề', exact: true }).click();
  await page.getByRole('button', { name: '+ Thêm chủ đề', exact: true }).click();
  const code = unique();
  await page.getByLabel('Mã chủ đề', { exact: true }).fill(code);
  await page.getByLabel('Tên chủ đề', { exact: true }).fill(code);
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toContainText('Đã lưu');
  await page.getByLabel('Tìm học liệu', { exact: true }).fill(code);
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa ' + code, exact: true }).click();
  const listing = await page.request.get('/api/author/topics?q=' + code);
  const item = (await listing.json() as { items: { id: number; revision: number }[] }).items[0];
  const csrf = await (await page.request.get('/api/auth/csrf')).json() as { token: string; headerName: string };
  const changed = await page.request.put('/api/author/topics/' + item.id, { headers: { [csrf.headerName]: csrf.token },
    data: { requestId: crypto.randomUUID(), baseRevision: item.revision, name: 'Bản mới ' + code, description: '' } });
  expect(changed.status()).toBe(200);
  await page.getByLabel('Tên chủ đề', { exact: true }).fill('Nội dung đang nhập');
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Nội dung đã thay đổi ở nơi khác');
  await expect(page.getByLabel('Tên chủ đề', { exact: true })).toHaveValue('Nội dung đang nhập');
  const final = await page.request.get('/api/author/topics?q=' + code);
  expect((await final.json() as { items: { name: string }[] }).items[0].name).toBe('Bản mới ' + code);
});

test('catalog and editor fit a mobile screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await authorLogin(page);
  await expect(page.getByRole('heading', { name: 'Sinh hoạt hằng ngày — Bắt đầu', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/module-2-catalog-mobile.png', fullPage: true });
  await page.getByRole('button', { name: '+ Thêm vòng đấu', exact: true }).click();
  await expect(page.getByLabel('Tên vòng đấu', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.getByRole('button', { name: 'Hủy', exact: true }).click();
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: 'test-results/module-2-catalog-desktop.png', fullPage: true });
});
