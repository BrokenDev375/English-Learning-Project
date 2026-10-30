import { api } from '../../api';
import type { CatalogPage, Unit } from '../content/catalog';
export interface RoundProgress {
  unit: Unit; totalLessons: number; completedLessons: number; startedLessons: number;
  state: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'; nextLessonId: number | null;
  number: number; locked: boolean; prerequisiteUnitId: number | null; prerequisiteRoundNumber: number | null; prerequisiteTitle: string | null;
  writing?: { required: boolean; submitted: boolean; state: string };
}
export async function recommendedRound(first: CatalogPage<RoundProgress>): Promise<RoundProgress | null> {
  let rows = first;
  for (;;) {
    const next = rows.items.find(r => !r.locked && r.totalLessons > 0 && (r.nextLessonId !== null || (r.writing?.required && !r.writing.submitted)));
    if (next) return next;
    if (rows.page + 1 >= rows.totalPages) return null;
    rows = await api<CatalogPage<RoundProgress>>('/api/me/rounds?size=' + first.size + '&page=' + (rows.page + 1));
  }
}
export function RoundMeter({ totalLessons, completedLessons, writing }: Pick<RoundProgress, 'totalLessons' | 'completedLessons' | 'writing'>) {
  return <div className="round-meter"><span>{totalLessons > 0 ? `Đã hoàn thành ${completedLessons}/${totalLessons} bài` : 'Chưa có bài học công bố'}</span>{totalLessons > 0 && <progress max={totalLessons} value={completedLessons} aria-label="Tiến độ vòng học" />}{writing?.required && <small>{writing.submitted ? 'Đã nộp bài viết cuối vòng' : 'Cần nộp bài viết cuối vòng để mở vòng sau'}</small>}</div>;
}
