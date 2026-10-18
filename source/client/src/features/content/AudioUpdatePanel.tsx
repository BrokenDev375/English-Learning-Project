import { useRef, useState } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import './audio-update.css';

interface QuestionRow { id: number; title: string; versionId: number; reason: string | null }
interface LessonRow { id: number; title: string; versionId: number; requiredQuestionIds: number[]; reason: string | null }
interface Preview {
  materialId: number; targetVersionId: number; targetVersionNo: number; previewToken: string;
  questions: QuestionRow[]; lessons: LessonRow[]; foreignQuestionCount: number; foreignLessonCount: number;
}
interface Command {
  requestId: string; targetVersionId: number; previewToken: string;
  questionIds: number[]; lessonIds: number[]; contentConfirmed: boolean;
}
interface Applied { questions: { id: number; versionId: number }[]; lessons: { id: number; versionId: number }[] }
export default function AudioUpdatePanel({ materialId, disabled, onBlockingChange }: {
  materialId: number; disabled: boolean; onBlockingChange: (value: boolean) => void;
}) {
  const [preview, setPreview] = useState<Preview | null>(null);
  const [questions, setQuestions] = useState<number[]>([]), [lessons, setLessons] = useState<number[]>([]);
  const [confirmed, setConfirmed] = useState(false), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState('');
  const pending = useRef<Command | null>(null);
  const path = '/api/author/materials/' + materialId + '/audio-update';
  const locked = busy || uncertain || disabled;
  async function load() {
    if (locked) return;
    setBusy(true); onBlockingChange(true); setError(''); setNotice('');
    try {
      const next = await api<Preview>(path);
      setPreview(next); setConfirmed(false);
      const selected = next.questions.filter(q => !q.reason).slice(0, 100).map(q => q.id);
      setQuestions(selected);
      setLessons(next.lessons.filter(l => !l.reason && l.requiredQuestionIds.every(id => selected.includes(id))).slice(0, 100).map(l => l.id));
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); onBlockingChange(false); }
  }
  function selectQuestion(id: number, checked: boolean) {
    if (checked && questions.length >= 100) { setError('Mỗi lần chọn tối đa 100 câu hỏi.'); return; }
    setQuestions(checked ? [...questions, id] : questions.filter(q => q !== id));
    if (!checked) setLessons(lessons.filter(lesson => !preview?.lessons.find(l => l.id === lesson)?.requiredQuestionIds.includes(id)));
    setConfirmed(false);
  }
  function selectLesson(row: LessonRow, checked: boolean) {
    if (checked) {
      const required = [...new Set([...questions, ...row.requiredQuestionIds])];
      if (lessons.length >= 100 || required.length > 100) { setError('Mỗi lần chọn tối đa 100 câu hỏi và 100 bài học.'); return; }
      setQuestions(required); setLessons([...lessons, row.id]);
    } else setLessons(lessons.filter(id => id !== row.id));
    setConfirmed(false);
  }
  async function apply(retry = false) {
    if (busy || disabled || (!retry && locked)) return;
    const command = retry ? pending.current : preview && {
      requestId: requestId(), targetVersionId: preview.targetVersionId, previewToken: preview.previewToken,
      questionIds: [...questions], lessonIds: [...lessons], contentConfirmed: confirmed,
    };
    if (!command) return;
    pending.current = command; setBusy(true); onBlockingChange(true); setError('');
    let ambiguous = false;
    try {
      const result = await api<Applied>(path, 'POST', command);
      pending.current = null; setUncertain(false); setPreview(null); setConfirmed(false);
      setNotice('Đã cập nhật audio cho ' + result.questions.length + ' câu hỏi và ' + result.lessons.length + ' bài học. Các phiên bản cũ được giữ nguyên.');
    } catch (failure) {
      ambiguous = failure instanceof ApiError && (failure.status === 0 || failure.status >= 500);
      setUncertain(ambiguous); setError(errorMessage(failure));
      if (!ambiguous) {
        pending.current = null;
        if (failure instanceof ApiError && failure.status === 409) setPreview(null);
      }
    } finally { setBusy(false); onBlockingChange(ambiguous); }
  }
  return <section className="audio-update-panel" aria-label="Cập nhật audio đồng loạt">
    <h3>Áp dụng audio mới cho nội dung liên quan</h3>
    <p>Chọn câu hỏi và bài học để dùng phiên bản audio đã xuất bản. Nội dung cũ và các lượt làm đã tạo được giữ nguyên.</p>
    {notice && <div className="message success" role="status">{notice}</div>}
    {error && <div className="message error" role="alert">{error}</div>}
    {uncertain ? <div className="message warning">
      <p>Chưa nhận được xác nhận. Thử lại cùng thao tác để tránh tạo trùng phiên bản.</p>
      <button type="button" className="button secondary" disabled={busy || disabled} onClick={() => void apply(true)}>Thử lại cập nhật audio</button>
    </div> : <button type="button" className="button secondary" disabled={locked} onClick={() => void load()}>
      {busy ? 'Đang xử lý…' : preview ? 'Tải lại danh sách cập nhật' : 'Cập nhật audio cho câu hỏi/bài học'}
    </button>}
    {preview && <div className="audio-update-options">
      <p><strong>Áp dụng phiên bản audio {preview.targetVersionNo}.</strong> Chọn bài sẽ chọn kèm các câu cần cập nhật trong bài đó.</p>
      <fieldset disabled={locked}>
        <legend>Câu hỏi liên quan</legend>
        {preview.questions.length === 0 && <p className="muted">Không có câu hỏi của bạn cần cập nhật.</p>}
        {preview.questions.map(q => <label className="audio-update-row" key={q.id}>
          <input type="checkbox" disabled={!!q.reason} checked={questions.includes(q.id)} onChange={e => selectQuestion(q.id, e.target.checked)} />
          <span>{q.title}{q.reason && <small>{q.reason}</small>}</span>
        </label>)}
      </fieldset>
      <fieldset disabled={locked}>
        <legend>Bài học liên quan</legend>
        {preview.lessons.length === 0 && <p className="muted">Không có bài học của bạn cần cập nhật.</p>}
        {preview.lessons.map(l => <label className="audio-update-row" key={l.id}>
          <input type="checkbox" disabled={!!l.reason} checked={lessons.includes(l.id)} onChange={e => selectLesson(l, e.target.checked)} />
          <span>{l.title}{l.reason && <small>{l.reason}</small>}</span>
        </label>)}
      </fieldset>
      {(preview.foreignQuestionCount > 0 || preview.foreignLessonCount > 0) &&
        <p className="muted">Có {preview.foreignQuestionCount} câu hỏi và {preview.foreignLessonCount} bài học thuộc người biên soạn khác. Chủ nội dung có thể mở tài liệu này để cập nhật phần của họ.</p>}
      <label className="audio-update-row">
        <input type="checkbox" disabled={locked} checked={confirmed} onChange={e => setConfirmed(e.target.checked)} />
        <span>Tôi đã kiểm tra transcript và đáp án của các câu đã chọn phù hợp với audio mới.</span>
      </label>
      <button type="button" className="button primary" disabled={locked || !confirmed || (questions.length === 0 && lessons.length === 0)} onClick={() => void apply()}>
        Áp dụng cho {questions.length} câu hỏi và {lessons.length} bài học
      </button>
    </div>}
  </section>;
}
