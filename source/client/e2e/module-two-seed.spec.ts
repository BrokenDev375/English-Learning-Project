import { test, expect } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { CatalogPage, Unit } from '../src/features/content/catalog';
import type { ContentRoot, LessonVersion, QuestionVersion, Summary } from '../src/features/content/authoring';
import type { MaterialRoot } from '../src/features/content/materials';
const local = Object.fromEntries(readFileSync(resolve(process.cwd(), '../../.env'), 'utf8').split(/\r?\n/).filter(line => line && !line.startsWith('#') && line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
interface Course { unitSeedKey: string; data: { title: string }; questions: { practiceRole: string; position: number; materialKey: string | null; data: { prompt: string; type: string } }[] }
interface MaterialSpec { seedKey: string; file: string | null; sha256: string | null; data: { kind: string; title: string } }
const curriculum = JSON.parse(readFileSync(resolve(process.cwd(), '../server/src/main/resources/demo/curriculum-v1.json'), 'utf8')) as { lessons: Course[]; materials: MaterialSpec[] };
async function get<T>(request: APIRequestContext, path: string): Promise<T> { const r = await request.get(path); expect(r.status(), await r.text()).toBe(200); return await r.json() as T; }
async function login(request: APIRequestContext, student = false) { const token = await get<{ token: string; headerName: string }>(request, '/api/auth/csrf'); const r = await request.post('/api/auth/login', { headers: { [token.headerName]: token.token }, data: { email: student ? local.DEMO_STUDENT_EMAIL ?? 'student@english.local' : local.DEMO_TEACHER_EMAIL ?? 'teacher@english.local', password: student ? local.DEMO_STUDENT_PASSWORD ?? 'Student@12345' : local.DEMO_TEACHER_PASSWORD ?? 'Teacher@12345' } }); expect(r.status()).toBe(200); }
async function pages<T>(request: APIRequestContext, path: string) { const all: T[] = []; for (let page = 0; ; page++) { const result = await get<CatalogPage<T>>(request, path + (path.includes('?') ? '&' : '?') + 'size=50&page=' + page); all.push(...result.items); if (page + 1 >= result.totalPages) return all; } }

test('complete curriculum has six published lessons and ninety distinct version slots with 5/5/5 groups', async ({ request }) => {
  test.setTimeout(60000); await login(request);
  const bank = await pages<Summary>(request, '/api/author/questions?scope=OWN');
  const lessonBank = await pages<Summary>(request, '/api/author/lessons?scope=OWN');
  const ids = new Set<number>(), origins = new Set<string>(), types: Record<string, number> = {};
  for (const course of curriculum.lessons) {
    const row = lessonBank.find(l => l.title === course.data.title); expect(row).toBeTruthy();
    const root = await get<ContentRoot>(request, '/api/author/lessons/' + row!.id); expect(root.status).toBe('PUBLISHED');
    const version = root.published as LessonVersion; expect(version.questions).toHaveLength(15);
    for (const role of ['GUIDED', 'INDEPENDENT', 'REVIEW_VARIANT']) expect(version.questions.filter(s => s.practiceRole === role).map(s => s.position)).toEqual([0, 1, 2, 3, 4]);
    const questions = await Promise.all(course.questions.map(async q => {
      const row = bank.find(item => item.promptExcerpt === q.data.prompt.slice(0, 200)); expect(row).toBeTruthy(); return await get<ContentRoot>(request, '/api/author/questions/' + row!.id);
    }));
    for (let i = 0; i < questions.length; i++) { const q = questions[i], v = q.published as QuestionVersion, expected = course.questions[i];
      expect(q.status).toBe('PUBLISHED'); expect(version.questions).toContainEqual({ questionVersionId: v.id, practiceRole: expected.practiceRole, position: expected.position }); expect(v.type).toBe(expected.data.type); expect(v.prompt).toBe(expected.data.prompt); expect(ids.has(q.id)).toBe(false); ids.add(q.id); origins.add(q.originKey!); types[v.type] = (types[v.type] ?? 0) + 1;
    }
  }
  expect(ids.size).toBe(90); expect(origins.size).toBe(90); expect(types).toEqual({ SINGLE_CHOICE: 30, FILL_BLANK: 30, MATCHING: 30 });
  await login(request, true); const units = await pages<Unit>(request, '/api/units');
  expect(units.filter(unit => /^Unit e2e-authoring-\d{13}$/.test(unit.title))).toHaveLength(0);
  for (const title of ['Sinh hoạt hằng ngày — Bắt đầu', 'Ăn uống và mua sắm — Bắt đầu', 'Đi lại và chỉ đường — Bắt đầu']) {
    const unit = units.find(u => u.title === title); expect(unit).toBeTruthy(); const detail = await get<{ lessons: { id: number; title: string; questionCounts: unknown }[] }>(request, '/api/units/' + unit!.id);
    const expectedTitles = curriculum.lessons.filter(l => l.unitSeedKey.endsWith(title.startsWith('Sinh') ? 'daily-routines' : title.startsWith('Ăn') ? 'food-shopping' : 'travel-directions')).map(l => l.data.title);
    for (const title of expectedTitles) { const lesson = detail.lessons.find(l => l.title === title); expect(lesson?.questionCounts).toEqual({ guided: 5, independent: 5, reviewVariant: 5 }); const intro = await request.get('/api/lessons/' + lesson!.id); expect(intro.status()).toBe(200); expect(await intro.text()).not.toMatch(/answerKey|correctOptionId|transcript|materialVersionId/); }
  }
});

test('all seven real media files match the manifest and support byte ranges', async ({ request }) => {
  await login(request); const bank = await pages<{ id: number; title: string }>(request, '/api/author/materials?scope=OWN'); let files = 0;
  for (const spec of curriculum.materials.filter(m => m.file)) {
    const row = bank.find(r => r.title === spec.data.title); expect(row).toBeTruthy(); const root = await get<MaterialRoot>(request, '/api/author/materials/' + row!.id); const asset = root.published!.assetId!;
    const response = await request.get('/api/assets/' + asset); expect(response.status()).toBe(200); const bytes = await response.body(); expect(createHash('sha256').update(bytes).digest('hex')).toBe(spec.sha256);
    const range = await request.get('/api/assets/' + asset, { headers: { Range: 'bytes=0-15' } }); expect(range.status()).toBe(206); expect(await range.body()).toEqual(bytes.subarray(0, 16)); files++;
  }
  expect(files).toBe(7);
});

test('learner can browse three Units with two lessons each on desktop and mobile', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(local.DEMO_STUDENT_EMAIL ?? 'student@english.local'); await page.getByLabel('Mật khẩu', { exact: true }).fill(local.DEMO_STUDENT_PASSWORD ?? 'Student@12345'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.getByRole('button', { name: 'Khám phá bài học', exact: true }).click();
  await page.getByLabel('Tìm Unit', { exact: true }).fill('Bắt đầu');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.locator('.catalog-card')).toHaveCount(3);
  await expect(page.getByRole('heading', { name: 'Đi lại và chỉ đường — Bắt đầu', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/module-2-complete-units-desktop.png', fullPage: true });
  for (const unit of ['Sinh hoạt hằng ngày — Bắt đầu', 'Ăn uống và mua sắm — Bắt đầu', 'Đi lại và chỉ đường — Bắt đầu']) {
    await page.getByRole('button', { name: 'Xem bài trong ' + unit + ' →', exact: true }).click();
    await expect(page.getByText('5 câu hướng dẫn · 5 câu tự làm', { exact: true })).toHaveCount(2);
    if (unit.startsWith('Ăn')) { await page.setViewportSize({ width: 390, height: 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false); await page.screenshot({ path: 'test-results/module-2-complete-food-mobile.png', fullPage: true }); }
    await page.getByRole('button', { name: 'Về các Unit', exact: true }).click();
  }
});
