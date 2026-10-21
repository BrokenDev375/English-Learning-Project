import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage } from '../../api';
import { navigate, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import type { CatalogPage, Topic } from '../content/catalog';
import { allPages } from '../content/authoring';
import LearningCatalog from '../content/LearningCatalog';
import { createAttempt, createCommand, listAttempts } from './api';
import type { Create, Summary } from './types';
import { modeNames } from './types';
import AttemptRunner from './AttemptRunner';
import PersonalReview from './PersonalReview';
import { activateLearningUser, getPendingCreate, setPendingCreate } from './session';
import './learning.css';

export default function LearningWorkspace({ user }: { user: User }) {
  activateLearningUser(user.id);
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const [attemptId] = useRouteParam('attempt'), [tab, setTab] = useRouteParam('tab', 'catalog');
  const [command, setCommand] = useState<ReturnType<typeof createCommand> | null>(getPendingCreate), [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function start(input: Create | null) {
    if (busy || user.role !== 'STUDENT') return;
    const body = command ?? (input && createCommand(input)); if (!body) return;
    setCommand(body); setPendingCreate(body); setBusy(true); setError('');
    try { const s = await createAttempt(body); if (getPendingCreate() === body) setPendingCreate(null); if (active.current) { setCommand(null); navigate({ view: 'learning', attempt: String(s.attemptId) }); } }
    catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) { setCommand(null); if (getPendingCreate() === body) setPendingCreate(null); }
      if (active.current && e instanceof ApiError && e.status === 401) window.dispatchEvent(new Event('session-expired'));
    } finally { setBusy(false); }
  }
  if (attemptId && user.role === 'STUDENT') {
    const id = Number(attemptId);
    return Number.isSafeInteger(id) && id > 0 ? <AttemptRunner key={user.id + ':' + id} id={id} userId={user.id} /> : <div className="message error" role="alert">Mã lượt làm không hợp lệ.<button className="link-button" onClick={() => navigate({ attempt: null })}>Về danh sách</button></div>;
  }
  return <>
    {user.role === 'STUDENT' && <div className="learning-tabs" role="tablist" aria-label="Học và luyện tập">{[['catalog', 'Khám phá bài học'], ['practice', 'Luyện tập theo chủ đề'], ['review', 'Ôn tập cá nhân'], ['history', 'Lượt làm của tôi']].map(([key, label]) => <button role="tab" key={key} aria-selected={tab === key} onClick={() => { if (!command) { setError(''); setTab(key); } }}>{label}</button>)}</div>}
    {error && <div className="message error" role="alert">{error}</div>}
    {command && <div className="message warning" role="status">{busy ? 'Đang tạo hoặc tiếp tục lượt làm…' : 'Chưa nhận được xác nhận tạo lượt. Gửi lại đúng thao tác trước khi chọn lượt mới.'}{!busy && <button className="button secondary" onClick={() => void start(null)}>Gửi lại yêu cầu tạo lượt</button>}</div>}
    {user.role !== 'STUDENT' || tab === 'catalog' ? <LearningCatalog student={user.role === 'STUDENT'} start={lessonId => void start({ mode: 'LEARN', lessonId })} disabled={busy || !!command} /> : tab === 'practice' ? <PracticeForm start={input => void start(input)} disabled={busy || !!command} /> : tab === 'review' ? <PersonalReview start={input => void start(input)} disabled={busy || !!command} /> : <AttemptHistory />}
  </>;
}
function PracticeForm({ start, disabled }: { start: (c: Create) => void; disabled: boolean }) {
  const [topics, setTopics] = useState<Topic[]>([]), [topic, setTopic] = useState(''), [difficulty, setDifficulty] = useState('EASY'), [count, setCount] = useState(10), [error, setError] = useState(''), [refresh, setRefresh] = useState(0);
  useEffect(() => { let active = true; setError(''); void allPages<Topic>('/api/topics').then(rows => { if (active) { setTopics(rows); setTopic(rows[0] ? String(rows[0].id) : ''); } }).catch(e => { if (active) setError(errorMessage(e)); }); return () => { active = false; }; }, [refresh]);
  return <section><span className="eyebrow">TỰ CHỌN MỘT THỬ THÁCH NHỎ</span><h1>Luyện tập theo chủ đề<span>.</span></h1><p className="muted">Luyện các câu tự làm. Nếu chưa đủ câu phù hợp, bạn sẽ nhận số câu hiện có. Mở Ôn tập cá nhân để xem nội dung cần luyện từ kết quả của bạn.</p>{error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Tải lại chủ đề</button></div>}<form className="panel learning-panel practice-form" onSubmit={e => { e.preventDefault(); if (topic) start({ mode: 'PRACTICE', topicId: Number(topic), difficulty, count }); }}>
    <label>Chủ đề luyện tập<select required value={topic} disabled={disabled} onChange={e => setTopic(e.target.value)}><option value="">Chọn chủ đề</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
    <label>Độ khó<select disabled={disabled} value={difficulty} onChange={e => setDifficulty(e.target.value)}><option value="EASY">Dễ</option><option value="MEDIUM">Vừa</option><option value="HARD">Khó</option></select></label>
    <label>Số câu<input disabled={disabled} type="number" min={1} max={10} required value={count} onChange={e => setCount(Number(e.target.value))} /></label>
    <button className="button primary" disabled={disabled || !topic}>Bắt đầu hoặc tiếp tục luyện tập</button></form></section>;
}
function AttemptHistory() {
  const [page, setPage] = useState(0), [mode, setMode] = useState(''), [state, setState] = useState(''), [rows, setRows] = useState<CatalogPage<Summary> | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => { let active = true; setBusy(true); setError(''); const p = new URLSearchParams({ page: String(page), size: '10' }); if (mode) p.set('mode', mode); if (state) p.set('state', state); void listAttempts(p).then(r => { if (active) setRows(r); }).catch(e => { if (active) setError(errorMessage(e)); }).finally(() => { if (active) setBusy(false); }); return () => { active = false; }; }, [page, mode, state, refresh]);
  return <section><span className="eyebrow">TIẾP TỤC TỪ NƠI BẠN DỪNG</span><h1>Lượt làm của tôi<span>.</span></h1><div className="panel"><div className="filters"><label>Chế độ<select value={mode} onChange={e => { setMode(e.target.value); setPage(0); }}><option value="">Tất cả</option>{Object.entries(modeNames).map(([v, text]) => <option key={v} value={v}>{text}</option>)}</select></label><label>Trạng thái lượt<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="">Tất cả</option><option value="IN_PROGRESS">Đang làm</option><option value="SUBMITTED">Đã nộp</option></select></label></div>
    {error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Tải lại lượt làm</button></div>}
    {busy ? <p className="table-message" role="status">Đang tải các lượt làm…</p> : !rows?.items.length ? <p className="table-message">Chưa có lượt phù hợp. Chọn một bài học hoặc chủ đề để bắt đầu.</p> : <div className="table-wrap"><table><thead><tr><th>Lượt</th><th>Chế độ</th><th>Đã trả lời</th><th>Cập nhật</th><th>Thao tác</th></tr></thead><tbody>{rows.items.map(r => <tr key={r.attemptId}><td>#{r.attemptId}</td><td>{modeNames[r.mode]}</td><td>{r.answeredCount}/{r.totalQuestions}</td><td>{new Date(r.updatedAt).toLocaleString('vi-VN')}</td><td><button className="link-button" onClick={() => navigate({ attempt: String(r.attemptId) })}>{r.state === 'SUBMITTED' ? 'Xem kết quả' : 'Tiếp tục làm'} #{r.attemptId}</button></td></tr>)}</tbody></table></div>}
    <div className="pagination"><span>{rows?.totalElements ?? 0} lượt làm</span><div><button className="button secondary" disabled={busy || !page} onClick={() => setPage(v => v - 1)}>Trang trước</button><button className="button secondary" disabled={busy || page + 1 >= (rows?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Trang sau</button></div></div></div></section>;
}
