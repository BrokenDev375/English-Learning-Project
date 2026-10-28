import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { CatalogPage, Topic, Unit } from './catalog';
import { allPages } from './authoring';
import LessonIntroduction from '../learning/LessonIntroduction';
import type { Introduction } from '../learning/LessonIntroduction';
import type { Progress } from '../learning/types';
import { RoundMeter } from '../learning/journey';
import RoundMap, { RoundRules } from '../learning/RoundMap';
import type { RoundProgress } from '../learning/journey';
import '../content/materials.css';
import '../content/authoring.css';
import { roundHeading } from './roundNumbering';
import '../learning/journey.css';
interface LessonSummary { id: number; title: string; description: string; objectiveIds: number[]; questionCounts: { guided: number; independent: number; reviewVariant: number } }
interface Detail { unit: Unit; lessons: LessonSummary[] }
export default function LessonCatalog({ student, start, disabled }: { student: boolean; start: (id: number) => void; disabled: boolean }) {
  const [topics, setTopics] = useState<Topic[]>([]), [topic, setTopic] = useState(''), [page, setPage] = useState(0), [query, setQuery] = useState(''), [search, setSearch] = useState('');
  const [unitId] = useRouteParam('unit'), [lessonId] = useRouteParam('lesson');
  const [units, setUnits] = useState<CatalogPage<Unit> | null>(null), [rounds, setRounds] = useState<RoundProgress[]>([]);
  const [loadedDetail, setDetail] = useState<Detail | null>(null), [loadedIntro, setIntro] = useState<Introduction | null>(null), [progress, setProgress] = useState<Progress[]>([]);
  const [loadedRound, setRound] = useState<RoundProgress | null>(null);
  const detail = unitId && loadedDetail?.unit.id === Number(unitId) ? loadedDetail : null;
  const intro = lessonId && loadedIntro?.lesson.id === Number(lessonId) ? loadedIntro : null;
  const round = loadedRound && loadedRound.unit.id === Number(unitId || intro?.lesson.unitId) ? loadedRound : null;
  const [progressReady, setProgressReady] = useState(false), [error, setError] = useState(''), [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => { let live = true; void allPages<Topic>('/api/topics').then(rows => { if (live) setTopics(rows); }).catch(e => { if (live) setError(errorMessage(e)); }); return () => { live = false; }; }, []);
  useEffect(() => {
    let live = true; setBusy(true); setError('');
    const params = new URLSearchParams({ q: search, page: String(page), size: '9' }); if (topic) params.set('topicId', topic);
    async function load() {
      if (student) {
        const rows = await api<CatalogPage<RoundProgress>>('/api/me/rounds?' + params);
        if (live) { setRounds(rows.items); setUnits({ ...rows, items: rows.items.map(r => r.unit) }); }
      } else {
        const rows = await api<CatalogPage<Unit>>('/api/units?' + params); if (live) setUnits(rows);
      }
    }
    void load().catch(e => { if (live) setError(errorMessage(e)); }).finally(() => { if (live) setBusy(false); });
    return () => { live = false; };
  }, [student, topic, page, search, refresh]);
  useEffect(() => {
    if (!unitId && !lessonId) return;
    let live = true; setError('');
    void Promise.all([unitId ? api<Detail>('/api/units/' + encodeURIComponent(unitId)) : Promise.resolve(null), lessonId ? api<Introduction>('/api/lessons/' + encodeURIComponent(lessonId)) : Promise.resolve(null)])
      .then(async ([unit, lesson]) => {
        const id = unit?.unit.id ?? lesson?.lesson.unitId;
        const round = student && id ? await api<RoundProgress>('/api/me/rounds/' + id) : null;
        const resolvedUnit = unit ?? (lesson ? await api<Detail>('/api/units/' + lesson.lesson.unitId) : null);
        if (live) { setDetail(resolvedUnit); setIntro(lesson); setRound(round); }
      }).catch(e => { if (live) setError(errorMessage(e)); });
    return () => { live = false; };
  }, [student, unitId, lessonId, refresh]);
  useEffect(() => {
    if (!student || (!unitId && !lessonId)) { setProgress([]); setProgressReady(false); return; }
    let live = true; setProgressReady(false);
    void allPages<Progress>('/api/me/learning-progress' + (unitId ? '?unitId=' + encodeURIComponent(unitId) : '')).then(rows => { if (live) { setProgress(rows); setProgressReady(true); } }).catch(e => { if (live) setError(errorMessage(e)); });
    return () => { live = false; };
  }, [student, unitId, lessonId, refresh]);
  function progressLabel(id: number) {
    if (!student || !progressReady) return null;
    const p = progress.find(row => row.lessonId === id);
    return <p className="lesson-progress">{p?.state === 'COMPLETED' ? 'Đã hoàn thành bài học' : p?.state === 'STARTED' ? 'Đã bắt đầu · Chưa hoàn thành' : 'Chưa bắt đầu'}{p?.updateAvailable && <small>Có phiên bản bài học mới</small>}</p>;
  }
  const completed = detail?.lessons.filter(l => progress.some(p => p.lessonId === l.id && p.state === 'COMPLETED')).length ?? 0;
  const next = detail?.lessons.find(l => !progress.some(p => p.lessonId === l.id && p.state === 'COMPLETED'));
  if ((unitId && !detail) || (lessonId && !intro) || (student && (unitId || lessonId) && !round)) return <section className="panel material-editor">{error ? <div role="alert" className="message error">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div> : <p role="status">Đang mở bài học…</p>}<button className="button secondary" onClick={() => returnTo(lessonId ? { lesson: null } : { unit: null, lesson: null })}>Quay lại danh sách</button></section>;
  if (student && round?.locked) return <section className="locked-round-page">
    <span className="eyebrow">CHƠI THEO VÒNG</span><h1>Vòng {round.number} đang khóa<span>.</span></h1>
    <div className="panel locked-round-panel"><span className="locked-round-icon" aria-hidden="true">🔒</span><h2>{round.unit.title}</h2><p>Hoàn thành tất cả bài trong Vòng {round.prerequisiteRoundNumber} — {round.prerequisiteTitle} để mở vòng này.</p>
      <button className="button primary" onClick={() => navigate({ unit: String(round.prerequisiteUnitId), lesson: null })}>Về vòng đang mở →</button><button className="button secondary" onClick={() => navigate({ unit: null, lesson: null })}>Mở bản đồ vòng chơi</button>
    </div></section>;
  return <section className={intro ? 'lesson-intro-page' : 'round-journey'}>
    <span className="eyebrow">{student && !intro ? 'CHƠI THEO VÒNG' : !student && (detail || intro) ? roundHeading(loadedDetail?.unit.roundNumber).toUpperCase() : 'KHÁM PHÁ NỘI DUNG'}</span><h1>{intro ? intro.lesson.title : detail ? student && round ? 'Vòng ' + round.number + ' · ' + detail.unit.title : detail.unit.title : student ? 'Bản đồ vòng chơi' : 'Bài học tiếng Anh'}<span>.</span></h1>
    {error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Thử lại</button></div>}
    {intro ? <LessonIntroduction key={intro.lesson.id} intro={intro} progress={progress.find(p => p.lessonId === intro.lesson.id)} progressReady={progressReady} student={student} disabled={disabled} start={start} retryProgress={() => setRefresh(v => v + 1)} /> : detail ? <>
      <p className="muted">{detail.unit.description}</p>
      {student && <article className="panel round-overview"><h2>Tiến độ vòng chơi</h2>{progressReady ? <><RoundMeter totalLessons={detail.lessons.length} completedLessons={completed} /><p>{detail.lessons.length > 0 && completed === detail.lessons.length ? 'Bạn đã hoàn thành tất cả bài trong vòng này. Bạn có thể học lại từng bài.' : 'Chọn một bài bên dưới để chơi. Hoàn thành tất cả bài trong vòng sẽ mở khóa vòng kế tiếp.'}</p>{next && <button className="button primary" disabled={disabled} onClick={() => navigate({ lesson: String(next.id) })}>Chơi bài tiếp theo →</button>}</> : <p role="status">{error ? 'Chưa tải được tiến độ vòng học.' : 'Đang tải tiến độ vòng học…'}</p>}</article>}
      <div className="catalog-grid">{detail.lessons.map((l, index) => <article className="panel catalog-card" key={l.id}><span className="eyebrow">BÀI {index + 1}</span><h2>{l.title}</h2><p className="muted">{l.description}</p><p className="muted">{l.questionCounts.guided} câu hướng dẫn · {l.questionCounts.independent} câu tự làm</p>{progressLabel(l.id)}<button className="link-button" disabled={busy} onClick={() => navigate({ lesson: String(l.id) })}>{student ? 'Chơi bài ' + (index + 1) + ' · ' : 'Xem giới thiệu '}{l.title} →</button></article>)}</div>
      <div className="material-actions"><button className="button secondary" onClick={() => returnTo({ unit: null, lesson: null })}>{student ? 'Về bản đồ vòng chơi' : 'Về các vòng đấu'}</button></div>
    </> : <>
      <p className="muted">{student ? 'Bắt đầu ở vòng đang mở. Làm đủ các bài để mở khóa vòng tiếp theo; vòng đã hoàn thành luôn có thể chơi lại.' : 'Chọn chủ đề và xem những bài học đã được công bố.'}</p>
      {student && <RoundRules />}
      <div className="panel catalog-browser"><form className="filters" onSubmit={event => { event.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(v => v + 1); }}><label className="search-label">{student ? 'Tìm vòng học' : 'Tìm vòng đấu'}<input value={query} maxLength={100} onChange={event => setQuery(event.target.value)} /></label><label>Chủ đề<select value={topic} onChange={event => { setTopic(event.target.value); setPage(0); }}><option value="">Tất cả chủ đề</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label><button className="button secondary" disabled={busy}>Tìm kiếm</button></form>
        {busy ? <p className="table-message" role="status">Đang tải hành trình…</p> : !units?.items.length ? <div className="catalog-empty"><strong>{student ? 'Chưa có vòng chơi phù hợp' : 'Chưa có vòng đấu phù hợp'}</strong><p className="muted">Nội dung đã xuất bản sẽ xuất hiện tại đây.</p></div> : student ? <RoundMap rounds={rounds} disabled={busy || disabled} open={r => navigate({ unit: String(r.unit.id), lesson: null })} /> : <div className="catalog-grid">{units.items.map(u => {
          const round = rounds.find(r => r.unit.id === u.id);
          return <article className="catalog-card round-card" key={u.id}><span className="eyebrow">{student ? 'VÒNG HỌC' : roundHeading(u.roundNumber).toUpperCase()}</span><h2>{u.title}</h2><p className="muted">{u.description}</p>{student && round && <><span className="round-state">{round.state === 'COMPLETED' ? 'Đã hoàn thành vòng' : round.state === 'IN_PROGRESS' ? 'Đang học' : 'Sẵn sàng bắt đầu'}</span><RoundMeter {...round} /></>}<button className="link-button" disabled={busy} onClick={() => navigate({ unit: String(u.id), lesson: null })}>Xem bài trong {u.title} →</button></article>;
        })}</div>}
        <div className="pagination"><span>{units?.totalElements ?? 0} {student ? 'vòng học' : 'vòng đấu'}</span><div><button className="button secondary compact" disabled={busy || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={busy || page + 1 >= (units?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button></div></div>
      </div>
    </>}
  </section>;
}
