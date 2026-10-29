import { useEffect, useState } from 'react';
import { errorMessage } from '../../api';
import { useRouteParam } from '../../navigation';
import { allPages } from '../content/authoring';
import type { TeacherUnit, TeacherOverview, TeacherLesson, Breakdown, Question, Page } from './types';
import { useStatistics, date, levelNames, Period, RequestState, Pager, Sample, PerformancePanel } from './shared';
import { roundOption } from '../content/roundNumbering';
import './statistics.css';

function useOwnedUnits(refresh: number) {
  const [data, setData] = useState<TeacherUnit[] | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  useEffect(() => {
    let active = true; setLoading(true); setData(null); setError('');
    void allPages<TeacherUnit>('/api/teacher/statistics/units').then(rows => { if (active) setData(rows); })
      .catch(e => { if (active) setError(errorMessage(e)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refresh]);
  return { data, loading, error };
}
export default function TeacherStatistics() {
  const [unit, setUnit] = useRouteParam('unit'), [period, setPeriod] = useRouteParam('period', 'ALL');
  const [refresh, setRefresh] = useState(0), retry = () => setRefresh(v => v + 1);
  const units = useOwnedUnits(refresh);
  const query = new URLSearchParams({ period, ...(unit ? { unitId: unit } : {}) }).toString();
  const overview = useStatistics<TeacherOverview>('/api/teacher/statistics?' + query, refresh), data = overview.data;
  return <section className="statistics"><span className="eyebrow">HIỆU QUẢ HỌC LIỆU CỦA BẠN</span><h1>Thống kê học liệu<span>.</span></h1>
    <p className="muted">Số liệu tổng hợp của các vòng đấu bạn làm chủ. Một câu hỏi dùng chung chỉ được tính từ lượt học trong vòng đấu này.</p>
    <div className="stats-toolbar"><label className="teacher-unit-filter">Vòng đấu của tôi<select value={unit} disabled={units.loading || !units.data} onChange={e => setUnit(e.target.value)}>
      <option value="">Tất cả vòng đấu của tôi</option>
      {unit && units.data && !units.data.some(u => String(u.unitId) === unit) && <option value={unit}>Vòng đấu không khả dụng</option>}
      {units.data?.map(u => <option key={u.unitId} value={u.unitId}>{roundOption(u.roundNumber, u.title)}</option>)}
    </select></label><Period value={period} change={setPeriod} /><button className="button secondary" onClick={retry}>Cập nhật số liệu</button></div>

    <RequestState {...units} retry={retry} /><RequestState {...overview} retry={retry} />
    {units.data?.length === 0 && <p className="message">Bạn chưa được phân công vòng đấu. Quản trị viên phân công vòng để bạn biên soạn và theo dõi kết quả.</p>}
    <p className="muted">Chỉ tính lượt học mở từ bài trong vòng đấu, gồm cả phiên bản cũ và bài ngừng công bố. Ôn tập cá nhân và lượt luyện tập cũ được theo dõi riêng.</p>
    {data && <><div className="stats-cards" aria-label="Tổng quan học liệu">
      <article className="panel"><strong>{data.learnersStarted}</strong><span>Người đã bắt đầu · toàn bộ</span></article>
      <article className="panel"><strong>{data.activity.submittedAttempts}</strong><span>Lượt đã nộp trong khoảng</span></article>
      <article className="panel"><strong>{data.activity.activeAttempts}</strong><span>Lượt đang làm · hiện tại</span></article>
      <article className="panel"><strong>{data.activity.completedLessons}</strong><span>Lượt hoàn thành bài · toàn bộ</span></article>
    </div><p className="muted">{data.units} vòng đấu · {data.activity.learnersWithSubmissions} người có bài nộp trong khoảng. Một người hoàn thành hai bài được tính hai lượt hoàn thành; học lại cùng bài không cộng thêm.</p>
      <section className="panel statistics-panel"><h2>Nhu cầu hỗ trợ</h2><p>{data.hintOpenedQuestions} câu đã mở gợi ý · {data.performance.guidedRetries.total} lần làm lại câu hướng dẫn · {data.performance.corrections.total} lần chữa sau nộp.</p><p className="muted">Chỉ xét các bài đã nộp trong khoảng chọn. Mở lại cùng gợi ý trong một lượt không cộng thêm.</p></section>
      <PerformancePanel data={data.performance} />
      <section className="panel statistics-panel"><h2>Kết quả theo độ khó</h2><div className="table-wrap"><table><thead><tr><th>Độ khó</th><th>Số câu lần đầu</th><th>Tự làm câu mới</th><th>Tự làm câu đã gặp</th></tr></thead><tbody>{data.difficulties.map(d => <tr key={d.difficulty}><td>{levelNames[d.difficulty]}</td><td>{d.performance.first.total}</td><td><Sample metric={d.performance.independentNew} /></td><td><Sample metric={d.performance.independentSeen} /></td></tr>)}</tbody></table></div></section>
      <p className="muted">Tải lúc {date(data.generatedAt)}. Người bắt đầu, lượt đang làm và số hoàn thành là trạng thái toàn bộ lịch sử; bộ lọc thời gian áp dụng cho bài đã nộp và kết quả câu trả lời.</p>
    </>}
    <TeacherBreakdown key={'groups:' + query} query={query} refresh={refresh} retry={retry} />
    <Lessons key={'lessons:' + query} query={query} refresh={refresh} retry={retry} />
    <QuestionErrors key={'questions:' + query} query={query} refresh={refresh} retry={retry} />
  </section>;
}
function TeacherBreakdown({ query, refresh, retry }: { query: string; refresh: number; retry: () => void }) {
  const [group, setGroup] = useState('TOPIC'), [page, setPage] = useState(0);
  const result = useStatistics<Page<Breakdown>>(`/api/teacher/statistics/breakdown?${query}&groupBy=${group}&page=${page}&size=10`, refresh);
  return <section className="panel statistics-panel"><h2>Kết quả theo nội dung</h2><label>Thống kê theo<select value={group} onChange={e => { setGroup(e.target.value); setPage(0); }}><option value="TOPIC">Chủ đề</option><option value="OBJECTIVE">Mục tiêu kiến thức</option></select></label>
    <RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có bài đã nộp trong phạm vi này.</p> : <div className="table-wrap"><table><thead><tr><th>Nội dung</th><th>Tự làm câu mới</th><th>Tự làm câu đã gặp</th><th>Có hỗ trợ / hướng dẫn</th></tr></thead><tbody>{result.data.items.map(g => <tr key={g.id}><td>{g.name}</td><td><Sample metric={g.performance.independentNew} /></td><td><Sample metric={g.performance.independentSeen} /></td><td><Sample metric={g.performance.assisted} /><small className="stats-sample">Hướng dẫn: <Sample metric={g.performance.guided} /></small></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}
  </section>;
}
function Lessons({ query, refresh, retry }: { query: string; refresh: number; retry: () => void }) {
  const [page, setPage] = useState(0);
  const result = useStatistics<Page<TeacherLesson>>(`/api/teacher/statistics/lessons?${query}&page=${page}&size=10`, refresh);
  return <section className="panel statistics-panel"><h2>Hiệu quả từng bài học</h2><p className="muted">Tên theo phiên bản mới nhất bạn quản lý; số liệu giữ toàn bộ phiên bản đã học. Số người bắt đầu, đang làm và hoàn thành là toàn bộ lịch sử.</p>
    <RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có bài học trong phạm vi này.</p> : <div className="table-wrap"><table><thead><tr><th>Bài học</th><th>Người đã bắt đầu</th><th>Lượt nộp trong khoảng</th><th>Người hoàn thành</th><th>Kết quả lần đầu</th><th>Hỗ trợ trong bài nộp</th></tr></thead><tbody>{result.data.items.map(l => <tr key={l.lessonId}><td>{l.title}<small className="stats-sample">{l.unitTitle}</small></td><td>{l.learnersStarted}<small className="stats-sample">{l.activeAttempts} lượt đang làm</small></td><td>{l.submittedAttempts}</td><td>{l.completedLearners}</td><td><Sample metric={l.performance.first} /></td><td>{l.hintOpenedQuestions} câu mở gợi ý<small className="stats-sample">{l.performance.guidedRetries.total} lần làm lại · {l.performance.corrections.total} lần chữa</small></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}
  </section>;
}
function QuestionErrors({ query, refresh, retry }: { query: string; refresh: number; retry: () => void }) {
  const [difficulty, setDifficulty] = useState(''), [page, setPage] = useState(0);
  const result = useStatistics<Page<Question>>(`/api/teacher/statistics/questions?${query}&page=${page}&size=10${difficulty ? '&difficulty=' + difficulty : ''}`, refresh);
  return <section className="panel statistics-panel"><h2>Câu hỏi thường trả lời sai</h2><p className="muted">Chỉ đếm lần đầu trong bài đã nộp thuộc vòng đấu của bạn; phiên bản câu hỏi được thống kê riêng. Không bao gồm lượt dùng chung câu hỏi ở vòng đấu khác.</p>
    <label>Độ khó câu hỏi<select value={difficulty} onChange={e => { setDifficulty(e.target.value); setPage(0); }}><option value="">Tất cả</option>{Object.entries(levelNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
    <RequestState {...result} retry={retry} />{result.data && <>{!result.data.items.length ? <p>Chưa có câu trả lời sai trong phạm vi này.</p> : <div className="table-wrap"><table><thead><tr><th>Câu hỏi / phiên bản</th><th>Độ khó</th><th>Sai lần đầu</th><th>Số người đã gặp trong bài nộp</th><th>Kết quả lần đầu</th></tr></thead><tbody>{result.data.items.map(q => <tr key={q.questionVersionId}><td className="stats-question">{q.prompt}<small className="stats-sample">#{q.questionId} · Phiên bản {q.versionNo}</small></td><td>{levelNames[q.difficulty]}</td><td>{q.first.incorrect}</td><td>{q.learners}</td><td><Sample metric={q.first} /></td></tr>)}</tbody></table></div>}<Pager page={page} pages={result.data.totalPages} total={result.data.totalElements} change={setPage} /></>}
  </section>;
}
