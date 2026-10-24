import { useEffect, useRef, useState } from 'react';
import { api, errorMessage, requestId } from '../../api';
import { navigate } from '../../navigation';
import './rewards.css';

interface Result { rank: number; memberCount: number; activeCount: number; weeklyXp: number; tierBefore: string; tierAfter: string; outcome: string; holdReason: string; finalizedAt: string }
interface Season { id: number; startAt: string; endAt: string; cutoffAt: string; status: string; rules: { capacity: number; minimumMembers: number; minimumActive: number } }
interface Membership { memberKey: string; groupId: number; tier: string; joinedAt: string; weeklyXp: number; rank: number | null; projectedOutcome: string; result: Result | null }
interface Overview { serverTime: string; revision: number; state: string; canJoin: boolean; season: Season | null; membership: Membership | null }
interface Member { memberKey: string; displayName: string; isSelf: boolean; rank: number; weeklyXp: number; outcome: string }
interface Board { items: Member[]; self: Member; revision: number; provisional: boolean; totalElements: number; totalPages: number; groupStats: { memberCount: number; activeCount: number; movementEnabled: boolean; holdReason: string } }
interface History { items: { season: Season; groupId: number; result: Result }[]; totalElements: number; totalPages: number }
const tiers: Record<string, string> = { BRONZE: 'Đồng', SILVER: 'Bạc', GOLD: 'Vàng', PLATINUM: 'Bạch Kim', DIAMOND: 'Kim Cương' };
const outcomes: Record<string, string> = { KEEP: 'Giữ bậc', PROMOTE: 'Lên bậc', DEMOTE: 'Xuống bậc' };
const holds: Record<string, string> = { NONE: '', TOO_FEW_MEMBERS: 'Nhóm chưa đủ 10 thành viên để xét lên/xuống bậc.', TOO_FEW_ACTIVE: 'Nhóm cần ít nhất 5 người có XP để xét lên/xuống bậc.', TIED_BOUNDARY: 'Khối hòa điểm vắt ranh giới nên giữ bậc.', MINIMUM_TIER: 'Bạn đang ở bậc đầu tiên.', MAXIMUM_TIER: 'Bạn đang ở bậc cao nhất.' };
const date = (value: string) => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });

