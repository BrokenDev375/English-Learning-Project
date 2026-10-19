import { api, ApiError } from '../../api';
import type { CatalogPage, Objective, Topic, Unit } from './catalog';
import type { MaterialContent, Source } from './materials';
export type BankKind = 'questions' | 'lessons';
export const questionTypes = { SINGLE_CHOICE: 'Chọn một đáp án', FILL_BLANK: 'Điền chỗ trống', MATCHING: 'Ghép cặp' };
export const practiceRoles = { GUIDED: 'Có hướng dẫn', INDEPENDENT: 'Tự làm', REVIEW_VARIANT: 'Biến thể ôn tập' };
export const difficulties = { EASY: 'Dễ', MEDIUM: 'Vừa', HARD: 'Khó' };
export interface Option { id: string; text: string }
export type Part = { kind: 'TEXT'; text: string } | { kind: 'BLANK'; blankId: string };
export interface Blank { blankId: string; acceptedAnswers: string[] }
export interface Pair { leftId: string; rightId: string }
export interface QuestionData { type: keyof typeof questionTypes; topicId: number; objectiveId: number; difficulty: keyof typeof difficulties; prompt: string; explanation: string; hint: string | null; materialVersionId: number | null; source: Source; content: { options?: Option[]; parts?: Part[]; leftItems?: Option[]; rightItems?: Option[] }; answerKey: { correctOptionId?: string | null; blanks?: Blank[]; caseSensitive?: boolean; normalizeWhitespace?: boolean; pairs?: Pair[] } }
export type Instruction = { kind: 'TEXT'; text: string } | { kind: 'EXAMPLE'; text: string; translation: string | null };
export interface Slot { questionVersionId: number; practiceRole: keyof typeof practiceRoles; position: number }
export interface LessonData { title: string; description: string; instructions: Instruction[]; objectiveIds: number[]; questions: Slot[] }
export interface Stamp { id: number; versionNo: number; revision: number; state: string; schemaVersion: 1 }
export type QuestionVersion = QuestionData & Stamp & { questionId: number };
export type LessonVersion = LessonData & Stamp & { lessonId: number };
export type Version = QuestionVersion | LessonVersion;
export interface ContentRoot { id: number; ownerId: number; rootRevision: number; status: string; draftVersionId: number | null; publishedVersionId: number | null; originKey?: string; unitId?: number; sortOrder?: number; draft: Version | null; published: Version | null }
export interface Summary { id: number; ownerId: number; rootRevision: number; status: string; draftVersionId: number | null; publishedVersionId: number | null; title?: string; promptExcerpt?: string; type?: keyof typeof questionTypes; difficulty?: keyof typeof difficulties; unitId?: number; sortOrder?: number }
export type MaterialVersion = MaterialContent & Stamp & { materialId: number };
export interface LessonPreviewData { lesson: LessonVersion; questions: QuestionVersion[]; materials: MaterialVersion[] }
export interface ContentPreview { resourceId: number; draftVersionId: number; baseRevision: number; previewToken: string; errors: { path: string; code: string; message: string }[]; warnings: string[]; data: QuestionVersion | LessonPreviewData }
export interface Choices { topics: Topic[]; objectives: Objective[]; units: Unit[]; materials: MaterialVersion[]; materialRoots: number[]; questions: QuestionVersion[]; questionRoots: number[] }
export async function allPages<T>(path: string): Promise<T[]> { const rows: T[] = []; let page = 0; for (;;) { const result = await api<CatalogPage<T>>(path + (path.includes('?') ? '&' : '?') + 'size=50&page=' + page++); rows.push(...result.items); if (page >= result.totalPages) return rows; } }
async function bankVersions<T extends Stamp>(kind: string, admin: boolean): Promise<{ versions: T[]; roots: number[] }> {
  const scopes = admin ? ['ALL', 'PUBLISHED'] : ['OWN', 'PUBLISHED'];
  const summaries = (await Promise.all(scopes.map(scope => allPages<Summary>('/api/author/' + kind + '?scope=' + scope)))).flat();
  const ids = [...new Set(summaries.map(row => row.id))]; const versions: T[] = [];
  // Keep both current published and editable draft versions available to the author.
  for (let i = 0; i < ids.length; i += 8) { const roots = await Promise.all(ids.slice(i, i + 8).map(id => api<{ draft: T | null; published: T | null }>('/api/author/' + kind + '/' + id))); roots.forEach(root => { if (root.draft) versions.push(root.draft); if (root.published) versions.push(root.published); }); }
  return { versions, roots: ids };
}
export async function loadChoices(admin: boolean, lessons: boolean): Promise<Choices> {
  const [topics, objectives, units, materials, questions] = await Promise.all([
    allPages<Topic>('/api/author/topics?scope=ALL'), allPages<Objective>('/api/author/objectives?scope=ALL'), allPages<Unit>('/api/author/units?scope=' + (admin ? 'ALL' : 'OWN')),
    bankVersions<MaterialVersion>('materials', admin), lessons ? bankVersions<QuestionVersion>('questions', admin) : Promise.resolve({ versions: [] as QuestionVersion[], roots: [] as number[] }),
  ]);
  return { topics, objectives, units, materials: materials.versions, materialRoots: materials.roots, questions: questions.versions, questionRoots: questions.roots };
}
// Existing lesson slots may use older immutable versions, which are absent from bank summaries.
export async function historicalVersion<T>(kind: string, id: number, roots: number[]): Promise<T> {
  for (const root of roots) { try { return await api<T>('/api/author/' + kind + '/' + root + '/versions/' + id); } catch (error) { if (!(error instanceof ApiError && error.status === 404)) throw error; } }
  throw new Error('Không tìm thấy phiên bản nội dung được phép xem.');
}
export function newQuestion(choices: Choices): QuestionData { const topic = choices.topics[0]?.id ?? 0; return { type: 'SINGLE_CHOICE', topicId: topic, objectiveId: choices.objectives.find(o => o.topicId === topic)?.id ?? 0, difficulty: 'EASY', prompt: '', explanation: '', hint: null, materialVersionId: null, source: { label: 'Tự biên soạn', url: null, license: null }, content: { options: [{ id: 'opt-a', text: '' }, { id: 'opt-b', text: '' }] }, answerKey: { correctOptionId: null } }; }
export const newLesson = (): LessonData => ({ title: '', description: '', instructions: [{ kind: 'TEXT', text: '' }], objectiveIds: [], questions: [] });
export function payload(version: Version | QuestionData | LessonData) { if ('type' in version) { const { type, topicId, objectiveId, difficulty, prompt, explanation, hint, materialVersionId, source, content, answerKey } = version; return { type, topicId, objectiveId, difficulty, prompt, explanation, hint, materialVersionId, source, content, answerKey }; } const { title, description, instructions, objectiveIds, questions } = version; return { title, description, instructions, objectiveIds, questions }; }
