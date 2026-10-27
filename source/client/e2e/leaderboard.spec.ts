import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { write } from './round-fixtures';
import type { Answer, AnswerKey, Item, Snapshot } from '../src/features/learning/types';

const password = 'LeaderboardTest@12345';
const seed = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as { lessons: { questions: { data: { prompt: string; answerKey: AnswerKey } }[] }[] };
async function student(page: Page, name = 'Học viên ' + randomUUID().slice(0, 8)) {
  const email = randomUUID() + '@test.local';
  await write(page.request, '/api/auth/register', { email, password, fullName: name });
  await page.goto('/#/leaderboard'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bảng xếp hạng.', exact: true })).toBeVisible();
  await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toBeVisible(); return name;
}
function answer(item: Item): Answer {
  const key = seed.lessons.flatMap(l => l.questions).find(q => q.data.prompt === item.question.prompt)!.data.answerKey;
  if (item.question.type === 'SINGLE_CHOICE') return { type: item.question.type, optionId: key.correctOptionId! };
  if (item.question.type === 'FILL_BLANK') return { type: item.question.type, values: key.blanks!.map(b => ({ blankId: b.blankId, value: b.acceptedAnswers[0] })) };
  return { type: 'MATCHING', pairs: key.pairs! };
}

test('sidebar opens the global board and group tab preserves the old league URL and keyboard navigation', async ({ page }) => {
  await student(page); await page.getByRole('button', { name: 'Trang chủ', exact: true }).click();
  await page.getByRole('button', { name: 'Bảng xếp hạng', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'Toàn hệ thống', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toContainText('Chưa xếp hạng');
  await page.getByRole('tab', { name: 'Nhóm thi đấu', exact: true }).click(); await page.reload();
  await expect(page.getByRole('tab', { name: 'Nhóm thi đấu', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { name: 'Giải đấu tuần.', exact: true })).toBeVisible();
  await page.goto('/#/league'); await expect(page.getByRole('tab', { name: 'Nhóm thi đấu', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('tab', { name: 'Nhóm thi đấu', exact: true }).focus(); await page.keyboard.press('Home');
  await expect(page.getByRole('tab', { name: 'Toàn hệ thống', exact: true })).toBeFocused();
  await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toBeVisible();
});

test('real earned XP appears without joining a league and board reads never award XP', async ({ page }) => {
  const name = await student(page); const s: Snapshot = await write(page.request, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: 1 });
  await write(page.request, '/api/attempts/' + s.attemptId + '/submit', { requestId: randomUUID(), baseRevision: s.revision, playerState: s.playerState, answers: s.items.map(item => ({ attemptQuestionId: item.attemptQuestionId, response: answer(item) })) });
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(25);
  await page.getByRole('button', { name: 'Cập nhật xếp hạng', exact: true }).click();
  const self = page.getByLabel('Vị trí của bạn', { exact: true }); await expect(self).toContainText('25 XP'); await expect(self).toContainText(name); await expect(self).toContainText('Cấp 2');
  const board = await (await page.request.get('/api/me/leaderboard')).json(); expect(board.self.rank).toBeGreaterThan(0);
  await expect(self).toContainText('Hạng #' + board.self.rank);
  expect((await (await page.request.get('/api/me/league')).json()).membership).toBeNull();
  await page.reload(); await expect(self).toContainText('25 XP');
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(25);
});

test('fixture podium keeps tied ranks and self outside the first page; mobile fits', async ({ page }) => {
  await student(page);
  const rows = Array.from({ length: 35 }, (_, i) => ({ position: i + 1, rank: i < 2 ? 1 : i + 1, displayName: i === 34 ? 'Học viên ở trang sau' : 'Học viên ' + (i + 1), activityLevel: 5, weeklyXp: i < 2 ? 100 : 100 - i, isSelf: i === 34 }));
  const now = new Date().toISOString();
  await page.route('**/api/me/leaderboard?*', route => {
    const query = new URL(route.request().url()).searchParams, pageIndex = Number(query.get('page') ?? 0), size = Number(query.get('size') ?? 20);
    return route.fulfill({ json: { serverTime: now, zone: 'Asia/Ho_Chi_Minh', weekStartAt: now, weekEndAt: now, top: rows.slice(0, 3), items: rows.slice(pageIndex * size, (pageIndex + 1) * size), self: rows[34], totalElements: 35, page: pageIndex, size, totalPages: Math.ceil(35 / size) } });
  });
  await page.getByRole('button', { name: 'Cập nhật xếp hạng', exact: true }).click();
  await expect(page.locator('.podium-place')).toHaveText(['Hạng #1', 'Hạng #1', 'Hạng #3']);
  await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toContainText('Hạng #35');
  await expect(page.locator('.ranking-me')).toHaveCount(0);
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click(); await expect(page.locator('.ranking-me')).toContainText('Học viên ở trang sau');
  await expect(page.locator('.ranking-list tbody tr')).toHaveCount(15);
  await page.screenshot({ path: test.info().outputPath('leaderboard-desktop.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: test.info().outputPath('leaderboard-mobile.png'), fullPage: true });
});

test('a failed refresh keeps the previous result visible and retry recovers', async ({ page }) => {
  await student(page); let fail = true;
  await page.route('**/api/me/leaderboard?*', route => fail ? route.abort('failed') : route.continue());
  await page.getByRole('button', { name: 'Cập nhật xếp hạng', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Đang hiển thị dữ liệu lần tải thành công trước');
  await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toBeVisible();
  fail = false; await page.getByRole('button', { name: 'Thử tải lại bảng xếp hạng', exact: true }).click(); await expect(page.getByRole('alert')).toHaveCount(0);
});

test('account switching clears the personal position and a new learner is not assigned a fake rank', async ({ page }) => {
  const previous = await student(page, 'Tài khoản bảng trước'); await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByLabel('Email', { exact: true })).toBeVisible();
  const current = await student(page, 'Tài khoản bảng mới'); const self = page.getByLabel('Vị trí của bạn', { exact: true });
  await expect(self).toContainText(current); await expect(self).not.toContainText(previous); await expect(self).toContainText('Chưa xếp hạng'); await expect(self).toContainText('0 XP');
});

test('an empty-week fixture shows a play entry and no invented podium learners', async ({ page }) => {
  await student(page); const original = await (await page.request.get('/api/me/leaderboard')).json();
  const empty = { ...original, top: [], items: [], totalElements: 0, totalPages: 0, self: { ...original.self, position: null, rank: null, weeklyXp: 0 } };
  await page.route('**/api/me/leaderboard?*', route => route.fulfill({ json: empty }));
  await page.getByRole('button', { name: 'Cập nhật xếp hạng', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tuần mới, cùng bắt đầu nhé!', exact: true })).toBeVisible();
  await expect(page.locator('.podium-card')).toHaveCount(0); await expect(page.getByLabel('Vị trí của bạn', { exact: true })).toContainText('Chưa xếp hạng');
  await page.getByRole('button', { name: 'Vào chơi và nhận XP', exact: true }).click(); await expect(page).toHaveURL(/#\/learning\?tab=catalog/);
});
