import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { CatalogPage, Topic, Unit, Objective } from './catalog';
import type { Instruction } from './authoring';
import { allPages } from './authoring';
import { Instructions } from './AuthorPreview';
import './materials.css';
import './authoring.css';
interface LessonSummary { id: number; title: string; description: string; objectiveIds: number[]; questionCounts: { guided: number; independent: number; reviewVariant: number } }
interface Detail { unit: Unit; lessons: LessonSummary[] }
interface Introduction { lesson: LessonSummary; instructions: Instruction[]; objectives: Objective[] }
export default function LearningCatalog() {
  const [topics, setTopics] = useState<Topic[]>([]), [topic, setTopic] = useState(''), [page, setPage] = useState(0), [query, setQuery] = useState(''), [search, setSearch] = useState('');
  const [unitId] = useRouteParam('unit'), [lessonId] = useRouteParam('lesson');
  const [units, setUnits] = useState<CatalogPage<Unit> | null>(null), [loadedDetail, setDetail] = useState<Detail | null>(null), [loadedIntro, setIntro] = useState<Introduction | null>(null);
  const detail = unitId && loadedDetail?.unit.id === Number(unitId) ? loadedDetail : null;
  const intro = lessonId && loadedIntro?.lesson.id === Number(lessonId) ? loadedIntro : null;
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => { let active = true; void allPages<Topic>('/api/topics').then(rows => { if (active) setTopics(rows); }).catch(f => { if (active) setError(errorMessage(f)); }); return () => { active = false; }; }, []);
  useEffect(() => { let active = true; setBusy(true); setError(''); const params = new URLSearchParams({ q: search, page: String(page), size: '9' }); if (topic) params.set('topicId', topic); void api<CatalogPage<Unit>>('/api/units?' + params).then(rows => { if (active) setUnits(rows); }).catch(f => { if (active) setError(errorMessage(f)); }).finally(() => { if (active) setBusy(false); }); return () => { active = false; }; }, [topic, page, search, refresh]);
  useEffect(() => {
    if (!unitId && !lessonId) return;
    let active = true; setError('');
    void Promise.all([
      unitId ? api<Detail>('/api/units/' + encodeURIComponent(unitId)) : Promise.resolve(null),
      lessonId ? api<Introduction>('/api/lessons/' + encodeURIComponent(lessonId)) : Promise.resolve(null),
    ]).then(([unit, lesson]) => { if (active) { setDetail(unit); setIntro(lesson); } })
      .catch(failure => { if (active) setError(errorMessage(failure)); });
    return () => { active = false; };
  }, [unitId, lessonId, refresh]);
  function open(kind: 'units' | 'lessons', id: number) { navigate(kind === 'units' ? { unit: String(id), lesson: null } : { lesson: String(id) }); }
  if ((unitId && !detail) || (lessonId && !intro)) return <section className="panel material-editor">{error ? <div role="alert" className="message error">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div> : <p role="status">Đang mở bài học…</p>}<button className="button secondary" onClick={() => returnTo(lessonId ? { lesson: null } : { unit: null, lesson: null })}>Quay lại danh sách</button></section>;
  return <section><span className="eyebrow">KHÁM PHÁ NỘI DUNG</span><h1>{intro ? intro.lesson.title : detail ? detail.unit.title : 'Bài học tiếng Anh'}<span>.</span></h1>{error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div>}
    {intro ? <div className="panel material-editor"><p className="muted">{intro.lesson.description}</p><h2>Bạn sẽ học gì?</h2><ul>{intro.objectives.map(o => <li key={o.id}>{o.name}</li>)}</ul><Instructions lesson={intro} /><div className="message warning">Chức năng bắt đầu làm bài đang được hoàn thiện. Bạn có thể xem nội dung giới thiệu và mục tiêu của bài trước.</div><button className="button secondary" onClick={() => returnTo({ lesson: null })}>Về danh sách bài trong Unit</button></div> : detail ? <><p className="muted">{detail.unit.description}</p><div className="catalog-grid">{detail.lessons.map(l => <article className="panel catalog-card" key={l.id}><span className="eyebrow">BÀI HỌC</span><h2>{l.title}</h2><p className="muted">{l.description}</p><p className="muted">{l.questionCounts.guided} câu hướng dẫn · {l.questionCounts.independent} câu tự làm</p><button className="link-button" disabled={busy} onClick={() => void open('lessons', l.id)}>Xem giới thiệu {l.title} →</button></article>)}</div><div className="material-actions"><button className="button secondary" onClick={() => returnTo({ unit: null, lesson: null })}>Về các Unit</button></div></> : <><p className="muted">Chọn chủ đề và xem những bài học đã được công bố.</p><div className="panel catalog-browser"><form className="filters" onSubmit={e => { e.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(v => v + 1); }}><label className="search-label">Tìm Unit<input value={query} maxLength={100} onChange={e => setQuery(e.target.value)} /></label><label>Chủ đề<select value={topic} onChange={e => { setTopic(e.target.value); setPage(0); }}><option value="">Tất cả chủ đề</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label><button className="button secondary" disabled={busy}>Tìm kiếm</button></form>{busy ? <p className="table-message" role="status">Đang tải bài học…</p> : !units?.items.length ? <div className="catalog-empty"><strong>Chưa có Unit phù hợp</strong><p className="muted">Nội dung đã xuất bản sẽ xuất hiện tại đây.</p></div> : <div className="catalog-grid">{units.items.map(u => <article className="catalog-card" key={u.id}><span className="eyebrow">CHẶNG {u.stage}</span><h2>{u.title}</h2><p className="muted">{u.description}</p><button className="link-button" disabled={busy} onClick={() => void open('units', u.id)}>Xem bài trong {u.title} →</button></article>)}</div>}<div className="pagination"><span>{units?.totalElements ?? 0} Unit</span><div><button className="button secondary compact" disabled={busy || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={busy || page + 1 >= (units?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button></div></div></div></>}
  </section>;
}
