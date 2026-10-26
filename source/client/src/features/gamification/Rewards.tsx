import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage, requestId } from '../../api';
import { navigate } from '../../navigation';
import './rewards.css';

interface Summary {
  serverTime: string; revision: number; totalXp: number; activityLevel: number; levelFloorXp: number; nextLevelAtXp: number;
  day: { date: string; awardedXp: number; capXp: number; isActive: boolean };
  streak: { current: number; best: number }; badges: { earnedCount: number; totalCount: number };
  backlog: { pendingSubmissions: number; deferredSubmissions: number }; processingState: string;
}
interface Badge { code: string; name: string; description: string; earnedAt: string | null; progress: number; target: number }
interface Decision { rewardType: string; attemptQuestionId: number | null; objectiveId: number | null; requestedXp: number; awardedXp: number; reason: string }
interface Activity { submissionId: number; attemptId: number; mode: string; submittedAt: string; disposition: string; awardedXp: number; qualifiedActivity: boolean; decisions: Decision[]; league: { countedXp: number; reason: string } }
interface ActivityPage { items: Activity[]; totalElements: number; page: number; totalPages: number; requestedSubmission: { state: string } | null }
const reasons: Record<string, string> = {
  AWARDED: 'Đủ điều kiện', DAILY_CAP_REACHED: 'Đã đạt giới hạn XP trong ngày', ALREADY_CLAIMED: 'Nguồn này đã được xét thưởng',
  ALREADY_REWARDED_TODAY: 'Mục tiêu đã nhận thưởng ôn trong ngày', SEEN_BEFORE: 'Câu đã gặp trước lượt này',
  GUIDED: 'Câu có hướng dẫn', SUPPORTED: 'Đã dùng hỗ trợ', FIRST_NOT_CORRECT: 'Lần tự làm đầu chưa đúng', INCOMPLETE_FIRST: 'Lần đầu chưa trả lời đủ',
  NO_REVIEW_CONTEXT: 'Lượt chưa có bằng chứng mốc ôn', REVIEW_NOT_DUE: 'Chưa có lịch ôn đến hạn đủ điều kiện',
  REVIEW_OBJECTIVE_NOT_PASSED: 'Cần tự làm đúng đầy đủ phần ôn của mục tiêu', BEFORE_ACTIVATION: 'Bài nộp trước khi bật phần thưởng',
};
const date = (value: string) => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
const leagueReasons: Record<string, string> = { COUNTED: 'Đã tính vào giải đấu tuần', NOT_JOINED: 'Chưa tham gia giải đấu tại lúc xét thưởng', BEFORE_JOIN: 'Bài nộp trước khi tham gia tuần', LATE_AFTER_CUTOFF: 'Xét thưởng sau mốc khóa điểm tuần; XP cá nhân vẫn được giữ', NO_AWARDED_XP: 'Lượt này không có XP mới để tính vào tuần' };
const changed = () => window.dispatchEvent(new Event('gamification:changed'));

function useSummary() {
  const [summary, setSummary] = useState<Summary | null>(null), [error, setError] = useState('');
  useEffect(() => {
    let active = true, debounce: number | undefined;
    async function load() {
      try {
        const next = await api<Summary>('/api/me/gamification');
        if (active) { setSummary(old => old && (old.revision > next.revision || old.revision === next.revision && Date.parse(old.serverTime) > Date.parse(next.serverTime)) ? old : next); setError(''); }
      } catch (e) { if (active) setError(errorMessage(e)); }
    }
    const refresh = () => { window.clearTimeout(debounce); debounce = window.setTimeout(() => void load(), 200); };
    void load(); const interval = window.setInterval(() => void load(), 15000);
    window.addEventListener('gamification:changed', refresh);
    return () => { active = false; window.clearInterval(interval); window.clearTimeout(debounce); window.removeEventListener('gamification:changed', refresh); };
  }, []);
  return { summary, error };
}

function Stats({ value }: { value: Summary }) {
  const progress = Math.max(0, Math.min(100, (value.totalXp - value.levelFloorXp) / Math.max(1, value.nextLevelAtXp - value.levelFloorXp) * 100));
  return <div className="reward-stats">
    <article className="panel reward-level"><span className="eyebrow">CẤP ĐỘ HOẠT ĐỘNG</span><div className="reward-number">{value.activityLevel}<span>{value.totalXp.toLocaleString('vi-VN')} XP tích lũy</span></div>
      <progress aria-label="Tiến độ cấp độ hoạt động" value={progress} max={100} /><small>Còn {Math.max(0, value.nextLevelAtXp - value.totalXp)} XP đến cấp tiếp theo</small><small>Mỗi {value.nextLevelAtXp - value.levelFloorXp} XP tăng 1 cấp</small></article>
    <article className="panel"><span className="eyebrow">CHUỖI NGÀY HỌC</span><div className="reward-number">{value.streak.current}<span>ngày liên tiếp</span></div><small>Kỷ lục: {value.streak.best} ngày · {value.day.isActive ? 'Hôm nay đã có hoạt động hợp lệ' : 'Học thêm hôm nay để giữ chuỗi'}</small></article>
    <article className="panel"><span className="eyebrow">XP HÔM NAY</span><div className="reward-number">{value.day.awardedXp}<span>/ {value.day.capXp} XP</span></div><progress aria-label="Giới hạn XP hôm nay" value={value.day.awardedXp} max={value.day.capXp} /><small>Ngày học theo giờ Việt Nam</small></article>
  </div>;
}