export default function LeagueDashboard() {
  const [overview, setOverview] = useState<Overview | null>(null), [board, setBoard] = useState<Board | null>(null), [history, setHistory] = useState<History | null>(null);
  const [page, setPage] = useState(0), [historyPage, setHistoryPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [timeLeft, setTimeLeft] = useState('');
  const pending = useRef<string | null>(null), inFlight = useRef(false), mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    if (!overview?.season) { setTimeLeft(''); return; }
    const receivedAt = performance.now(), remaining = Date.parse(overview.season.endAt) - Date.parse(overview.serverTime);
    const tick = () => {
      const seconds = Math.max(0, Math.ceil((remaining - (performance.now() - receivedAt)) / 1000));
      const days = Math.floor(seconds / 86400), hours = Math.floor(seconds % 86400 / 3600), minutes = Math.floor(seconds % 3600 / 60);
      setTimeLeft(seconds === 0 ? 'Đã hết thời gian thi đấu' : days ? `Còn ${days} ngày ${hours} giờ` : `Còn ${hours} giờ ${minutes} phút ${seconds % 60} giây`);
    };
    tick(); const timer = window.setInterval(tick, 1000); return () => window.clearInterval(timer);
  }, [overview?.serverTime, overview?.season?.endAt]);
  useEffect(() => {
    const reload = () => setRefresh(v => v + 1);
    const timer = window.setInterval(reload, 15000); window.addEventListener('league:changed', reload);
    return () => { window.clearInterval(timer); window.removeEventListener('league:changed', reload); };
  }, []);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const next = await api<Overview>('/api/me/league');
        const [results, ranking] = await Promise.all([
          api<History>(`/api/me/league/history?page=${historyPage}&size=5`),
          next.membership ? api<Board>(`/api/me/league/leaderboard?page=${page}&size=10`) : Promise.resolve(null),
        ]);
        if (active) { setOverview(next); setHistory(results); setBoard(ranking); setError(''); }
      } catch (e) { if (active) setError(errorMessage(e)); }
    }
    void load(); return () => { active = false; };
  }, [refresh, page, historyPage]);
  async function join() {
    if (inFlight.current) return; inFlight.current = true; setBusy(true); setError('');
    const key = pending.current ?? requestId(); pending.current = key;
    try {
      await api('/api/me/league/join', 'POST', { requestId: key }); pending.current = null;
      if (mounted.current) { setPage(0); setRefresh(v => v + 1); }
    } catch (e) { if (mounted.current) setError(errorMessage(e)); }
    finally { inFlight.current = false; if (mounted.current) setBusy(false); }
  }
  const member = overview?.membership, season = overview?.season;
  return <section className="rewards-page league-page">
    <div className="reward-heading"><div><span className="eyebrow">CÙNG NHAU GIỮ NHỊP HỌC</span><h1>Giải đấu tuần<span>.</span></h1><p className="muted">Thi đua bằng XP học tập. Mỗi tuần là một lần chủ động tham gia.</p></div><button className="button secondary" onClick={() => setRefresh(v => v + 1)}>Tải lại bảng</button></div>
    {error && <div className="message error" role="alert">{error}</div>}
    {!overview && !error && <p role="status">Đang tải giải đấu…</p>}
    {overview?.state === 'UNAVAILABLE' && <div className="panel league-intro"><h2>Tuần thi đấu đang được chuẩn bị</h2><p>Vui lòng tải lại sau ít giây.</p></div>}
    {season && <article className="panel league-intro"><div className="reward-history-head"><div><span className="eyebrow">TUẦN THI ĐẤU · GIỜ VIỆT NAM</span><h2>{member ? `Bậc ${tiers[member.tier]}` : 'Sẵn sàng cho tuần mới'}</h2></div><span className="league-state">{overview?.state === 'WAITING_PREVIOUS_SETTLEMENT' ? 'Đợi kết quả tuần trước' : member?.result ? 'Đã chốt kết quả' : season.status === 'OPEN' ? 'Đang diễn ra' : 'Đang chốt tuần'}</span></div>
      <p className="muted">{date(season.startAt)} → {date(season.endAt)}</p>
      <p className="muted" aria-label="Thời gian còn lại của tuần">{timeLeft}</p>
      {!member && <p>Tham gia nhóm tối đa {season.rules.capacity} người cùng bậc. Chỉ XP từ bài nộp sau khi tham gia được tính vào bảng tuần; tổng XP cá nhân vẫn được giữ.</p>}
      {overview?.state === 'WAITING_PREVIOUS_SETTLEMENT' && <p role="status">Nhóm tuần trước của bạn đang chờ chốt. Sau khi có kết quả, bạn có thể tham gia tuần mới.</p>}
      {(overview?.canJoin || pending.current) && <button className="button" disabled={busy} onClick={() => void join()}>{busy ? 'Đang tham gia…' : pending.current ? 'Kiểm tra lần tham gia' : 'Tham gia tuần này'}</button>}
      {member && <div className="league-self"><div><span>XP tuần của bạn</span><strong>{member.weeklyXp}</strong></div><div><span>Thứ hạng {member.result ? 'chính thức' : 'tạm thời'}</span><strong>#{member.rank ?? '—'}</strong></div><div><span>{member.result ? 'Kết quả' : 'Dự kiến'}</span><strong>{outcomes[member.projectedOutcome]}</strong></div></div>}
      {member?.result && <p>{holds[member.result.holdReason]} Bậc tiếp theo: <strong>{tiers[member.result.tierAfter]}</strong>.</p>}
      {member && !member.result && season.status !== 'OPEN' && <p role="status">Tuần đã đóng nhận tham gia. Bảng đang chờ chốt sau {date(season.cutoffAt)}.</p>}
    </article>}
    {board && <article className="panel league-board"><div className="reward-history-head"><h2>Bảng nhóm của bạn</h2><span className="muted">{board.groupStats.memberCount}/25 thành viên · {board.groupStats.activeCount} người có XP</span></div>
      <p className="muted">{board.provisional ? 'Thứ hạng tạm thời, có thể thay đổi đến khi chốt tuần.' : 'Kết quả chính thức đã được lưu.'} {holds[board.groupStats.holdReason]}</p>
      <div className="league-table-wrap"><table><thead><tr><th>Hạng</th><th>Học viên</th><th>XP tuần</th><th>{board.provisional ? 'Dự kiến' : 'Kết quả'}</th></tr></thead><tbody>{board.items.map(row => <tr key={row.memberKey} className={row.isSelf ? 'league-me' : ''}><td>#{row.rank}</td><td>{row.displayName}{row.isSelf && <small> · Bạn</small>}</td><td><strong>{row.weeklyXp}</strong></td><td>{outcomes[row.outcome]}</td></tr>)}</tbody></table></div>
      {!board.items.some(row => row.isSelf) && <p>Vị trí của bạn: #{board.self.rank} · {board.self.weeklyXp} XP · {outcomes[board.self.outcome]}</p>}
      <div className="pagination"><span>Trang {page + 1}/{Math.max(1, board.totalPages)}</span><div><button className="button secondary" disabled={page === 0} onClick={() => setPage(v => v - 1)}>Trang trước</button><button className="button secondary" disabled={page + 1 >= board.totalPages} onClick={() => setPage(v => v + 1)}>Trang sau</button></div></div>
      <button className="link-button" onClick={() => navigate({ view: 'learning' }, { reset: true })}>Học và tích lũy XP tuần →</button>
    </article>}
    <details className="panel league-rules"><summary>Luật xếp hạng và tính điểm</summary><p>Tuần bắt đầu thứ Hai lúc 00:00, theo giờ Việt Nam. Tối đa 25 người/nhóm; chỉ xét lên/xuống khi đủ 10 thành viên và 5 người có XP. Mỗi phía có tối đa 20% số thành viên.</p><p>Người bằng XP có cùng hạng. Khối hòa vắt ranh giới giữ bậc, không phân xử bằng thời điểm tham gia. Đồng không xuống thấp hơn; Kim Cương không lên cao hơn. Vắng tuần giữ bậc đã chốt gần nhất.</p><p>Bài nộp thuộc tuần cũ được xét thêm trong 2 phút sau hết tuần. Sau mốc đó, XP cá nhân hợp lệ vẫn được nhận nhưng bảng tuần cũ giữ nguyên. XP trước khi tham gia không được cộng ngược vào bảng.</p></details>
    <article className="panel reward-history"><h2>Lịch sử tuần của bạn</h2>{history && !history.items.length && <p className="muted">Chưa có tuần đã chốt. Kết quả sẽ xuất hiện ở đây sau khi nhóm hoàn tất.</p>}
      {history?.items.map(item => <article key={item.season.id}><div className="reward-history-head"><div><strong>{date(item.season.startAt)} · {tiers[item.result.tierBefore]}</strong><small>#{item.result.rank}/{item.result.memberCount} · {item.result.weeklyXp} XP</small></div><strong>{outcomes[item.result.outcome]} → {tiers[item.result.tierAfter]}</strong></div><p className="muted">{holds[item.result.holdReason]}</p></article>)}
      {history && history.totalPages > 1 && <div className="pagination"><span>{history.totalElements} tuần đã chốt</span><div><button className="button secondary" disabled={historyPage === 0} onClick={() => setHistoryPage(v => v - 1)}>Tuần mới hơn</button><button className="button secondary" disabled={historyPage + 1 >= history.totalPages} onClick={() => setHistoryPage(v => v + 1)}>Tuần cũ hơn</button></div></div>}
    </article>
  </section>;
}
