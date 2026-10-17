import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const envFile = resolve(process.cwd(), '../../.env');
const localEnv: Record<string, string> = {};
if (existsSync(envFile)) for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
  const at = line.indexOf('='); if (at > 0 && !line.startsWith('#')) localEnv[line.slice(0, at)] = line.slice(at + 1);
}
const adminEmail = process.env.E2E_ADMIN_EMAIL ?? localEnv.BOOTSTRAP_ADMIN_EMAIL ?? 'admin@english.local';
const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? localEnv.BOOTSTRAP_ADMIN_PASSWORD;
const password = 'Student@12345';
async function login(page: Page, email: string, secret: string) {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(secret);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
}
async function register(page: Page) {
  const email = 'e2e-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7) + '@english.local';
  await page.goto('/');
  await page.getByRole('tab', { name: 'Đăng ký', exact: true }).click();
  await page.getByLabel('Họ tên', { exact: true }).fill('Học viên Kiểm thử');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Tạo tài khoản thành công');
  return email;
}
test('register, login, profile and refresh persist the real account', async ({ page }) => {
  const email = await register(page);
  await login(page, email, password);
  await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
  await expect(page.getByText(email, { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Quản lý tài khoản', exact: true })).toHaveCount(0);
  const result = await page.request.get('/api/admin/users');
  expect(result.status()).toBe(403);
  await page.screenshot({ path: 'test-results/learner-desktop.png', fullPage: true });
});
test('wrong credentials show an error and preserve email', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill('student@english.local');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('student@english.local');
});
test('admin changes role, locks, unlocks and soft deletes across two browsers', async ({ browser }) => {
  if (!adminPassword) throw new Error('Set E2E_ADMIN_PASSWORD or create project .env first.');
  const learnerContext = await browser.newContext();
  const adminContext = await browser.newContext();
  try {
    const learner = await learnerContext.newPage(), admin = await adminContext.newPage();
    const email = await register(learner);
    await login(learner, email, password);
    await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible();
    await login(admin, adminEmail, adminPassword);
    await admin.getByRole('button', { name: 'Quản lý tài khoản', exact: true }).click();
    await admin.getByLabel('Tìm tài khoản', { exact: true }).fill(email);
    await admin.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    const row = admin.getByRole('row').filter({ hasText: email });
    await expect(row).toHaveCount(1);
    await row.getByRole('combobox').selectOption('TEACHER');
    await admin.getByRole('dialog').getByRole('button', { name: 'Hủy', exact: true }).click();
    await expect(row.getByRole('combobox')).toHaveValue('STUDENT');
    await row.getByRole('combobox').selectOption('TEACHER');
    await admin.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(row.getByRole('combobox')).toHaveValue('TEACHER');
    await expect(learner.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await login(learner, email, password);
    await learner.getByRole('button', { name: 'Tài khoản', exact: true }).click();
    await expect(learner.getByText('Người biên soạn', { exact: true }).first()).toBeVisible();
    await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible();
    await row.getByRole('button', { name: 'Khóa', exact: true }).click();
    await admin.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(row.getByText('Đã khóa', { exact: true })).toBeVisible();
    await expect(learner.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await row.getByRole('button', { name: 'Mở khóa', exact: true }).click();
    await admin.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(row.getByText('Hoạt động', { exact: true })).toBeVisible();
    await login(learner, email, password);
    await expect(learner.getByText('Đã kết nối', { exact: true })).toBeVisible();
    await row.getByRole('button', { name: 'Xóa', exact: true }).click();
    await admin.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(row.getByText('Đã xóa', { exact: true })).toBeVisible();
    await expect(learner.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await admin.screenshot({ path: 'test-results/admin-desktop.png', fullPage: true });
  } finally { await learnerContext.close(); await adminContext.close(); }
});
test('mobile account fits the screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, 'student@english.local', password);
  await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của bạn.' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/learner-mobile.png', fullPage: true });
});
test('network failure keeps login form input', async ({ page }) => {
  await page.goto('/');
  await page.route('**/api/auth/login', route => route.abort('failed'));
  await page.getByLabel('Email', { exact: true }).fill('student@english.local');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Chưa kết nối được máy chủ');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('student@english.local');
});
