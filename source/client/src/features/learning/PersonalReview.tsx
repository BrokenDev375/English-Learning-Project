import { useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import type { CatalogPage, Topic } from '../content/catalog';
import { allPages, difficulties } from '../content/authoring';
import type { Create } from './types';

interface ObjectiveReview {
  objectiveId: number; topicId: number; name: string; status: string; reason: string;
  needsReview: boolean; hasMistakes: boolean; stage: number; lastPracticedAt: string; nextReviewAt: string;
  submissionCount: number; firstAnsweredCount: number; firstCorrectCount: number; assistedCount: number; unansweredCount: number;
  lastWrongCount: number; lastIndependentAnswered: number; lastIndependentCorrect: number;
  availableQuestionCount: number; unseenQuestionCount: number; currentDifficulty: keyof typeof difficulties;
  recommendedDifficulty: keyof typeof difficulties; recommendationReason: string;
  freshSetCount: number; freshAnsweredCount: number; freshCorrectCount: number;
}
interface Dashboard {
  generatedAt: string; pendingSubmissions: number; totalObjectives: number;
  needsReviewCount: number; mistakeObjectives: number; objectives: CatalogPage<ObjectiveReview>;
}
const statusNames: Record<string, string> = {
  NEEDS_PRACTICE: 'Cần luyện thêm', NEEDS_INDEPENDENT_WORK: 'Cần tự làm thêm',
  NO_EVIDENCE: 'Chưa đủ dữ liệu', DUE: 'Đến mốc ôn', SCHEDULED: 'Đã có mốc ôn',
};
const recommendationNames: Record<string, string> = {
  KEEP_PRACTICING: 'Tiếp tục luyện mức hiện tại.',
  NOT_ENOUGH_DATA: 'Chưa đủ dữ liệu từ các bộ câu mới để đề xuất mức cao hơn.',
  NOT_ENOUGH_NEW_QUESTIONS: 'Chưa đủ câu mới để đánh giá hoặc luyện mức cao hơn. Cần bổ sung học liệu.',
  HIGHEST_DEMO_LEVEL: 'Đang ở mức khó cao nhất của ngân hàng demo.',
  TRY_NEXT_DIFFICULTY: 'Có thể thử mức khó tiếp theo trong Luyện tập theo chủ đề.',
};
const date = (value: string) => new Date(value).toLocaleString('vi-VN');

export default function PersonalReview({ start, disabled }: { start: (input: Create) => void; disabled: boolean }) {
  const [topics, setTopics] = useState<Topic[]>([]), [topic, setTopic] = useState(''), [kind, setKind] = useState('ALL');
  const [count, setCount] = useState(10), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Dashboard | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const pendingSync = useRef<string | null>(null);
  useEffect(() => {
    let active = true;
    void allPages<Topic>('/api/topics').then(rows => { if (active) setTopics(rows); })
      .catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setData(null);
    async function load() {
      // The server ledger makes repeated synchronization safe, including after reload.
      // Keep the same request ID when an ACK is uncertain.
      for (let batch = 0; batch < 10; batch++) {
        const syncId = pendingSync.current ?? requestId(); pendingSync.current = syncId;
        const result = await api<{ processedSubmissions: number; remainingSubmissions: number }>(
          '/api/me/review/sync', 'POST', { requestId: syncId });
        if (pendingSync.current === syncId) pendingSync.current = null;
        if (!active) return;
        if (result.remainingSubmissions === 0) break;
      }
      const params = new URLSearchParams({ kind, page: String(page), size: '12' });
      if (topic) params.set('topicId', topic);
      const result = await api<Dashboard>('/api/me/review?' + params);
      if (active) setData(result);
    }
    void load().catch(e => {
      if (active) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) pendingSync.current = null;
        setError(errorMessage(e));
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [topic, kind, page, refresh]);
  const blocked = disabled || loading || !data || data.pendingSubmissions > 0;
  function begin(reviewKind: 'DUE' | 'MISTAKES') {
    start({ mode: 'REVIEW', reviewKind, topicId: topic ? Number(topic) : null, count });
  }
  return <section className="personal-review">
    <span className="eyebrow">LUYỆN LẠI PHẦN BẠN CẦN</span><h1>Ôn tập cá nhân<span>.</span></h1>
    <p className="muted">Từ kết quả đã nộp, ưu tiên mục tiêu còn sai hoặc đã tới mốc ôn. Câu biến thể chưa gặp được ưu tiên trước câu đã làm.</p>
    <div className="panel learning-panel">
      <div className="filters">
        <label>Chủ đề ôn<select value={topic} disabled={disabled || loading} onChange={e => { setTopic(e.target.value); setPage(0); }}>
          <option value="">Tất cả chủ đề đã học</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select></label>
        <label>Nội dung hiển thị<select value={kind} disabled={disabled || loading} onChange={e => { setKind(e.target.value); setPage(0); }}>
          <option value="ALL">Tất cả mục tiêu đã có kết quả</option><option value="DUE">Nội dung cần ôn</option><option value="MISTAKES">Mục tiêu còn lỗi sai</option>
        </select></label>
        <label>Số câu mỗi lượt<input type="number" min={1} max={10} value={count} disabled={disabled || loading}
          onChange={e => setCount(Number(e.target.value))} /></label>
        <button className="button secondary" disabled={disabled || loading} onClick={() => setRefresh(v => v + 1)}>Cập nhật nội dung ôn</button>
      </div>
      <p className="muted">Có thể nhận ít câu hơn yêu cầu khi kho chưa đủ. Luyện lại câu đã gặp giúp củng cố kiến thức; gợi ý tăng độ khó chỉ dùng kết quả tự làm trên câu mới.</p>
      {error && <div className="message error" role="alert">{error}<button className="link-button" disabled={loading} onClick={() => setRefresh(v => v + 1)}>Thử tải lại nội dung ôn</button></div>}
      {loading && <p role="status">Đang cập nhật kết quả và nội dung ôn…</p>}
      {data && <>
        <div className="review-summary" aria-label="Tổng quan ôn tập">
          <p><strong>{data.totalObjectives}</strong> mục tiêu đã có kết quả</p>
          <p><strong>{data.needsReviewCount}</strong> nội dung cần ôn</p>
          <p><strong>{data.mistakeObjectives}</strong> mục tiêu còn lỗi sai</p>
        </div>
        {data.pendingSubmissions > 0 && <div className="message warning" role="status">Còn {data.pendingSubmissions} kết quả cần cập nhật. Bấm Cập nhật nội dung ôn để tiếp tục.</div>}
        <div className="material-actions">
          <button className="button primary" disabled={blocked || !Number.isInteger(count) || count < 1 || count > 10 || !data.needsReviewCount} onClick={() => begin('DUE')}>Bắt đầu ôn nội dung cần ôn</button>
          <button className="button secondary" disabled={blocked || !Number.isInteger(count) || count < 1 || count > 10 || !data.mistakeObjectives} onClick={() => begin('MISTAKES')}>Ôn mục tiêu còn lỗi sai</button>
        </div>
      </>}
    </div>
    {data && (!data.objectives.items.length ? <div className="panel learning-panel">
      <h2>{data.totalObjectives ? 'Chưa có nội dung phù hợp bộ lọc' : 'Chưa có kết quả để chọn bài ôn'}</h2>
      <p className="muted">{data.totalObjectives ? 'Bạn có thể xem tất cả mục tiêu hoặc chọn chủ đề khác.' : 'Học một bài hoặc luyện tập theo chủ đề rồi nộp bài. Kết quả của bạn sẽ được dùng để chọn nội dung ôn.'}</p>
    </div> : <>
      <div className="review-objectives">{data.objectives.items.map(o => <article className="panel learning-panel review-objective" key={o.objectiveId}>
        <div className="review-objective-heading"><h2>{o.name}</h2><span className={'review-state' + (o.needsReview ? ' review-state-due' : '')}>{statusNames[o.status] ?? o.status}</span></div>
        <p>{o.reason}</p>
        <p className="muted">Lượt gần nhất: tự làm đúng {o.lastIndependentCorrect}/{o.lastIndependentAnswered} câu · {o.lastWrongCount} câu sai lần đầu.</p>
        <p className="muted">Lịch sử: {o.submissionCount} lượt · {o.assistedCount} câu có hướng dẫn/hỗ trợ · {o.unansweredCount} câu bỏ trống.</p>
        <p>Đã luyện: {date(o.lastPracticedAt)}<br />Mốc nhắc ôn: <strong>{date(o.nextReviewAt)}</strong></p>
        <p className="muted">Mức {difficulties[o.currentDifficulty]}: {o.availableQuestionCount} câu trong kho · {o.unseenQuestionCount} câu chưa gặp.</p>
        {!o.availableQuestionCount && <p className="message warning">Chưa có câu đang công bố phù hợp mức này. Giảng viên cần bổ sung học liệu.</p>}
        {o.availableQuestionCount > 0 && o.unseenQuestionCount === 0 && <p className="muted">Lượt ôn sẽ dùng lại câu đã gặp.</p>}
        <div className="review-recommendation"><strong>Gợi ý mức luyện: {difficulties[o.recommendedDifficulty]}</strong>
          <p>{recommendationNames[o.recommendationReason] ?? o.recommendationReason}</p>
          <p className="muted">{o.freshSetCount} bộ đủ câu mới · tự làm đúng {o.freshCorrectCount}/{o.freshAnsweredCount} câu mới. Đây là gợi ý luyện tập nội bộ.</p>
        </div>
      </article>)}</div>
      <div className="pagination"><span>{data.objectives.totalElements} mục tiêu</span><div>
        <button className="button secondary" disabled={disabled || loading || page === 0} onClick={() => setPage(v => v - 1)}>Trang ôn trước</button>
        <button className="button secondary" disabled={disabled || loading || page + 1 >= data.objectives.totalPages} onClick={() => setPage(v => v + 1)}>Trang ôn sau</button>
      </div></div>
    </>)}
  </section>;
}
