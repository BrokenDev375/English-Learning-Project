import { useState } from 'react';
import type { AdminOverview, Question, Page } from './types';
import { useStatistics, date, levelNames, modeNames, Period, RequestState, Pager, Sample, PerformancePanel } from './shared';
import './statistics.css';

export default function AdminStatistics() {
  const [period, setPeriod] = useState('ALL'), [refresh, setRefresh] = useState(0);
  const result = useStatistics<AdminOverview>('/api/admin/statistics?period=' + period, refresh);
  const retry = () => setRefresh(v => v + 1), data = result.data;
  const roleNames: Record<string, string> = { STUDENT: 'Học viên', TEACHER: 'Giảng viên', ADMIN: 'Quản trị viên' };
  const statuses: Record<string, string> = { ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', DELETED: 'Đã xóa' };
  return <section className="statistics"><span className="eyebrow">TỔNG QUAN HỆ THỐNG</span><h1>Thống kê học tập<span>.</span></h1>
    <p className="muted">Số liệu tổng hợp từ bài đã nộp. Không hiển thị đáp án hoặc nội dung riêng của từng học viên.</p>
    <div className="stats-toolbar"><Period value={period} change={setPeriod} /><button className="button secondary" onClick={retry}>Cập nhật số liệu</button></div><RequestState {...result} retry={retry} />
    {data && <><div className="stats-cards" aria-label="Tổng quan hệ thống"><article className="panel"><strong>{data.activity.submittedAttempts}</strong><span>Lượt đã nộp trong khoảng</span></article><article className="panel"><strong>{data.activity.learnersWithSubmissions}</strong><span>Người có bài nộp trong khoảng</span></article><article className="panel"><strong>{data.activity.activeAttempts}</strong><span>Lượt đang làm · hiện tại</span></article><article className="panel"><strong>{data.presence.connectedAccounts}</strong><span>Tài khoản có kết nối mở</span></article></div>
      <p className="muted">{data.presence.openConnections} kết nối WebSocket còn heartbeat trên máy chủ hiện tại. Nhiều tab của một tài khoản chỉ tính một người. Trạng thái có thể trễ trong cửa sổ heartbeat khi mất mạng.</p>
      <section className="panel statistics-panel"><h2>Tài khoản và hoạt động</h2><p>{data.roles.map(r => `${roleNames[r.label] ?? r.label}: ${r.count}`).join(' · ')}</p><p>{data.statuses.map(s => `${statuses[s.label] ?? s.label}: ${s.count}`).join(' · ')}</p><p>{data.activity.modes.map(m => `${modeNames[m.mode]}: ${m.submissions}`).join(' · ')}</p><p className="muted">Tài khoản và lượt đang làm là số hiện tại. Bài nộp giữ lịch sử kể cả tài khoản đã bị khóa hoặc xóa.</p></section>
      <PerformancePanel data={data.performance} />
      <section className="panel statistics-panel"><h2>Kết quả theo độ khó</h2><div className="table-wrap"><table><thead><tr><th>Độ khó</th><th>Số câu lần đầu</th><th>Tự làm câu mới</th><th>Tự làm câu đã gặp</th></tr></thead><tbody>{data.difficulties.map(d => <tr key={d.difficulty}><td>{levelNames[d.difficulty]}</td><td>{d.performance.first.total}</td><td><Sample metric={d.performance.independentNew} /></td><td><Sample metric={d.performance.independentSeen} /></td></tr>)}</tbody></table></div></section>
      <p className="muted">Tải lúc {date(data.generatedAt)}. Phân bố độ khó theo các câu trong bài đã nộp, không phải toàn bộ ngân hàng câu hỏi.</p>
    </>}
    <QuestionErrors key={period} period={period} refresh={refresh} retry={retry} />
  </section>;
}
function QuestionErrors({ period, refresh, retry }: { period: string; refresh: number; retry: () => void }) {
  const [difficulty, setDifficulty] = useState(''), [page, setPage] = useState(0);
  const result = useStatistics<Page<Question>>(`/api/admin/statistics/questions?period=${period}&page=${page}&size=10${difficulty ? '&difficulty=' + difficulty : ''}`, refresh);
  return <section className="panel statistics-panel"><h2>Câu hỏi thường trả lời sai</h2><p className="muted">Xếp theo số lần sai ở lần đầu, tách từng phiên bản câu. Lượt làm lại và chữa không thay số sai gốc; cỡ mẫu nhỏ chưa đủ kết luận câu có vấn đề.</p>
    <label>Độ khó câu hỏi<select value={difficulty} onChange={e => { setDifficulty(e.target.value); setPage(0); }}><option value="">Tất cả</option>{Object.entries(levelNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
    <RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có câu trả lời sai trong phạm vi này.</p> : <div className="table-wrap"><table><thead><tr><th>Câu hỏi / phiên bản</th><th>Độ khó</th><th>Sai lần đầu</th><th>Số người đã gặp trong bài nộp</th><th>Kết quả lần đầu</th></tr></thead><tbody>{result.data.items.map(q => <tr key={q.questionVersionId}><td className="stats-question">{q.prompt}<small className="stats-sample">#{q.questionId} · Phiên bản {q.versionNo}</small></td><td>{levelNames[q.difficulty]}</td><td>{q.first.incorrect}</td><td>{q.learners}</td><td><Sample metric={q.first} /></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}</section>;
}
