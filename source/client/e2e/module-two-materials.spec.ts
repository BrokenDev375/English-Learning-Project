import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const envPath = resolve(process.cwd(), '../../.env');
const local = Object.fromEntries((existsSync(envPath) ? readFileSync(envPath, 'utf8') : '').split(/\r?\n/)
  .filter(line => line && !line.startsWith('#') && line.includes('='))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const demoEmail = local.DEMO_TEACHER_EMAIL ?? 'teacher@english.local';
const demoPassword = local.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345';
const media = (file: string) => resolve(process.cwd(), '../server/src/main/resources/demo/materials/' + file);
let authorEmail: string;
test.beforeAll(async ({ request }) => {
  authorEmail = 'e2e-material-' + Date.now() + '@english.local';
  const csrf = async () => await (await request.get('/api/auth/csrf')).json() as { token: string; headerName: string };
  let token = await csrf();
  expect((await request.post('/api/auth/login', { headers: { [token.headerName]: token.token }, data: {
    email: process.env.E2E_ADMIN_EMAIL ?? local.BOOTSTRAP_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD ?? local.BOOTSTRAP_ADMIN_PASSWORD,
  } })).status()).toBe(200);
  token = await csrf();
  const created = await request.post('/api/auth/register', { headers: { [token.headerName]: token.token }, data: { email: authorEmail, password: 'Teacher@12345', fullName: 'Tác giả kiểm thử tài liệu' } });
  expect(created.status()).toBe(201);
  const user = await created.json() as { id: number };
  expect((await request.patch('/api/admin/users/' + user.id + '/role', { headers: { [token.headerName]: token.token }, data: { role: 'TEACHER' } })).status()).toBe(200);
});
async function login(page: Page, email = authorEmail) {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(email === demoEmail ? demoPassword : 'Teacher@12345');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Quản lý học liệu', exact: true }).click();
  await page.getByRole('button', { name: 'Tài liệu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài liệu học tập.' })).toBeVisible();
}
async function start(page: Page, title: string, kind = 'READING') {
  await page.getByRole('button', { name: '+ Thêm tài liệu', exact: true }).click();
  await page.getByRole('combobox', { name: 'Loại tài liệu', exact: true }).selectOption(kind);
  await page.getByLabel('Tên tài liệu', { exact: true }).fill(title);
  await page.getByLabel('Tác giả hoặc nguồn', { exact: true }).fill('Học liệu tự soạn để kiểm thử');
  await page.getByLabel('Giấy phép / điều kiện sử dụng', { exact: true }).fill('Chỉ dùng kiểm thử local');
}
async function save(page: Page) {
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu bản nháp trên máy chủ.' })).toBeVisible();
}
async function publish(page: Page) {
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await page.getByRole('button', { name: 'Xuất bản tài liệu', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã xuất bản tài liệu.' })).toBeVisible();
}

test('reading drafts validate, publish, and preserve the previous published version', async ({ page }) => {
  await login(page); const title = 'Đọc thử e2e-' + Date.now(); await start(page, title);
  await save(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Cần nhập nội dung đoạn đọc');
  await expect(page.getByRole('button', { name: 'Xuất bản tài liệu', exact: true })).toBeDisabled();
  const original = 'Linh reads a book every evening.';
  await page.getByRole('textbox', { name: 'Nội dung đoạn đọc', exact: true }).fill(original); await save(page); await publish(page);
  await expect(page.getByText(original, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true }).click();
  await page.getByRole('textbox', { name: 'Nội dung đoạn đọc', exact: true }).fill('She reads two books on Sunday.'); await save(page);
  await page.getByRole('tab', { name: 'Bản đã xuất bản', exact: true }).click();
  await expect(page.getByText(original, { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Bản nháp', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Nội dung đoạn đọc', exact: true })).toHaveValue('She reads two books on Sunday.');
});

test('audio upload survives a lost response and the preview plays within the selected segment', async ({ page }) => {
  await login(page); await start(page, 'Nghe thử e2e-' + Date.now(), 'AUDIO');
  await page.getByLabel('File audio', { exact: true }).setInputFiles(media('daily-routine.wav'));
  let originalId = '', calls = 0;
  await page.route('**/api/assets', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const requestId = route.request().postData()?.match(/name="requestId"\r\n\r\n([a-f0-9-]+)/)?.[1];
    expect(requestId).toBeTruthy(); calls++;
    if (calls === 1) {
      originalId = requestId!;
      // Chromium interception omits file bytes from multipart postData. Commit the
      // same file and requestId with this browser context, then lose its response.
      const response = await page.request.post(route.request().url(), {
        headers: { 'X-CSRF-TOKEN': route.request().headers()['x-csrf-token'] },
        multipart: { requestId: originalId, file: { name: 'daily-routine.wav', mimeType: 'audio/wav', buffer: readFileSync(media('daily-routine.wav')) } },
      });
      expect(response.status(), await response.text()).toBe(201);
      return route.abort('failed');
    }
    expect(requestId).toBe(originalId); return route.continue();
  });
  await page.getByRole('button', { name: 'Tải file lên', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Chưa kết nối được máy chủ');
  await page.getByRole('button', { name: 'Thử lại thao tác', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã tải file'); expect(calls).toBe(2);
  await page.getByLabel('Bản chép lời', { exact: true }).fill('Transcript kept out of the learner preview.');
  await page.getByLabel('Bắt đầu từ (giây)', { exact: true }).fill('1');
  await page.getByLabel('Kết thúc tại (giây, để trống để nghe hết)', { exact: true }).fill('4');
  await save(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  const player = page.locator('audio');
  await expect.poll(() => player.evaluate(element => (element as HTMLAudioElement).readyState)).toBeGreaterThanOrEqual(1);
  await player.evaluate(async element => { const audio = element as HTMLAudioElement; audio.muted = true; audio.currentTime = 1; await audio.play(); });
  await expect.poll(() => player.evaluate(element => (element as HTMLAudioElement).currentTime)).toBeGreaterThan(1);
  await player.evaluate(element => { (element as HTMLAudioElement).currentTime = 7; });
  await expect.poll(() => player.evaluate(element => (element as HTMLAudioElement).currentTime)).toBeLessThanOrEqual(4.01);
  await page.getByRole('button', { name: 'Xuất bản tài liệu', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã xuất bản tài liệu.');
  await expect(page.getByText('Transcript kept out of the learner preview.', { exact: true })).toHaveCount(0);
});

test('images enforce upload size, display, publish, retire and can start a new draft', async ({ page }) => {
  await login(page); const title = 'Ảnh thử e2e-' + Date.now(); await start(page, title, 'IMAGE');
  await page.getByLabel('File ảnh', { exact: true }).setInputFiles({ name: 'oversized.png', mimeType: 'image/png', buffer: Buffer.alloc(5242881) });
  await page.getByRole('button', { name: 'Tải file lên', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('File tối đa 5 MiB');
  await page.getByLabel('File ảnh', { exact: true }).setInputFiles(media('daily-routine.png'));
  await page.getByRole('button', { name: 'Tải file lên', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã tải file');
  await page.getByLabel('Mô tả ảnh', { exact: true }).fill('Lịch sinh hoạt minh họa của Linh');
  await page.getByLabel('Chú thích ảnh', { exact: true }).fill('Talk about her day.');
  await save(page); await publish(page);
  await expect.poll(() => page.getByRole('img', { name: 'Lịch sinh hoạt minh họa của Linh' }).evaluate(element => (element as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  await page.getByRole('button', { name: 'Ngừng cấp mới', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã ngừng cấp tài liệu');
  await page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true }).click();
  await page.getByRole('button', { name: 'Bỏ bản nháp', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận bỏ bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài liệu học tập.' })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Đã bỏ bản nháp.' })).toBeVisible();
});

test('another author can view published demo material but cannot edit it', async ({ page }) => {
  await login(page);
  await page.getByRole('combobox', { name: 'Phạm vi', exact: true }).selectOption('PUBLISHED');
  await page.getByRole('textbox', { name: 'Tìm tài liệu', exact: true }).fill('A day with Linh');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Mở A day with Linh', exact: true }).click();
  await expect(page.getByText('Every morning, Linh gets up at seven.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tạo bản chỉnh sửa', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Ngừng cấp mới', exact: true })).toHaveCount(0);
});

test('demo library and reader fit desktop and mobile screens', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 }); await login(page, demoEmail);
  await page.getByRole('textbox', { name: 'Tìm tài liệu', exact: true }).fill('Linh');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'A day with Linh', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Listen: Linh’s daily routine', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/module-2-materials-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/module-2-materials-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Mở A day with Linh', exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/module-2-reader-mobile.png', fullPage: true });
});
