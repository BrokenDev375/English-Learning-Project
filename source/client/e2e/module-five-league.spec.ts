import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Answer, Item, Snapshot } from '../src/features/learning/types';

const seed = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8'));
const questions = seed.lessons.flatMap((l: { questions: unknown[] }) => l.questions) as { data: { prompt: string; answerKey: { correctOptionId: string; blanks: { blankId: string; acceptedAnswers: string[] }[]; pairs: { leftId: string; rightId: string }[] } } }[];
const password = 'LeagueTest@12345';
async function post(page: Page, path: string, body: unknown) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post(path, { data: body, headers: { [csrf.headerName]: csrf.token } });
  expect(response.ok(), await response.text()).toBeTruthy(); return response.json();
}
async function learner(page: Page, name: string) {
  const email = `league-${Date.now()}-${crypto.randomUUID()}@test.local`;
  await post(page, '/api/auth/register', { email, password, fullName: name });
  await page.goto('/#/league'); await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Giải đấu tuần.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tham gia tuần này', exact: true })).toBeVisible();
}
function answer(item: Item): Answer {
  const key = questions.find(q => q.data.prompt === item.question.prompt)!.data.answerKey;
  if (item.question.type === 'SINGLE_CHOICE') return { type: 'SINGLE_CHOICE', optionId: key.correctOptionId };
  if (item.question.type === 'FILL_BLANK') return { type: 'FILL_BLANK', values: key.blanks.map(b => ({ blankId: b.blankId, value: b.acceptedAnswers[0] })) };
  return { type: 'MATCHING', pairs: key.pairs };
}
async function practice(page: Page, topicIndex: number, count = 3) {
  const units = await (await page.request.get('/api/units?size=50')).json();
  const unit = units.items.find((u: { title: string }) => u.title.includes(topicIndex ? 'Trường' : 'Sinh hoạt hằng ngày'));
  const snapshot: Snapshot = await post(page, '/api/attempts', { requestId: crypto.randomUUID(), mode: 'PRACTICE', topicId: unit.topicId, difficulty: 'EASY', count });
  await post(page, `/api/attempts/${snapshot.attemptId}/submit`, { requestId: crypto.randomUUID(), baseRevision: snapshot.revision, playerState: snapshot.playerState,
    answers: snapshot.items.map(item => ({ attemptQuestionId: item.attemptQuestionId, response: answer(item) })) });
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).backlog.pendingSubmissions, { timeout: 20000 }).toBe(0);
}
async function memberRow(page: Page, name: string) {
  for (let i = 0; i < 3; i++) {
    const row = page.getByRole('row').filter({ hasText: name });
    if (await row.count()) return row;
    const next = page.locator('.league-board').getByRole('button', { name: 'Trang sau', exact: true });
    if (await next.isDisabled()) break;
    await next.click();
    await expect(page.locator('.league-board .pagination > span')).toContainText(`Trang ${i + 2}/`);
  }
  return page.getByRole('row').filter({ hasText: name });
}

test('a two-member active group has a promotion slot and displays the easier current rules', async ({ page, browser }) => {
  test.setTimeout(60000); await learner(page, 'Nhóm nhỏ A'); await page.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
  const context = await browser.newContext();
  try {
    const other = await context.newPage(); await learner(other, 'Nhóm nhỏ B'); await other.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
    await practice(page, 0, 3); await practice(other, 0, 2);
    await expect.poll(async () => (await (await page.request.get('/api/me/league/leaderboard')).json()).groupStats.movementEnabled, { timeout: 20000 }).toBe(true);
    const overview = await (await page.request.get('/api/me/league')).json(), board = await (await page.request.get('/api/me/league/leaderboard')).json();
    expect(overview.season.rules.minimumMembers).toBe(2); expect(overview.season.rules.minimumActive).toBe(2); expect(board.groupStats.memberCount).toBe(2); expect(board.groupStats.promotionQuota).toBe(1); expect(board.self.outcome).toBe('PROMOTE');
    await page.reload(); await page.locator('.league-rules summary').click(); await expect(page.locator('.league-rules')).toContainText('đủ 2 thành viên và 2 người có XP'); await expect(page.locator('.league-rules')).toContainText('ít nhất 1 suất'); await expect(page.locator('.league-self')).toContainText('Lên bậc');
  } finally { await context.close(); }
});

