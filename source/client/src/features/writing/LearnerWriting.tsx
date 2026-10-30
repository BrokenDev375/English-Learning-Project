import { useEffect, useState, useSyncExternalStore } from 'react';
import { writingSession } from './session';
import { wordCount, writingStates } from './types';
import type { Grade, Definition } from './types';
import './writing.css';

export function WritingRequirements({ definition }: { definition: Definition }) {
  return <div className="writing-requirements"><h3>{definition.title}</h3><p className="writing-text">{definition.prompt}</p><p className="muted">Gợi ý {definition.minWords}–{definition.maxWords} từ · Thang điểm 10</p><ul>{definition.criteria.map(c => <li key={c.id}>{c.text} <strong>({c.points} điểm)</strong></li>)}</ul></div>;
}
export function WritingGrade({ grade, definition }: { grade: Grade; definition: Definition }) {
  return <div className="writing-grade"><h4>Kết quả: {grade.earnedPoints}/{grade.totalPoints} điểm</h4><ul>{definition.criteria.map(c => { const mark = grade.marks.find(m => m.criterionId === c.id); return <li key={c.id}><span>{mark?.met ? '☑ Đạt' : '☐ Chưa đạt'} · {c.text} · {mark?.met ? c.points : 0}/{c.points} điểm</span>{mark?.comment && <p className="writing-text">{mark.comment}</p>}</li>; })}</ul><p className="writing-text">{grade.comment || 'Giáo viên chưa ghi nhận xét thêm.'}</p>{grade.allowRevision && <p>Giáo viên cho phép sửa và nộp lại. Bản nộp và điểm cũ được giữ trong lịch sử.</p>}</div>;
}
export default function LearnerWriting({ userId, unitId, changed }: { userId: number; unitId: number; changed: () => void }) {
  const session = writingSession(userId, unitId), state = useSyncExternalStore(session.subscribe, session.getSnapshot);
  const [confirm, setConfirm] = useState(false);
  useEffect(() => { void session.load(); }, [session]);
  const { view, text, busy, pending, conflict, recovery, error } = state;
  const editable = !!view?.work && ['DRAFT', 'RETURNED'].includes(view.work.state);
  async function submit() { setConfirm(false); await session.send('submit'); changed(); }
  return <article className="panel writing-panel" aria-label="Bài viết cuối vòng"><div className="writing-heading"><div><span className="eyebrow">THỬ THÁCH CUỐI VÒNG</span><h2>Bài viết cuối vòng</h2></div><button className="button secondary" disabled={busy || !!pending} onClick={() => { void session.load(); changed(); }}>Cập nhật bài viết</button></div>
    {error && <div className="message error" role="alert">{error}</div>}
    {!view ? <p role="status">{busy ? 'Đang tải bài viết…' : 'Chưa tải được bài viết.'}</p> : !view.available ? <p className="muted">{view.blockReason}</p> : <>
      {view.version && <WritingRequirements definition={view.version.definition} />}
      {!view.unlocked && <p role="status">{view.blockReason}</p>}
      {recovery && <div className="writing-conflict" role="alert"><h3>Có bản viết chưa đồng bộ tại máy</h3><p className="writing-text">{recovery.text}</p><button className="button primary" onClick={() => void session.recover(true)}>Khôi phục bản tại máy</button><button className="button secondary" disabled={!!recovery.pending} onClick={() => void session.recover(false)}>Dùng bản máy chủ</button></div>}
      {conflict && <div className="writing-conflict" role="alert"><h3>Bản máy chủ đã thay đổi</h3><p>Bản tại máy:</p><pre>{text}</pre><p>Bản máy chủ:</p><pre>{conflict.work?.text ?? ''}</pre><button className="button secondary" onClick={() => void session.useServer()}>Dùng bản máy chủ</button><button className="button primary" disabled={!['DRAFT', 'RETURNED'].includes(conflict.work?.state ?? '')} onClick={() => void session.keepLocal()}>Giữ bản tại máy để lưu sau</button></div>}
      {view.unlocked && !view.work && <button className="button primary" disabled={busy || !!recovery} onClick={() => void session.send('start')}>Bắt đầu viết</button>}
      {view.work && <><p className="writing-state" role="status">{writingStates[view.work.state]}</p>{editable ? <><label className="writing-label">Bài viết của bạn<textarea aria-label="Bài viết của bạn" rows={12} maxLength={30000} value={text} disabled={busy || !!pending || !!conflict || !!recovery} onChange={e => session.edit(e.target.value)} /></label><p className="muted">{wordCount(text)} từ · {busy ? 'Đang đồng bộ…' : pending ? 'Đang chờ xác nhận thao tác' : session.dirty ? state.localSaved ? 'Đã lưu tại máy · Chưa lưu máy chủ' : 'Chưa lưu tại máy' : 'Đã lưu trên máy chủ'}</p><div className="writing-actions"><button className="button secondary" disabled={busy || !!pending || !!conflict || !!recovery} onClick={() => void session.send('draft')}>Lưu bài viết ngay</button><button className="button primary" disabled={busy || !!pending || !!conflict || !!recovery || !text.trim()} onClick={() => setConfirm(true)}>Nộp bài viết</button></div></> : <p>Nộp bài viết đã được ghi nhận để mở vòng tiếp theo. Bạn có thể xem kết quả sau khi giáo viên chấm.</p>}</>}
      {pending && <button className="button primary" disabled={busy || !!recovery} onClick={() => void session.send(pending.action)}>Gửi lại bài viết và đối soát</button>}
      {confirm && <div className="writing-confirm" role="dialog" aria-modal="true" aria-label="Xác nhận nộp bài viết"><h3>Nộp bài viết?</h3><p>Bài sẽ được gửi cho giáo viên. Sau khi nộp, bạn chỉ sửa được khi giáo viên cho phép.</p><button className="button primary" disabled={busy} onClick={() => void submit()}>Xác nhận nộp bài viết</button><button className="button secondary" onClick={() => setConfirm(false)}>Tiếp tục viết</button></div>}
      {view.history.length > 0 && <section className="writing-history"><h3>Lịch sử bài nộp</h3>{view.history.map((s, index) => <details key={s.id} open={index === 0}><summary>Bản nộp {view.history.length - index} · {new Date(s.submittedAt).toLocaleString('vi-VN')} · {s.grade ? `${s.grade.earnedPoints}/10 điểm` : 'Chờ chấm'}</summary><p className="writing-text">{s.text}</p>{s.grade && view.version && <WritingGrade grade={s.grade} definition={view.version.definition} />}</details>)}</section>}
    </>}
  </article>;
}
