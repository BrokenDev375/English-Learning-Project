import { useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import type { Create } from './types';
import { durableCall } from './storage/journal';

type ReviewKind = 'ALL' | 'PRIORITY' | 'WRONG';
interface Dashboard {
  pendingSubmissions: number; choices: { all: number; priority: number; wrong: number };
}
const options: { kind: ReviewKind; count: keyof Dashboard['choices']; title: string; description: string; empty: string }[] = [
  { kind: 'ALL', count: 'all', title: 'Ôn tất cả',
    description: 'Các câu sai ở vòng chơi chưa ôn đúng và toàn bộ câu biến thể đang công bố.',
    empty: 'Chưa có câu sai hoặc câu biến thể đang công bố.' },
  { kind: 'PRIORITY', count: 'priority', title: 'Ôn ưu tiên',
    description: 'Các câu sai chưa ôn đúng, xen kẽ câu biến thể cùng mục tiêu kiến thức.',
    empty: 'Hãy nộp một bài vòng chơi để hệ thống xác định câu sai và biến thể liên quan.' },
  { kind: 'WRONG', count: 'wrong', title: 'Ôn các câu sai',
    description: 'Chỉ làm lại câu sai ở vòng chơi chưa ôn đúng, gồm câu hướng dẫn và tự làm.',
    empty: 'Chưa có câu sai ở vòng chơi còn trong học liệu đang công bố.' },
];

export default function PersonalReview({ start, disabled, userId }: { start: (input: Create) => void; disabled: boolean; userId: number }) {
  const [count, setCount] = useState(10), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Dashboard | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const pendingSync = useRef<string | null>(null);
  useEffect(() => {
    const changed = () => setRefresh(v => v + 1);
    window.addEventListener('review-changed', changed); window.addEventListener('learning-recover', changed);
    return () => { window.removeEventListener('review-changed', changed); window.removeEventListener('learning-recover', changed); };
  }, []);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setData(null);
    async function load() {
      // The server ledger makes repeated synchronization safe, including after reload.
      // Keep the same request ID when an ACK is uncertain.
      for (let batch = 0; batch < 10; batch++) {
        const syncId = pendingSync.current ?? requestId(); pendingSync.current = syncId;
        const result = await durableCall<{ processedSubmissions: number; remainingSubmissions: number }>(userId, 'review-sync', { requestId: syncId });
        if (pendingSync.current === syncId) pendingSync.current = null;
        if (!active) return;
        if (result.remainingSubmissions === 0) break;
      }
      const result = await api<Dashboard>('/api/me/review?size=1');
      if (active) setData(result);
    }
    void load().catch(e => {
      if (active) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) pendingSync.current = null;
        setError(errorMessage(e));
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [userId, refresh]);
  const blocked = disabled || loading || !data || data.pendingSubmissions > 0 || !Number.isInteger(count) || count < 1 || count > 10;
  function begin(reviewKind: ReviewKind) {
    start({ mode: 'REVIEW', reviewKind, topicId: null, count });
  }
  return <section className="personal-review">
    <span className="eyebrow">LUYỆN LẠI PHẦN BẠN CẦN</span><h1>Ôn tập cá nhân<span>.</span></h1>
    <p className="muted">Chọn cách ôn bạn muốn. Nộp lượt ôn và làm đúng câu nào, câu đó sẽ rời danh sách câu sai. Nếu chơi vòng sau lại sai, câu đó sẽ được thêm lại.</p>
    <div className="review-toolbar">
        <label>Số câu mỗi lượt<input type="number" min={1} max={10} value={count} disabled={disabled || loading}
          onChange={e => setCount(Number(e.target.value))} /></label>
        <button className="button secondary" disabled={disabled || loading} onClick={() => setRefresh(v => v + 1)}>Cập nhật nội dung ôn</button>
    </div>
    {error && <div className="message error" role="alert">{error}<button className="link-button" disabled={loading} onClick={() => setRefresh(v => v + 1)}>Thử tải lại nội dung ôn</button></div>}
    {loading && <p role="status">Đang cập nhật kết quả và nội dung ôn…</p>}
    {data && data.pendingSubmissions > 0 && <div className="message warning" role="status">Còn {data.pendingSubmissions} kết quả cần cập nhật. Bấm Cập nhật nội dung ôn để tiếp tục.</div>}
    <div className="review-options" aria-label="Chọn cách ôn tập">
      {options.map(option => <article className="panel review-option" key={option.kind}>
        <h2>{option.title}</h2><p>{option.description}</p>
        <p className="review-option-count">{data ? <><strong>{data.choices[option.count]}</strong> câu có thể ôn</> : 'Đang tải kho câu…'}</p>
        {data && !data.choices[option.count] && <p className="muted">{option.empty}</p>}
        <button className={'button ' + (option.kind === 'PRIORITY' ? 'primary' : 'secondary')}
          disabled={blocked || !data?.choices[option.count]} onClick={() => begin(option.kind)}>{option.title}</button>
      </article>)}
    </div>
    <p className="muted review-footnote">Mỗi lượt tối đa 10 câu, có thể ít hơn nếu kho chưa đủ. Tiếp tục lượt đang dở trong Lịch sử làm bài. Ôn tập không tính vượt vòng.</p>
  </section>;
}
