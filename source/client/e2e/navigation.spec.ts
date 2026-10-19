import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const env = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#') && line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
async function signIn(page: Page, student = false, path = '/') {
  await page.goto(path);
  await page.getByLabel('Email', { exact: true }).fill(student ? env.DEMO_STUDENT_EMAIL ?? 'student@english.local' : env.DEMO_TEACHER_EMAIL ?? 'teacher@english.local');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(student ? env.DEMO_STUDENT_PASSWORD ?? 'Student@12345' : env.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
}
async function content(page: Page) { await page.getByRole('button', { name: 'Quản lý học liệu', exact: true }).click(); }

test('browser Back and Forward traverse the sidebar and catalog tabs, including refresh', async ({ page }) => {
  await signIn(page); await content(page);
  await page.getByRole('tab', { name: 'Chủ đề', exact: true }).click();
  await page.getByRole('tab', { name: 'Mục tiêu học', exact: true }).click();
  await page.getByRole('tab', { name: 'Unit', exact: true }).click();
  const length = await page.evaluate(() => history.length);
  await page.getByRole('tab', { name: 'Unit', exact: true }).click();
  expect(await page.evaluate(() => history.length)).toBe(length);
  await page.goBack(); await expect(page.getByRole('tab', { name: 'Mục tiêu học', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.goBack(); await expect(page.getByRole('tab', { name: 'Chủ đề', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.goForward(); await expect(page.getByRole('tab', { name: 'Mục tiêu học', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.reload(); await expect(page.getByRole('tab', { name: 'Mục tiêu học', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của bạn.' })).toBeVisible();
  await page.goBack(); await expect(page.getByRole('tab', { name: 'Mục tiêu học', exact: true })).toHaveAttribute('aria-selected', 'true');
});

test('catalog forms and material details can be revisited with Back, Forward and direct URLs', async ({ page }) => {
  await signIn(page); await content(page);
  await page.getByRole('button', { name: '+ Thêm unit', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Thêm unit', exact: true })).toBeVisible();
  await page.goBack(); await expect(page.getByRole('heading', { name: 'Thêm unit', exact: true })).toHaveCount(0);
  await page.goForward(); await expect(page.getByRole('heading', { name: 'Thêm unit', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Hủy', exact: true }).click();
  await page.getByRole('button', { name: 'Tài liệu', exact: true }).click();
  await page.getByLabel('Tìm tài liệu', { exact: true }).fill('A day with Linh');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Mở A day with Linh', exact: true }).click();
  await expect(page.locator('.material-editor-heading').getByRole('heading', { name: 'A day with Linh', exact: true })).toBeVisible();
  const url = page.url();
  await page.goBack(); await expect(page.getByRole('heading', { name: 'Tài liệu học tập.' })).toBeVisible();
  await page.goForward(); await expect(page.locator('.material-editor-heading').getByRole('heading', { name: 'A day with Linh', exact: true })).toBeVisible();
  await page.reload(); await expect(page.locator('.material-editor-heading').getByRole('heading', { name: 'A day with Linh', exact: true })).toBeVisible();
  const second = await page.context().newPage(); await second.goto(url);
  await expect(second.locator('.material-editor-heading').getByRole('heading', { name: 'A day with Linh', exact: true })).toBeVisible();
  await second.getByRole('button', { name: 'Về danh sách', exact: true }).click();
  await expect(second.getByRole('heading', { name: 'Tài liệu học tập.' })).toBeVisible();
  await second.close();
  expect(await page.evaluate(() => JSON.stringify(history.state))).not.toMatch(/answerKey|transcript|publishedVersionId/);
});

test('question and lesson forms and Unit ordering restore their screens after history navigation', async ({ page }) => {
  await signIn(page); await content(page);
  for (const [area, title, heading] of [['Câu hỏi', 'Thêm câu hỏi', 'Câu hỏi mới'], ['Bài học', 'Thêm bài học', 'Bài học mới']]) {
    await page.getByRole('button', { name: area, exact: true }).click();
    await page.getByRole('button', { name: '+ ' + title, exact: true }).click();
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await page.goBack(); await expect(page.getByRole('heading', { name: heading, exact: true })).toHaveCount(0);
    await page.goForward(); await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await page.reload(); await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Về danh sách', exact: true }).click();
  }
  await page.getByRole('combobox', { name: 'Unit', exact: true }).selectOption({ label: 'Sinh hoạt hằng ngày — Bắt đầu' });
  await page.getByRole('button', { name: 'Sắp xếp bài trong Unit đã chọn →', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sinh hoạt hằng ngày — Bắt đầu', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Sinh hoạt hằng ngày — Bắt đầu', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Về danh sách bài', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bài học của bạn.' })).toBeVisible();
});

test('learner Unit and lesson introduction survive Back, Forward and refresh', async ({ page }) => {
  await signIn(page, true);
  await page.getByRole('button', { name: 'Khám phá bài học', exact: true }).click();
  await page.getByRole('button', { name: 'Xem bài trong Sinh hoạt hằng ngày — Bắt đầu →', exact: true }).click();
  await page.getByRole('button', { name: 'Xem giới thiệu A day with Linh — Morning routine →', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bạn sẽ học gì?', exact: true })).toBeVisible();
  await page.goBack(); await expect(page.getByRole('heading', { name: 'Sinh hoạt hằng ngày — Bắt đầu.' })).toBeVisible();
  await page.goBack(); await expect(page.getByRole('heading', { name: 'Bài học tiếng Anh.' })).toBeVisible();
  await page.goForward(); await page.goForward();
  await expect(page.getByRole('heading', { name: 'Bạn sẽ học gì?', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Bạn sẽ học gì?', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Về danh sách bài trong Unit', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Sinh hoạt hằng ngày — Bắt đầu.' })).toBeVisible();
});

test('login preserves a requested screen and browser history cannot restore a signed-out session', async ({ page }) => {
  await signIn(page, false, '/#/content?area=questions');
  await expect(page.getByRole('heading', { name: 'Câu hỏi học tập.' })).toBeVisible();
  await page.getByRole('button', { name: 'Tài khoản', exact: true }).click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await page.goBack();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Quản lý học liệu', exact: true })).toHaveCount(0);
});
