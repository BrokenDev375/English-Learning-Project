import { useState } from 'react';
import type { User } from '../../types';
import { initials } from '../../components/Brand';
import { navigate } from '../../navigation';
import type { Summary } from '../learning/types';
import type { LearnerOverview, Breakdown, Lesson, Page, Rewards } from './types';
import { useStatistics, date, percent, modeNames, Period, RequestState, Pager, Sample, PerformancePanel } from './shared';
import './statistics.css';

const openLearning = (values: Record<string, string | null>) => navigate({ view: 'learning', ...values }, { reset: true });
export default function LearnerProfile({ user }: { user: User }) {
  const [period, setPeriod] = useState('ALL'), [refresh, setRefresh] = useState(0);
  const overview = useStatistics<LearnerOverview>('/api/me/statistics?period=' + period, refresh);
  const retry = () => setRefresh(v => v + 1), data = overview.data;
  return <section className="statistics"><span className="eyebrow">HÀNH TRÌNH CỦA BẠN</span><h1>Hồ sơ học tập<span>.</span></h1>
    <div className="stats-profile"><span className="avatar large">{initials(user.fullName)}</span><div><h2>{user.fullName}</h2><p className="muted">{user.email} · Tham gia {new Date(user.createdAt).toLocaleDateString('vi-VN')}</p></div></div>
    <div className="stats-toolbar"><Period value={period} change={setPeriod} /><button className="button secondary" onClick={retry}>Cập nhật số liệu</button></div>
    <RequestState {...overview} retry={retry} />
    {data && <>
      <div className="stats-cards" aria-label="Tổng quan học tập"><article className="panel"><strong>{data.activity.submittedAttempts}</strong><span>Lượt đã nộp trong khoảng</span></article><article className="panel"><strong>{data.activity.completedLessons}</strong><span>Bài đã hoàn thành · toàn bộ</span></article><article className="panel"><strong>{data.activity.activeAttempts}</strong><span>Lượt đang làm · hiện tại</span></article><article className="panel"><strong>{data.review.needsReview}</strong><span>Mục tiêu cần ôn · đã đồng bộ</span></article></div>
      <p className="muted">Đã nộp: {data.activity.modes.map(m => `${modeNames[m.mode]} ${m.submissions}`).join(' · ')}. Lần nộp gần nhất: {date(data.activity.lastSubmittedAt)}.</p>
      <PerformancePanel data={data.performance} />
      <section className="panel statistics-panel"><h2>Nội dung cần ôn</h2><p>{data.review.mistakeObjectives} mục tiêu còn lỗi sai · {data.review.trackedObjectives} mục tiêu đã theo dõi.</p><p>Mốc ôn sắp tới: {date(data.review.nextScheduledAt)}</p>
        {data.review.pendingSubmissions > 0 && <p className="message warning">Có {data.review.pendingSubmissions} kết quả mới chưa cập nhật vào lịch ôn. Mở Ôn tập cá nhân để cập nhật.</p>}
        <button className="button primary" onClick={() => openLearning({ tab: 'review' })}>Xem nội dung cần ôn</button></section>
      <RewardPanel data={data.rewards} />
      <p className="muted">Số liệu tải lúc {date(data.generatedAt)}. Tiến độ bài và lịch ôn là toàn bộ lịch sử, không phụ thuộc khoảng kết quả đã chọn.</p>
    </>}
    <BreakdownPanel key={period} period={period} refresh={refresh} retry={retry} />
    <CompletedLessons refresh={refresh} retry={retry} />
    <History refresh={refresh} retry={retry} />
  </section>;
}
function RewardPanel({ data }: { data: Rewards }) {
  return <section className="panel statistics-panel"><h2>Điểm thưởng và thành tích</h2>{data.state !== 'AVAILABLE' ? <p className="muted">Chưa có dữ liệu điểm kinh nghiệm, chuỗi ngày học, huy hiệu và Giải đấu tuần.</p> : <>
    <p>{data.totalXp ?? '—'} XP · Cấp {data.level ?? '—'} · Chuỗi {data.streakDays ?? '—'} ngày</p>
    <ul>{data.badges?.map(b => <li key={b.code}>{b.name} · {date(b.earnedAt)}</li>)}</ul>
    {data.currentWeek && <p>Tuần {data.currentWeek.week} · {data.currentWeek.tier} · Vị trí {data.currentWeek.position ?? '—'} · {data.currentWeek.xp ?? '—'} XP</p>}
    <p className="muted">Thành tích từ các bài đã được xử lý phần thưởng. Bài vừa nộp có thể cần một lúc để cập nhật.</p>
    <div className="material-actions"><button className="button secondary" onClick={() => navigate({ view: 'rewards' }, { reset: true })}>Xem phần thưởng →</button><button className="button secondary" onClick={() => navigate({ view: 'league' }, { reset: true })}>Xem bảng tuần →</button></div>
    {!!data.history?.length && <details><summary>Lịch sử Giải đấu tuần</summary><ul>{data.history.map(w => <li key={w.week}>{w.week}: {w.tier} · Vị trí {w.position ?? '—'} · {w.xp ?? '—'} XP</li>)}</ul></details>}
  </>}</section>;
}
function BreakdownPanel({ period, refresh, retry }: { period: string; refresh: number; retry: () => void }) {
  const [group, setGroup] = useState('TOPIC'), [page, setPage] = useState(0);
  const result = useStatistics<Page<Breakdown>>(`/api/me/statistics/breakdown?period=${period}&groupBy=${group}&page=${page}&size=10`, refresh);
  return <section className="panel statistics-panel"><h2>Độ chính xác theo nội dung</h2><label>Thống kê theo<select value={group} onChange={e => { setGroup(e.target.value); setPage(0); }}><option value="TOPIC">Chủ đề</option><option value="OBJECTIVE">Mục tiêu kiến thức</option></select></label>
    <RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có bài đã nộp trong khoảng này.</p> : <div className="table-wrap"><table><thead><tr><th>Nội dung</th><th>Tự làm câu mới</th><th>Tự làm câu đã gặp</th><th>Có hỗ trợ / hướng dẫn</th></tr></thead><tbody>{result.data.items.map(g => <tr key={g.id}><td>{g.name}</td><td><Sample metric={g.performance.independentNew} /></td><td><Sample metric={g.performance.independentSeen} /></td><td><Sample metric={g.performance.assisted} /><small className="stats-sample">Hướng dẫn: <Sample metric={g.performance.guided} /></small></td></tr>)}</tbody></table></div>}
    <Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}</section>;
}
function CompletedLessons({ refresh, retry }: { refresh: number; retry: () => void }) {
  const [page, setPage] = useState(0);
  const result = useStatistics<Page<Lesson>>(`/api/me/statistics/lessons?page=${page}&size=5`, refresh);
  return <section className="panel statistics-panel"><h2>Bài đã hoàn thành</h2><RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có bài hoàn thành. Mở Vào chơi để bắt đầu vòng đầu tiên.</p> : <div className="table-wrap"><table><thead><tr><th>Bài học</th><th>Hoàn thành</th><th>Điểm lượt nộp gần nhất</th><th>Thao tác</th></tr></thead><tbody>{result.data.items.map(l => <tr key={l.lessonId}><td>{l.title}</td><td>{date(l.completedAt)}</td><td>{l.lastScorePercent === null ? '—' : percent(l.lastScorePercent)}</td><td><button className="link-button" onClick={() => openLearning({ attempt: String(l.lastAttemptId), tab: 'history' })}>Mở lượt gần nhất</button></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}</section>;
}
function History({ refresh, retry }: { refresh: number; retry: () => void }) {
  const [page, setPage] = useState(0), [mode, setMode] = useState('');
  const result = useStatistics<Page<Summary>>(`/api/attempts?page=${page}&size=5${mode ? '&mode=' + mode : ''}`, refresh);
  return <section className="panel statistics-panel"><h2>Lịch sử học, luyện và ôn</h2><label>Chế độ lịch sử<select value={mode} onChange={e => { setMode(e.target.value); setPage(0); }}><option value="">Tất cả</option>{Object.entries(modeNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có lượt làm phù hợp.</p> : <div className="table-wrap"><table><thead><tr><th>Lượt</th><th>Chế độ</th><th>Trạng thái</th><th>Cập nhật</th><th>Thao tác</th></tr></thead><tbody>{result.data.items.map(a => <tr key={a.attemptId}><td>#{a.attemptId}</td><td>{modeNames[a.mode]}</td><td>{a.state === 'SUBMITTED' ? 'Đã nộp' : 'Đang làm'}</td><td>{date(a.submittedAt ?? a.updatedAt)}</td><td><button className="link-button" onClick={() => openLearning({ attempt: String(a.attemptId), tab: 'history' })}>{a.state === 'SUBMITTED' ? 'Xem kết quả' : 'Tiếp tục làm'}</button></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}
    <p className="muted">Lịch sử hiển thị toàn bộ thời gian. Bộ lọc khoảng kết quả phía trên chỉ áp dụng cho các thống kê câu trả lời.</p></section>;
}
