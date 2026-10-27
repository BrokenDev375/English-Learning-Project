import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { initials } from '../../components/Brand';
import { navigate, useRouteParam } from '../../navigation';
import LeagueDashboard from './League';
import './rewards.css';
import './leaderboard.css';

interface Member { position: number | null; rank: number | null; displayName: string; activityLevel: number; weeklyXp: number; isSelf: boolean }
interface Board { serverTime: string; zone: string; weekStartAt: string; weekEndAt: string; top: Member[]; items: Member[]; self: Member; totalElements: number; page: number; size: number; totalPages: number }
const number = (value: number) => value.toLocaleString('vi-VN');
const date = (value: string) => new Date(value).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric' });
function Trophy() {
  return <svg className="ranking-trophy" viewBox="0 0 80 80" aria-hidden="true"><path d="M21 15H10v12q0 18 23 18M59 15h11v12q0 18-23 18" fill="none" stroke="currentColor" strokeWidth="5" /><path d="M21 8h38v20q0 22-19 22T21 28zM36 49h8v14h-8zM25 64h30v8H25z" fill="currentColor" /><path d="M40 17l4 9 10 1-7 7 2 10-9-5-9 5 2-10-7-7 10-1z" fill="#fff3c0" /></svg>;
}
function GlobalBoard() {
  const [data, setData] = useState<Board | null>(null), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [error, setError] = useState(''), [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true; setLoading(true);
    async function load() {
      try {
        const next = await api<Board>('/api/me/leaderboard?page=' + page + '&size=20');
        if (!active) return;
        if (page > 0 && page >= next.totalPages) { setPage(Math.max(0, next.totalPages - 1)); return; }
        setData(next); setError('');
      } catch (e) { if (active) setError(errorMessage(e)); }
      finally { if (active) setLoading(false); }
    }
    void load(); return () => { active = false; };
  }, [page, refresh]);
  useEffect(() => {
    const reload = () => setRefresh(value => value + 1);
    const timer = window.setInterval(reload, 15000); window.addEventListener('gamification:changed', reload);
    return () => { window.clearInterval(timer); window.removeEventListener('gamification:changed', reload); };
  }, []);
  return <div className="global-leaderboard">
    <div className="ranking-week-heading"><div><span className="eyebrow">XP TUẦN · TOÀN HỆ THỐNG</span><h2>Cùng tiến bộ mỗi ngày</h2>{data && <p className="muted">{date(data.weekStartAt)} → {date(data.weekEndAt)} · Tuần bắt đầu thứ Hai, 00:00 giờ Việt Nam</p>}</div><button type="button" className="button secondary" disabled={loading} onClick={() => setRefresh(value => value + 1)}>Cập nhật xếp hạng</button></div>
    {error && <div className="message error" role="alert">{error}{data && <p>Đang hiển thị dữ liệu lần tải thành công trước. Hãy cập nhật để xem thứ hạng mới.</p>}<button type="button" className="link-button" disabled={loading} onClick={() => setRefresh(value => value + 1)}>Thử tải lại bảng xếp hạng</button></div>}
    {!data && loading && <p role="status">Đang tải bảng xếp hạng…</p>}
    {data && <>
      <section className="ranking-self panel" aria-label="Vị trí của bạn"><div className="ranking-self-avatar" aria-hidden="true">{initials(data.self.displayName)}</div><div><span className="eyebrow">VỊ TRÍ CỦA BẠN</span><strong>{data.self.rank === null ? 'Chưa xếp hạng' : 'Hạng #' + number(data.self.rank)}</strong><span>{data.self.displayName} · Cấp {number(data.self.activityLevel)}</span></div><div className="ranking-self-xp"><strong>{number(data.self.weeklyXp)} XP</strong><span>đã nhận trong tuần</span></div></section>
      {data.top.length > 0 ? <section className="ranking-podium" aria-label="Top 3 học viên tuần này">{data.top.map(row => <article className={'podium-card podium-rank-' + row.rank + (row.isSelf ? ' podium-self' : '')} key={row.position}><span className="podium-place">Hạng #{row.rank}</span><Trophy /><span className="podium-avatar" aria-hidden="true">{initials(row.displayName)}</span><strong className="ranking-name">{row.displayName}{row.isSelf && <small> · Bạn</small>}</strong><span>Cấp {number(row.activityLevel)}</span><b>{number(row.weeklyXp)} XP</b></article>)}</section> : <section className="panel ranking-empty"><Trophy /><h2>Tuần mới, cùng bắt đầu nhé!</h2><p>Chưa có học viên nhận XP tuần này. Hoàn thành bài và nhận XP hợp lệ để xuất hiện trên bảng.</p><button type="button" className="button primary" onClick={() => navigate({ view: 'learning', tab: 'catalog' }, { reset: true })}>Vào chơi và nhận XP</button></section>}
      <section className="panel ranking-list" aria-label="Danh sách xếp hạng"><div className="ranking-list-heading"><h2>Bảng tuần này</h2><span className="muted">{number(data.totalElements)} học viên có XP</span></div>
        {data.items.length > 0 && <div className="table-wrap"><table><thead><tr><th scope="col">Hạng</th><th scope="col">Học viên</th><th scope="col">Cấp</th><th scope="col">XP tuần</th></tr></thead><tbody>{data.items.map(row => <tr key={row.position} className={row.isSelf ? 'ranking-me' : ''}><td><strong>#{number(row.rank!)}</strong></td><td><span className="ranking-name">{row.displayName}{row.isSelf && <small> · Bạn</small>}</span></td><td>{number(row.activityLevel)}</td><td><strong>{number(row.weeklyXp)} XP</strong></td></tr>)}</tbody></table></div>}
        {data.totalPages > 1 && <div className="pagination"><span>Trang {data.page + 1}/{data.totalPages}</span><div><button type="button" className="button secondary" disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Trang trước</button><button type="button" className="button secondary" disabled={loading || page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Trang sau</button></div></div>}
      </section>
      <details className="panel ranking-rules"><summary>Cách tính bảng xếp hạng chung</summary><p>Học viên đang hoạt động tự động có hạng khi nhận XP trong tuần. Bảng dùng XP đã được ghi nhận từ bài nộp hợp lệ, gồm cả XP trước khi tham gia nhóm thi đấu. Các bài đang chờ xử lý thưởng sẽ cập nhật sau khi xử lý xong.</p><p>Bằng XP thì đồng hạng, ví dụ 1, 1, 3. Thứ tự các dòng đồng hạng không tạo ưu tiên hoặc phần thưởng. Sang tuần mới, XP xếp hạng tính lại; tổng XP tích lũy và cấp của bạn vẫn giữ nguyên.</p><p>Bảng chung thể hiện tuần hiện tại và tự cập nhật khoảng 15 giây. Việc lên/xuống bậc thuộc tab Nhóm thi đấu.</p></details>
    </>}
  </div>;
}

export default function Leaderboards() {
  const [view] = useRouteParam('view'), [board] = useRouteParam('board');
  const selected = board === 'group' || view === 'league' && !board ? 'group' : 'global';
  function choose(next: string) { if (next !== selected) navigate({ view: 'leaderboard', board: next === 'group' ? 'group' : null }, { reset: true }); }
  return <section className="rewards-page leaderboards-page">
    <div className="reward-heading"><div><span className="eyebrow">HỌC ĐỀU · TIẾN BỘ CÙNG NHAU</span><h1>Bảng xếp hạng<span>.</span></h1><p className="muted">Tích lũy XP qua từng bài và theo dõi vị trí của bạn trong tuần.</p></div></div>
    <div className="ranking-tabs" role="tablist" aria-label="Loại bảng xếp hạng" onKeyDown={event => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault(); const next = event.key === 'Home' ? 'global' : event.key === 'End' ? 'group' : selected === 'global' ? 'group' : 'global';
      choose(next); requestAnimationFrame(() => document.getElementById('ranking-tab-' + next)?.focus());
    }}>{[['global', 'Toàn hệ thống'], ['group', 'Nhóm thi đấu']].map(([key, label]) => <button type="button" role="tab" id={'ranking-tab-' + key} key={key} aria-controls={'ranking-panel-' + key} aria-selected={selected === key} tabIndex={selected === key ? 0 : -1} onClick={() => choose(key)}>{label}</button>)}</div>
    <div role="tabpanel" id={'ranking-panel-' + selected} aria-labelledby={'ranking-tab-' + selected}>{selected === 'global' ? <GlobalBoard /> : <LeagueDashboard embedded />}</div>
  </section>;
}
