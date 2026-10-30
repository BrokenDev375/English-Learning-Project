import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { completeAnswers, write } from './round-fixtures';
import type { Snapshot } from '../src/features/learning/types';

async function student(page: Page) {
  const email = randomUUID() + '@round-map.test', password = 'RoundMap@12345';
  await write(page.request, '/api/auth/register', { email, password, fullName: 'Học viên bản đồ' });
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.locator('.home-play-button')).toBeVisible();
}
async function finishLesson(page: Page, index: number) {
  await page.locator('.catalog-grid .catalog-card').nth(index).getByRole('button').click();
  await page.getByRole('button', { name: 'Bắt đầu chơi', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10);
  const id = new URL(page.url()).hash.match(/attempt=(\d+)/)![1]; const s: Snapshot = await (await page.request.get('/api/attempts/' + id)).json();
  // Put complete responses into the real draft API, then exercise submit/review/confirm in the UI.
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const draft = await page.request.put('/api/attempts/' + id + '/draft', { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: s.revision, answers: completeAnswers(s), playerState: s.playerState } }); expect(draft.status()).toBe(200);
  await page.reload(); await expect(page.locator('.learning-status')).toContainText('10/10');
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); await expect(page.locator('.submission-review-items > li')).toHaveCount(10);
  await page.getByRole('button', { name: 'Xác nhận hoàn thành bài học', exact: true }).click(); await expect(page.locator('.round-overview')).toBeVisible();
}

test('home has one clear play entry and personal review; locked rounds explain what to do on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await student(page);
  await expect(page.locator('.home-play-button')).toHaveText(/Vào chơi vòng 1/); await expect(page.locator('.home-rounds')).toHaveCount(0); await expect(page.locator('.home-mode-cards article')).toHaveCount(2); await expect(page.getByText('Luyện tự chọn', { exact: true })).toHaveCount(0);
  await page.screenshot({ path: 'test-results/round-map-home-mobile.png', fullPage: true });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Vào chơi', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bản đồ vòng chơi.' })).toBeVisible();
  await expect(page.locator('.round-rules p')).toHaveCount(3); await expect(page.locator('.round-stop.open')).toHaveCount(1); await expect(page.locator('.round-stop.locked')).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Vòng 2 đang khóa', exact: true })).toBeDisabled(); await expect(page.locator('.round-stop.locked').first()).toContainText('Hoàn thành Vòng 1');
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: 'test-results/round-map-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.screenshot({ path: 'test-results/round-map-desktop.png', fullPage: true });
  await page.getByLabel('Tìm vòng học', { exact: true }).fill('Ăn uống'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click(); await expect(page.locator('.round-stop')).toHaveCount(1); await expect(page.getByRole('button', { name: 'Vòng 2 đang khóa' })).toBeDisabled();
  await page.goto('/#/learning?unit=2'); await expect(page.getByRole('heading', { name: 'Vòng 2 đang khóa.' })).toBeVisible(); await expect(page.getByRole('button', { name: 'Bắt đầu chơi', exact: true })).toHaveCount(0);
  await page.goto('/#/learning?lesson=3'); await expect(page.getByRole('heading', { name: 'Vòng 2 đang khóa.' })).toBeVisible();
  const csrf = await (await page.request.get('/api/auth/csrf')).json(); const denied = await page.request.post('/api/attempts', { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), mode: 'LEARN', lessonId: 3 } }); expect(denied.status()).toBe(409); expect((await denied.json()).code).toBe('ROUND_LOCKED');
  await page.getByRole('button', { name: 'Về vòng đang mở →', exact: true }).click(); await expect(page.locator('.round-overview')).toContainText('0/2');
});

test('finishing both lessons opens the next round and completed rounds remain replayable after reload', async ({ page }) => {
  test.setTimeout(90000); await student(page); await page.locator('.home-play-button').click(); await expect(page.locator('.round-overview')).toContainText('0/2');
  await finishLesson(page, 0); await expect(page.locator('.round-overview')).toContainText('1/2');
  await page.getByRole('button', { name: 'Về bản đồ vòng chơi', exact: true }).click(); await expect(page.getByRole('button', { name: 'Vòng 2 đang khóa' })).toBeDisabled();
  await page.getByRole('button', { name: 'Chơi tiếp vòng 1 →', exact: true }).click(); await finishLesson(page, 1); await expect(page.locator('.round-overview')).toContainText('2/2');
  await page.getByRole('button', { name: 'Về bản đồ vòng chơi', exact: true }).click(); await expect(page.getByRole('button', { name: 'Chơi vòng 2 →', exact: true })).toBeEnabled(); await expect(page.getByRole('button', { name: 'Vòng 3 đang khóa' })).toBeDisabled();
  await page.reload(); await expect(page.getByRole('button', { name: 'Chơi vòng 2 →', exact: true })).toBeEnabled(); await expect(page.getByRole('button', { name: 'Chơi lại vòng 1', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Chơi lại vòng 1', exact: true }).click(); await page.locator('.catalog-grid .catalog-card').first().getByRole('button').click(); await page.getByRole('button', { name: 'Chơi lại bài', exact: true }).click(); await expect(page.locator('.question-nav button')).toHaveCount(10);
});

test('old practice URLs return to the map and review explains its purpose without exposing a new practice form', async ({ page }) => {
  await student(page); const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await expect(nav.getByRole('button', { name: 'Luyện tự chọn', exact: true })).toHaveCount(0);
  await page.goto('/#/learning?tab=practice'); await expect(page.getByRole('heading', { name: 'Bản đồ vòng chơi.' })).toBeVisible(); await expect(page).toHaveURL(/tab=catalog/);
  await expect(page.locator('.practice-form')).toHaveCount(0); await expect(page.getByRole('tab', { name: 'Luyện tự chọn', exact: true })).toHaveCount(0);
  await nav.getByRole('button', { name: 'Ôn tập cá nhân', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Ôn tập cá nhân.' })).toBeVisible();
  await expect(page.locator('.review-option')).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Ôn tất cả', exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Ôn các câu sai', exact: true })).toBeDisabled();
  await nav.getByRole('button', { name: 'Vào chơi', exact: true }).click(); await expect(page.locator('.round-map')).toBeVisible();
});
