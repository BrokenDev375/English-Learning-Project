import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Answer, AnswerKey, Item, Result, Snapshot } from '../src/features/learning/types';
import { write } from './round-fixtures';

const seed = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as { lessons: { questions: { data: { prompt: string; answerKey: AnswerKey } }[] }[] };
function correct(item: Item): Answer {
  const key = seed.lessons.flatMap(l => l.questions).find(q => q.data.prompt === item.question.prompt)!.data.answerKey;
  if (item.question.type === 'SINGLE_CHOICE') return { type: item.question.type, optionId: key.correctOptionId! };
  if (item.question.type === 'FILL_BLANK') return { type: item.question.type, values: key.blanks!.map(b => ({ blankId: b.blankId, value: b.acceptedAnswers[0] })) };
  return { type: 'MATCHING', pairs: key.pairs! };
}
async function start(page: Page) {
  const email = randomUUID() + '@test.local', password = 'GameplayTest@12345';
  await write(page.request, '/api/auth/register', { email, password, fullName: 'Học viên game' });
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
  const snapshot: Snapshot = await write(page.request, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: 1 });
  await page.goto('/#/learning?attempt=' + snapshot.attemptId); await expect(page.locator('.game-world')).toBeVisible(); return snapshot;
}
async function pairCorrectly(page: Page, item: Item) {
  const answer = correct(item); if (answer.type !== 'MATCHING') throw new Error('Expected matching');
  for (const pair of answer.pairs) {
    await page.locator('.matching-left button').nth(item.question.content.leftItems!.findIndex(o => o.id === pair.leftId)).click();
    await page.locator('.matching-right button').nth(item.question.content.rightItems!.findIndex(o => o.id === pair.rightId)).click();
  }
}
async function submit(page: Page) {
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); await expect(page.locator('.game-vault')).toBeVisible();
}

test('game is visible immediately; matching lines and keys restore on mobile without revealing answers', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.emulateMedia({ reducedMotion: 'reduce' });
  const s = await start(page); expect(JSON.stringify(s)).not.toContain('answerKey');
  await expect(page.locator('.game-gate')).toHaveCount(s.items[0].question.content.options!.length);
  await page.getByRole('button', { name: 'Ghép thẻ mở rương', exact: true }).click();
  await expect(page.locator('#active-game-question')).toBeInViewport();
  const item = s.items.find(i => i.question.type === 'MATCHING')!; await pairCorrectly(page, item);
  await expect(page.locator('.matching-connections path')).toHaveCount(item.question.content.leftItems!.length);
  await expect(page.locator('.matching-treasure')).toHaveAttribute('data-open', 'false');
  await expect(page.locator('.key-explanation')).toHaveCount(0);
  await expect(page.locator('.key-slots .filled')).toHaveCount(item.question.content.leftItems!.length);
  await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click(); await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  await page.reload(); await expect(page.locator('.matching-connections path')).toHaveCount(item.question.content.leftItems!.length);
  await expect(page.locator('.matching-treasure')).toHaveAttribute('data-open', 'false');
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: test.info().outputPath('game-matching-mobile.png'), fullPage: true });
  await page.getByRole('button', { name: 'Bỏ cặp 1', exact: true }).click(); await expect(page.locator('.matching-connections path')).toHaveCount(item.question.content.leftItems!.length - 1);
});

test('guided chest opens only for the server-checked answer and locks again after editing', async ({ page }) => {
  const s = await start(page); await page.getByRole('button', { name: 'Ghép thẻ mở rương', exact: true }).click();
  const item = s.items.find(i => i.question.type === 'MATCHING')!; expect(item.practiceRole).toBe('GUIDED'); await pairCorrectly(page, item);
  await page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true }).click(); await expect(page.locator('.matching-treasure')).toHaveAttribute('data-open', 'true');
  await page.screenshot({ path: test.info().outputPath('game-chest-open-desktop.png'), fullPage: true });
  await page.getByRole('button', { name: 'Bỏ cặp 1', exact: true }).click(); await expect(page.locator('.matching-treasure')).toHaveAttribute('data-open', 'false');
  const independent = s.items.findIndex(i => i.question.type === 'MATCHING' && i.practiceRole === 'INDEPENDENT');
  await page.locator('.question-nav button').nth(independent).click(); await pairCorrectly(page, s.items[independent]);
  await expect(page.getByRole('button', { name: 'Kiểm tra đáp án', exact: true })).toHaveCount(0);
  await expect(page.locator('.key-explanation')).toHaveCount(0); await expect(page.locator('.matching-treasure')).toHaveAttribute('data-open', 'false');
});

test('submitted game uses immutable results; replay animation cannot change score or XP', async ({ page }) => {
  const s = await start(page);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const saved = await page.request.put('/api/attempts/' + s.attemptId + '/draft', { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID(), baseRevision: s.revision, playerState: s.playerState, answers: s.items.map(item => ({ attemptQuestionId: item.attemptQuestionId, response: correct(item) })) } });
  expect(saved.ok(), await saved.text()).toBeTruthy();
  await page.reload(); await expect(page.locator('.game-progress')).toContainText('10/10'); await submit(page);
  await expect(page.locator('.game-chests .is-open')).toHaveCount(10); await expect(page.getByLabel('3/3 sao', { exact: true })).toBeVisible();
  const path = '/api/attempts/' + s.attemptId + '/result'; const before: Result = await (await page.request.get(path)).json(); expect(before.correctCount).toBe(10);
  await expect.poll(async () => (await (await page.request.get('/api/me/gamification')).json()).totalXp, { timeout: 20000 }).toBe(25);
  let mutations = 0; page.on('request', request => { if (request.method() === 'POST' && /\/api\/attempts\//.test(request.url())) mutations++; });
  await page.getByRole('button', { name: 'Rương câu 3, đã mở', exact: true }).click();
  await page.getByRole('button', { name: 'Xem lại hoạt cảnh', exact: true }).click(); await page.getByRole('button', { name: 'Xem lại hoạt cảnh', exact: true }).click();
  expect(mutations).toBe(0); expect(await (await page.request.get(path)).json()).toEqual(before);
  expect((await (await page.request.get('/api/me/gamification')).json()).totalXp).toBe(25);
  await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.screenshot({ path: test.info().outputPath('game-results-mobile.png'), fullPage: true });
});

test('empty early submission has locked chests and no earned stars', async ({ page }) => {
  await start(page); await submit(page); await expect(page.locator('.game-chests .is-open')).toHaveCount(0);
  await expect(page.getByLabel('0/3 sao', { exact: true })).toBeVisible(); await expect(page.locator('.game-vault-caption')).toContainText('Rương còn khóa');
  await page.getByRole('button', { name: 'Rương câu 2, còn khóa', exact: true }).click(); await page.getByRole('button', { name: 'Xem lại hoạt cảnh', exact: true }).click();
  await expect(page.locator('.game-vault-scene .is-open')).toHaveCount(0); await expect(page.locator('.result-score')).toContainText('Bài học chưa hoàn thành');
});
