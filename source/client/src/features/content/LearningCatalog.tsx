import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { CatalogPage, Topic, Unit } from './catalog';
import { allPages } from './authoring';
import LessonIntroduction from '../learning/LessonIntroduction';
import type { Introduction } from '../learning/LessonIntroduction';
import type { Progress } from '../learning/types';
import '../content/materials.css';
import '../content/authoring.css';
interface LessonSummary { id: number; title: string; description: string; objectiveIds: number[]; questionCounts: { guided: number; independent: number; reviewVariant: number } }
interface Detail { unit: Unit; lessons: LessonSummary[] }
export default function LessonCatalog({ student, start, disabled }: { student: boolean; start: (id: number) => void; disabled: boolean }) {
  const [topics, setTopics] = useState<Topic[]>([]), [topic, setTopic] = useState(''), [page, setPage] = useState(0), [query, setQuery] = useState(''), [search, setSearch] = useState('');
  const [unitId] = useRouteParam('unit'), [lessonId] = useRouteParam('lesson');
  const [units, setUnits] = useState<CatalogPage<Unit> | null>(null), [loadedDetail, setDetail] = useState<Detail | null>(null), [loadedIntro, setIntro] = useState<Introduction | null>(null), [progress, setProgress] = useState<Progress[]>([]);
  const detail = unitId && loadedDetail?.unit.id === Number(unitId) ? loadedDetail : null;
  const intro = lessonId && loadedIntro?.lesson.id === Number(lessonId) ? loadedIntro : null;
  const [progressReady, setProgressReady] = useState(false);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => { let active = true; void allPages<Topic>('/api/topics').then(rows => { if (active) setTopics(rows); }).catch(f => { if (active) setError(errorMessage(f)); }); return () => { active = false; }; }, []);
  useEffect(() => { let active = true; setBusy(true); setError(''); const params = new URLSearchParams({ q: search, page: String(page), size: '9' }); if (topic) params.set('topicId', topic); void api<CatalogPage<Unit>>('/api/units?' + params).then(rows => { if (active) setUnits(rows); }).catch(f => { if (active) setError(errorMessage(f)); }).finally(() => { if (active) setBusy(false); }); return () => { active = false; }; }, [topic, page, search, refresh]);
  useEffect(() => {
    if (!unitId && !lessonId) return;
    let active = true; setError('');
    void Promise.all([unitId ? api<Detail>('/api/units/' + encodeURIComponent(unitId)) : Promise.resolve(null), lessonId ? api<Introduction>('/api/lessons/' + encodeURIComponent(lessonId)) : Promise.resolve(null)])
      .then(([unit, lesson]) => { if (active) { setDetail(unit); setIntro(lesson); } }).catch(f => { if (active) setError(errorMessage(f)); });
    return () => { active = false; };
  }, [unitId, lessonId, refresh]);
  useEffect(() => {
    if (!student || (!unitId && !lessonId)) { setProgress([]); setProgressReady(false); return; }
    let active = true; setProgressReady(false);
    void allPages<Progress>('/api/me/learning-progress' + (unitId ? '?unitId=' + encodeURIComponent(unitId) : '')).then(rows => { if (active) { setProgress(rows); setProgressReady(true); } }).catch(f => { if (active) setError(errorMessage(f)); });
    return () => { active = false; };
  }, [student, unitId, lessonId, refresh]);
  function progressLabel(id: number) {
    const p = progress.find(row => row.lessonId === id); if (!p) return null;
    return <p className="lesson-progress">{p.state === 'COMPLETED' ? 'Đã hoàn thành bài học' : p.state === 'STARTED' ? 'Đã bắt đầu · Chưa hoàn thành' : 'Chưa bắt đầu'}{p.updateAvailable && <small>Có phiên bản bài học mới</small>}</p>;
  }
  if ((unitId && !detail) || (lessonId && !intro)) return <section className="panel material-editor">{error ? <div role="alert" className="message error">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div> : <p role="status">Đang mở bài học…</p>}<button className="button secondary" onClick={() => returnTo(lessonId ? { lesson: null } : { unit: null, lesson: null })}>Quay lại danh sách</button></section>;
  return <section className={intro ? "lesson-intro-page" : undefined}><span className="eyebrow">KHÁM PHÁ NỘI DUNG</span><h1>{intro ? intro.lesson.title : detail ? detail.unit.title : 'Bài học tiếng Anh'}<span>.</span></h1>{error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div>}
    {intro ? <LessonIntroduction key={intro.lesson.id} intro={intro} progress={progress.find(p => p.lessonId === intro.lesson.id)} progressReady={progressReady} student={student} disabled={disabled} start={start} retryProgress={() => setRefresh(v => v + 1)} /> : detail ? <><p className="muted">{detail.unit.description}</p><div className="catalog-grid">{detail.lessons.map(l => <article className="panel catalog-card" key={l.id}><span className="eyebrow">BÀI HỌC</span><h2>{l.title}</h2><p className="muted">{l.description}</p><p className="muted">{l.questionCounts.guided} câu hướng dẫn · {l.questionCounts.independent} câu tự làm</p>{progressLabel(l.id)}<button className="link-button" disabled={busy} onClick={() => navigate({ lesson: String(l.id) })}>Xem giới thiệu {l.title} →</button></article>)}</div><div className="material-actions"><button className="button secondary" onClick={() => returnTo({ unit: null, lesson: null })}>Về các Unit</button></div></> : <><p className="muted">Chọn chủ đề và xem những bài học đã được công bố.</p><div className="panel catalog-browser"><form className="filters" onSubmit={e => { e.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(v => v + 1); }}><label className="search-label">Tìm Unit<input value={query} maxLength={100} onChange={e => setQuery(e.target.value)} /></label><label>Chủ đề<select value={topic} onChange={e => { setTopic(e.target.value); setPage(0); }}><option value="">Tất cả chủ đề</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label><button className="button secondary" disabled={busy}>Tìm kiếm</button></form>{busy ? <p className="table-message" role="status">Đang tải bài học…</p> : !units?.items.length ? <div className="catalog-empty"><strong>Chưa có Unit phù hợp</strong><p className="muted">Nội dung đã xuất bản sẽ xuất hiện tại đây.</p></div> : <div className="catalog-grid">{units.items.map(u => <article className="catalog-card" key={u.id}><span className="eyebrow">CHẶNG {u.stage}</span><h2>{u.title}</h2><p className="muted">{u.description}</p><button className="link-button" disabled={busy} onClick={() => navigate({ unit: String(u.id), lesson: null })}>Xem bài trong {u.title} →</button></article>)}</div>}<div className="pagination"><span>{units?.totalElements ?? 0} Unit</span><div><button className="button secondary compact" disabled={busy || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={busy || page + 1 >= (units?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button></div></div></div></>}
  </section>;
}