export function RewardStrip() {
  const { summary, error } = useSummary();
  if (!summary) return error ? <p className="muted">Chưa tải được phần thưởng. <button className="link-button" onClick={changed}>Thử lại</button></p> : null;
  return <div className="reward-strip panel"><div><strong>{summary.totalXp} XP</strong><span>Cấp hoạt động {summary.activityLevel}</span></div><div><strong>{summary.streak.current} ngày</strong><span>Chuỗi học liên tiếp</span></div><div><strong>{summary.badges.earnedCount}/{summary.badges.totalCount}</strong><span>Huy hiệu đã đạt</span></div>
    <div><strong>{summary.day.awardedXp}/{summary.day.capXp}</strong><span>XP hôm nay</span></div><div className="reward-strip-links"><button className="link-button" onClick={() => navigate({ view: 'rewards' }, { reset: true })}>Xem phần thưởng →</button><button className="link-button" onClick={() => navigate({ view: 'league' }, { reset: true })}>Thi đua tuần →</button></div></div>;
}

function Details({ activity }: { activity: Activity }) {
  if (activity.disposition === 'BEFORE_ACTIVATION') return <p className="muted">Bài nộp trước khi bật phần thưởng nên không được cộng XP hồi tố. Bạn có thể bắt đầu lượt học hoặc luyện tập mới.</p>;
  return <details className="reward-details"><summary>Xem cách tính {activity.awardedXp} XP</summary><ul>{activity.decisions.map((decision, i) => <li key={i}>
    <span>{decision.rewardType === 'DUE_REVIEW' ? 'Ôn mục tiêu đến hạn' : 'Câu học tập'} · {reasons[decision.reason] ?? decision.reason}</span><strong>+{decision.awardedXp} XP</strong>
  </li>)}</ul><p className="muted">{activity.qualifiedActivity ? 'Lượt này được tính là hoạt động học trong ngày.' : 'Lượt này chưa đủ điều kiện tính ngày học liên tiếp.'}</p><p className="muted">XP giải đấu: +{activity.league.countedXp} · {leagueReasons[activity.league.reason] ?? activity.league.reason}</p></details>;
}

