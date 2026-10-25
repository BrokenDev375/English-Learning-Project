import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { mkdirSync, appendFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { setTimeout } from 'node:timers/promises';
import { chromium, expect } from '@playwright/test';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const evidence = resolve(root, '.test-state'); mkdirSync(evidence, { recursive: true });
const log = resolve(evidence, 'm06-restart-docker.log'); writeFileSync(log, 'M06 isolated browser recovery\n');
const env = { ...process.env, APP_PORT: '5176', API_PORT: '8083', DEMO_ENABLED: 'true', BOOTSTRAP_ADMIN_ENABLED: 'true', WS_ALLOWED_ORIGINS: 'http://127.0.0.1:5176,http://localhost:5176' };
function docker(args) {
  const r = spawnSync('docker', ['compose', '--project-name', 'english-learning-m06-acceptance', '--file', 'compose.yml', ...args], { cwd: root, env, encoding: 'utf8', timeout: 600000 });
  appendFileSync(log, `\n> ${args.join(' ')}\n${r.stdout ?? ''}${r.stderr ?? ''}`);
  if (r.error || r.status !== 0) throw new Error(`Compose ${args[0]} failed; inspect ignored Docker log.`);
  return r.stdout.trim();
}
async function ready(since) {
  for (let i = 0; i < 90; i++) {
    if (docker(['logs', '--no-color', '--since', since, 'server']).includes('M02 curriculum seed completed:')) return;
    await setTimeout(1000);
  }
  throw new Error('Seed not ready');
}
const counts = () => docker(['exec', '-T', 'mysql', 'sh', '-c',
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B -e "SELECT COUNT(*) FROM attempts; SELECT COUNT(*) FROM submissions; SELECT COUNT(*) FROM answer_trials; SELECT COUNT(*) FROM question_exposures; SELECT COUNT(*) FROM lesson_progress; SELECT COUNT(*) FROM review_processed_submissions; SELECT COUNT(*) FROM review_objective_progress; SELECT COUNT(*) FROM review_difficulty_evidence; SELECT COUNT(*) FROM request_receipts; SELECT COUNT(*) FROM questions; SELECT COUNT(*) FROM lessons;"']);
const summary = { source: spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).stdout.trim(), stages: [] };
let browser, context;
try {
  let since = new Date().toISOString(); docker(['up', '-d', '--build', '--force-recreate', '--wait']); await ready(since);
  browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH });
  context = await browser.newContext({ baseURL: 'http://127.0.0.1:5176' }); const page = await context.newPage();
  const credentials = { email: `m06-restart-${randomUUID()}@test.local`, password: `Test@${randomUUID()}`, fullName: 'M06 restart test' };
  let csrf = await (await context.request.get('/api/auth/csrf')).json();
  assert.equal((await context.request.post('/api/auth/register', { headers: { [csrf.headerName]: csrf.token }, data: credentials })).status(), 201);
  await page.goto('/#/learning');
  async function login() { await page.getByLabel('Email', { exact: true }).fill(credentials.email); await page.getByLabel('Mật khẩu', { exact: true }).fill(credentials.password); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await expect(page.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible(); }
  await login();
  await page.getByRole('button', { name: 'Xem bài trong Sinh hoạt hằng ngày — Bắt đầu →', exact: true }).click();
  await page.getByRole('button', { name: /Xem giới thiệu A day with Linh/ }).click(); await page.getByRole('button', { name: 'Bắt đầu học', exact: true }).click();
  await expect(page.locator('.question-nav button')).toHaveCount(10);
  const id = page.url().match(/attempt=(\d+)/)[1]; const path = `/api/attempts/${id}`;
  let original, calls = 0;
  await page.route('**/api/attempts/*/draft', async route => {
    if (++calls === 1) { original = route.request().postDataJSON(); assert.equal((await route.fetch()).status(), 200); await route.abort(); }
    else { assert.deepEqual(route.request().postDataJSON(), original); await route.continue(); }
  });
  await page.locator('.answer-fields').getByRole('radio').last().check(); await page.getByRole('button', { name: 'Lưu nháp lên máy chủ', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi lại thao tác và đối soát', exact: true })).toBeVisible();
  const baseline = await (await context.request.get(path)).json(); const beforeCounts = counts();
  const asset = baseline.materials.find(m => m.kind === 'AUDIO' && m.asset)?.asset;
  assert.ok(asset?.url); const mediaHash = createHash('sha256').update(await (await context.request.get(asset.url)).body()).digest('hex');
  await context.setOffline(true); since = new Date().toISOString(); docker(['restart', 'server']); await ready(since);
  await context.setOffline(false); await page.reload(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible(); await login();
  await expect(page.locator('.learning-status')).toContainText('Đã lưu trên máy chủ');
  assert.ok(calls >= 2); assert.deepEqual(await (await context.request.get(path)).json(), baseline); assert.equal(counts(), beforeCounts);
  summary.stages.push({ stage: 'restart-with-lost-draft-ack', status: 'PASS', exactReplay: true, replayCalls: calls - 1, countsUnchanged: true });
  await page.unroute('**/api/attempts/*/draft');
  const syncResponse = page.waitForResponse(r => r.url().endsWith('/api/me/review/sync') && r.status() === 200);
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click(); await page.getByRole('button', { name: 'Xác nhận nộp bài', exact: true }).click(); await expect(page.locator('.result-score')).toBeVisible(); await syncResponse;
  await expect.poll(() => page.evaluate(() => new Promise((resolve, reject) => {
    const open = indexedDB.open('english-learning-recovery', 1); open.onerror = () => reject(open.error);
    open.onsuccess = () => { const db = open.result; const tx = db.transaction('commands', 'readonly'); const get = tx.objectStore('commands').getAll(); tx.oncomplete = () => { resolve(get.result.length); db.close(); }; };
  }))).toBe(0);
  const result = await (await context.request.get(path + '/result')).json();
  // Drain M04 projection with its public, idempotent service before comparing the restart baseline.
  csrf = await (await context.request.get('/api/auth/csrf')).json();
  for (let n = 0; n < 10; n++) { const r = await context.request.post('/api/me/review/sync', { headers: { [csrf.headerName]: csrf.token }, data: { requestId: randomUUID() } }); assert.equal(r.status(), 200); if ((await r.json()).remainingSubmissions === 0) break; }
  const dashboard = await (await context.request.get('/api/me/review')).json(); const progress = await (await context.request.get('/api/me/learning-progress')).json();
  const submittedCounts = counts(); const oldIds = docker(['ps', '-q']);
  since = new Date().toISOString(); docker(['up', '-d', '--force-recreate', '--no-build', '--wait']); await ready(since); assert.notEqual(docker(['ps', '-q']), oldIds);
  await page.reload(); await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible(); await login(); await expect(page.locator('.result-score')).toBeVisible();
  assert.deepEqual(await (await context.request.get(path + '/result')).json(), result);
  assert.deepEqual(await (await context.request.get('/api/me/learning-progress')).json(), progress);
  const restoredDashboard = await (await context.request.get('/api/me/review')).json(); delete dashboard.generatedAt; delete restoredDashboard.generatedAt; assert.deepEqual(restoredDashboard, dashboard);
  assert.equal(counts(), submittedCounts);
  const media = await context.request.get(asset.url); assert.equal(media.status(), 200); assert.equal(createHash('sha256').update(await media.body()).digest('hex'), mediaHash);
  const range = await context.request.get(asset.url, { headers: { Range: 'bytes=0-15' } }); assert.equal(range.status(), 206); assert.equal((await range.body()).length, 16);
  summary.stages.push({ stage: 'recreate-with-submission-and-review-ledger', status: 'PASS', immutableResult: true, countsUnchanged: true, reviewProjectionUnchanged: true, mediaHashAndRange: true });
  summary.status = 'PASS'; console.log('PASS: lost draft ACK + browser reload/login after restart; recreate preserves Submission/progress/M04 ledger and seed counts.');
} catch (e) { summary.status = 'FAIL'; summary.error = e.message; throw e; }
finally {
  await context?.close(); await browser?.close();
  try { docker(['stop']); } catch (e) { summary.status = 'FAIL'; summary.stopError = e.message; process.exitCode = 1; }
  writeFileSync(resolve(evidence, 'm06-restart.json'), JSON.stringify(summary, null, 2) + '\n');
}