test('join lost ACK replays same request and refresh keeps the membership', async ({ page }) => {
  await learner(page, 'Học viên kiểm tra tham gia');
  const ids: string[] = []; let lost = false;
  await page.route('**/api/me/league/join', async route => {
    ids.push(route.request().postDataJSON().requestId);
    if (!lost) { lost = true; const response = await route.fetch(); expect(response.ok()).toBeTruthy(); await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
  await page.getByRole('button', { name: 'Kiểm tra lần tham gia', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bảng nhóm của bạn' })).toBeVisible();
  expect(ids).toHaveLength(2); expect(ids[0]).toBe(ids[1]);
  const before = await (await page.request.get('/api/me/league')).json();
  await page.reload(); await expect(page.locator('.league-self')).toBeVisible();
  const after = await (await page.request.get('/api/me/league')).json();
  expect(after.membership).toEqual(before.membership);
  await page.screenshot({ path: test.info().outputPath('league-desktop.png'), fullPage: true });
});

test('lifetime XP before join stays outside the league and two independent sessions see live group XP', async ({ page, browser }) => {
  test.setTimeout(60000);
  let nameA = `Bạn A ${Date.now()}`; const nameB = `Bạn B ${Date.now()}`;
  for (let i = 0; i < 2; i++) {
    await learner(page, nameA); await practice(page, 0);
    await page.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
    await expect(page.locator('.league-self')).toBeVisible();
    const firstBoard = await (await page.request.get('/api/me/league/leaderboard')).json();
    if (firstBoard.groupStats.memberCount < 25) break;
    // If A took the last slot, a new learner starts in the next group; nobody moves groups.
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); nameA += ' mới';
  }
  await expect(page.locator('.league-self').first()).toContainText('0');
  const otherContext = await browser.newContext();
  try {
    const other = await otherContext.newPage(); await learner(other, nameB);
    await other.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
    await practice(other, 0);
    const own = await (await page.request.get('/api/me/league')).json(), peer = await (await other.request.get('/api/me/league')).json();
    expect(own.membership.groupId).toBe(peer.membership.groupId);
    expect(own.membership.weeklyXp).toBe(0); expect(peer.membership.weeklyXp).toBe(15);
    await expect(other.locator('.league-self > div').first()).toContainText('15', { timeout: 20000 });
    await expect(await memberRow(page, nameB)).toContainText('15');
    await expect(await memberRow(other, nameA)).toContainText('0');
  } finally { await otherContext.close(); }
});

test('league rules, private query rejection and mobile layout are visible', async ({ page }) => {
  await learner(page, 'Học viên màn hình nhỏ');
  await page.getByRole('button', { name: 'Tham gia tuần này', exact: true }).click();
  await page.locator('.league-rules summary').click();
  await expect(page.getByText('Khối hòa vắt ranh giới giữ bậc', { exact: false })).toBeVisible();
  const invalid = await page.request.get('/api/me/league/leaderboard?groupId=9999'); expect(invalid.status()).toBe(400);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.league-self')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('league-mobile.png'), fullPage: true });
});

test('fixture UI shows settlement wait and frozen history without automatic join', async ({ page }) => {
  await learner(page, 'Học viên kiểm tra trạng thái chốt');
  const overview = await (await page.request.get('/api/me/league')).json();
  const fixtures = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/test/resources/contracts/m05-fixtures.json'), 'utf8'));
  const history = fixtures.valid.find((f: { name: string }) => f.name === 'm05LeagueHistorySuccess').body;
  const previousEnd = Date.parse(overview.season.startAt);
  history.items[0].season.startAt = new Date(previousEnd - 7 * 86400000).toISOString();
  history.items[0].season.endAt = new Date(previousEnd).toISOString();
  history.items[0].season.cutoffAt = new Date(previousEnd + 120000).toISOString();
  history.items[0].result.finalizedAt = new Date(previousEnd + 121000).toISOString();
  await page.route('**/api/me/league', route => route.fulfill({ json: { ...overview, state: 'WAITING_PREVIOUS_SETTLEMENT', canJoin: false } }));
  await page.route('**/api/me/league/history?*', route => route.fulfill({ json: history }));
  await page.getByRole('button', { name: 'Tải lại bảng', exact: true }).click();
  await expect(page.getByText('Nhóm tuần trước của bạn đang chờ chốt.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tham gia tuần này', exact: true })).toHaveCount(0);
  await expect(page.locator('.reward-history')).toContainText('Giữ bậc → Đồng');
  await expect(page.locator('.reward-history')).toContainText('#1/1');
  await page.screenshot({ path: test.info().outputPath('league-history-fixture.png'), fullPage: true });
});