export default function RewardsDashboard() {
  const { summary, error: summaryError } = useSummary();
  const [badges, setBadges] = useState<Badge[]>([]), [history, setHistory] = useState<ActivityPage | null>(null);
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef<string | null>(null), mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const reload = () => setRefresh(v => v + 1);
    const interval = window.setInterval(reload, 15000); window.addEventListener('gamification:changed', reload);
    return () => { window.clearInterval(interval); window.removeEventListener('gamification:changed', reload); };
  }, []);
  useEffect(() => {
    let active = true;
    void Promise.all([api<{ items: Badge[] }>('/api/me/badges'), api<ActivityPage>(`/api/me/gamification/activities?page=${page}&size=10`)])
      .then(([b, h]) => { if (active) { setBadges(b.items); setHistory(h); setError(''); } }).catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [page, refresh]);
  async function synchronize() {
    if (busy) return; setBusy(true); setError('');
    const key = pending.current ?? requestId(); pending.current = key;
    try { await api('/api/me/gamification/sync', 'POST', { requestId: key }); pending.current = null; if (mounted.current) changed(); }
    catch (e) { if (mounted.current) setError(errorMessage(e)); }
    finally { if (mounted.current) setBusy(false); }
  }
  return <section className="rewards-page"><div className="reward-heading"><div><span className="eyebrow">MỖI NỖ LỰC ĐỀU CÓ Ý NGHĨA</span><h1>Phần thưởng học tập<span>.</span></h1><p className="muted">Tích lũy từ việc tự làm và ôn tập đều đặn. Cấp độ hoạt động không phải trình độ tiếng Anh.</p></div><button className="button secondary" disabled={busy} onClick={() => void synchronize()}>{busy ? 'Đang cập nhật…' : pending.current ? 'Gửi lại cập nhật' : 'Cập nhật phần thưởng'}</button></div>
    {(error || summaryError) && <div className="message error" role="alert">{error || summaryError}</div>}
    {summary ? <Stats value={summary} /> : <p role="status">Đang tải phần thưởng…</p>}
    {summary && summary.backlog.pendingSubmissions > 0 && <p className="message warning" role="status">Có {summary.backlog.pendingSubmissions} bài nộp đang chờ xét thưởng. Kết quả sẽ tự cập nhật.</p>}
    {summary && summary.backlog.deferredSubmissions > 0 && <p className="message warning" role="status">Có bài nộp cần xử lý lại. Phần thưởng đã ghi nhận vẫn được giữ nguyên.</p>}
    <div className="panel reward-rules"><strong>Cách nhận XP</strong><p>Tự làm đúng câu mới: +5 XP/câu. Ôn đúng đầy đủ mục tiêu đến hạn: +5 XP/mục tiêu. Tối đa 200 XP/ngày. Câu hướng dẫn, câu đã gặp và chữa sau nộp không có thưởng câu mới.</p><button className="link-button" onClick={() => navigate({ view: 'learning' }, { reset: true })}>Bắt đầu học hoặc ôn tập →</button></div>
    <div className="panel reward-rules"><strong>Giải đấu tuần</strong><p>Chủ động tham gia nhóm cùng bậc và thi đua bằng XP mới sau khi tham gia.</p><button className="link-button" onClick={() => navigate({ view: 'league' }, { reset: true })}>Xem giải đấu tuần →</button></div>
    <h2>Huy hiệu của bạn</h2><div className="reward-badges">{badges.map((badge, i) => <article key={badge.code} className={'panel reward-badge ' + (badge.earnedAt ? 'earned' : '')}><span className="badge-symbol" aria-hidden="true">{['✦', '◷', '♧'][i]}</span><h3>{badge.name}</h3><p>{badge.description}</p><progress aria-label={'Tiến độ ' + badge.name} value={badge.progress} max={badge.target} /><small>{badge.earnedAt ? 'Đã đạt · ' + date(badge.earnedAt) : `${badge.progress}/${badge.target} · Đang tiến tới`}</small></article>)}</div>
    <div className="panel reward-history"><h2>Lịch sử phần thưởng</h2>{history && !history.items.length ? <p className="muted">Chưa có bài nộp được xét thưởng. Bắt đầu một lượt học hoặc luyện tập để ghi dấu tiến bộ.</p> : history?.items.map(activity => <article key={activity.submissionId}><div className="reward-history-head"><div><strong>{activity.mode === 'LEARN' ? 'Học bài' : activity.mode === 'REVIEW' ? 'Ôn tập' : 'Luyện tập'} · lượt #{activity.attemptId}</strong><small>{date(activity.submittedAt)}</small></div><strong className="xp-award">+{activity.awardedXp} XP</strong></div><Details activity={activity} /></article>)}
      <div className="pagination"><span>{history?.totalElements ?? 0} bài nộp đã xét</span><div><button className="button secondary" disabled={page === 0} onClick={() => setPage(v => v - 1)}>Trang trước</button><button className="button secondary" disabled={!history || page + 1 >= history.totalPages} onClick={() => setPage(v => v + 1)}>Trang sau</button></div></div>
    </div>
  </section>;
}

export function AttemptRewards({ submissionId }: { submissionId: number }) {
  const [response, setResponse] = useState<ActivityPage | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const key = useRef<string | null>(null), alive = useRef(true), inFlight = useRef(false);
  const read = useCallback(async () => {
    try { const next = await api<ActivityPage>(`/api/me/gamification/activities?submissionId=${submissionId}`); if (alive.current) { setResponse(old => old?.items.length ? old : next); setError(''); } }
    catch (e) { if (alive.current) setError(errorMessage(e)); }
  }, [submissionId]);
  const synchronize = useCallback(async () => {
    if (inFlight.current) return; inFlight.current = true; if (alive.current) setBusy(true);
    const request = key.current ?? requestId(); key.current = request;
    try { await api('/api/me/gamification/sync', 'POST', { requestId: request }); key.current = null; if (alive.current) { changed(); await read(); } }
    catch (e) { if (alive.current) setError(errorMessage(e)); }
    finally { inFlight.current = false; if (alive.current) setBusy(false); }
  }, [read]);
  useEffect(() => { alive.current = true; void synchronize(); return () => { alive.current = false; }; }, [synchronize]);
  const pending = !response || ['PENDING', 'DEFERRED'].includes(response.requestedSubmission?.state ?? 'PENDING');
  useEffect(() => { if (!pending) return; const timer = window.setInterval(() => void read(), 3000); return () => window.clearInterval(timer); }, [pending, read]);
  const activity = response?.items[0];
  return <div className="panel attempt-rewards" aria-label="Phần thưởng lượt làm"><div className="reward-history-head"><div><span className="eyebrow">PHẦN THƯỞNG HỌC TẬP</span><h2>{activity ? `+${activity.awardedXp} XP` : 'Đang xét phần thưởng…'}</h2></div><button className="link-button" onClick={() => navigate({ view: 'rewards' }, { reset: true })}>Xem phần thưởng →</button></div>
    {activity && <Details activity={activity} />}{!activity && <p className="muted">Bài nộp đã được lưu. Phần thưởng sẽ được cập nhật khi xử lý xong.</p>}
    {error && <p className="message warning" role="alert">{error}</p>}{(pending || error) && <button className="button secondary" disabled={busy} onClick={() => void synchronize()}>{busy ? 'Đang cập nhật…' : 'Cập nhật phần thưởng'}</button>}
  </div>;
}
