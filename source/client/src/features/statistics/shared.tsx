import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import type { Metric, Performance } from './types';

export function useStatistics<T>(path: string, refresh: number) {
  const [data, setData] = useState<T | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  useEffect(() => {
    let active = true; setData(null); setLoading(true); setError('');
    void api<T>(path).then(value => { if (active) setData(value); })
      .catch(e => { if (active) setError(errorMessage(e)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [path, refresh]);
  return { data, loading, error };
}
export const date = (value: string | null) => value ? new Date(value).toLocaleString('vi-VN') : 'Chưa có';
export const percent = (value: number | null) => value === null ? 'Chưa có dữ liệu' : value.toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + '%';
export const modeNames: Record<string, string> = { LEARN: 'Học bài', PRACTICE: 'Luyện tập', REVIEW: 'Ôn tập' };
export const levelNames: Record<string, string> = { EASY: 'Dễ', MEDIUM: 'Vừa', HARD: 'Khó' };
export function Period({ value, change }: { value: string; change: (value: string) => void }) {
  return <label>Khoảng kết quả<select value={value} onChange={e => change(e.target.value)}><option value="ALL">Tất cả thời gian</option><option value="7D">7 ngày gần nhất</option><option value="30D">30 ngày gần nhất</option><option value="90D">90 ngày gần nhất</option></select></label>;
}
export function RequestState({ loading, error, retry }: { loading: boolean; error: string; retry: () => void }) {
  return <>{loading && <p role="status">Đang tải số liệu…</p>}{error && <div className="message error" role="alert">{error} <button className="link-button" onClick={retry}>Thử lại thống kê</button></div>}</>;
}
export function Pager({ page, pages, total, change }: { page: number; pages: number; total: number; change: (page: number) => void }) {
  return <div className="pagination"><span>{total} mục · Trang {pages ? page + 1 : 0}/{pages}</span><div><button className="button secondary" disabled={!page} onClick={() => change(page - 1)}>Trang trước</button><button className="button secondary" disabled={page + 1 >= pages} onClick={() => change(page + 1)}>Trang sau</button></div></div>;
}
export function Sample({ metric }: { metric: Metric }) {
  return <span>{percent(metric.accuracyPercent)}<small className="stats-sample">{metric.correct}/{metric.correct + metric.incorrect} câu đã trả lời đúng · {metric.unanswered} bỏ trống</small></span>;
}
export function PerformancePanel({ data }: { data: Performance }) {
  const rows = [['Câu hướng dẫn', data.guided], ['Câu tự làm có dùng hỗ trợ', data.assisted], ['Tự làm câu mới', data.independentNew], ['Tự làm câu đã gặp', data.independentSeen]] as const;
  return <section className="panel statistics-panel"><h2>Kết quả lần trả lời đầu</h2>
    <p className="muted">Tỷ lệ đúng tính trên câu đã trả lời; câu bỏ trống được ghi riêng. Một câu làm lại ở lượt khác vẫn là một lần quan sát. Các tỷ lệ này chưa chứng nhận trình độ.</p>
    <div className="stats-performance">{rows.map(([label, metric]) => <article key={label}><h3>{label}</h3><Sample metric={metric} />{metric.accuracyPercent !== null && <meter min={0} max={100} value={metric.accuracyPercent} aria-label={'Tỷ lệ đúng: ' + label} />}</article>)}</div>
    <p>Tổng lần đầu: <Sample metric={data.first} /></p>
    <div className="stats-practice"><p><strong>Làm lại sau phản hồi hướng dẫn</strong><Sample metric={data.guidedRetries} /></p><p><strong>Chữa sau nộp</strong><Sample metric={data.corrections} /></p></div>
    <p className="muted">Làm lại và chữa được tính riêng, không thay kết quả lần đầu. Khoảng thời gian lọc theo lúc nộp bài; lần chữa của những bài đó được tính đến lúc tải số liệu.</p>
  </section>;
}
