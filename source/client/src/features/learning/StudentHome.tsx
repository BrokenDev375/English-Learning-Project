import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate } from '../../navigation';
import type { User } from '../../types';
import type { CatalogPage } from '../content/catalog';
import type { LearnerOverview } from '../statistics/types';
import { RewardStrip } from '../gamification/Rewards';
import type { Snapshot, Summary } from './types';
import { modeNames } from './types';
import { recommendedRound, RoundMeter } from './journey';
import type { RoundProgress } from './journey';
import './journey.css';
import { TreasureChest } from './GameArtwork';
import './gameplay.css';

interface HomeData { overview: LearnerOverview; active: Summary | null; snapshot: Snapshot | null; rounds: CatalogPage<RoundProgress>; next: RoundProgress | null }
const openTab = (tab: string) => navigate({ view: 'learning', tab }, { reset: true });
const openReview = () => openTab('review');
function openRound(round: RoundProgress, lesson = false) { navigate({ view: 'learning', tab: 'catalog', unit: String(round.unit.id), lesson: lesson && round.nextLessonId ? String(round.nextLessonId) : null }, { reset: true }); }

export default function StudentHome({ user }: { user: User }) {
  const [data, setData] = useState<HomeData | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(true), [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let live = true; setLoading(true); setError('');
    async function load() {
      const [overview, attempts, rounds] = await Promise.all([
        api<LearnerOverview>('/api/me/statistics?period=ALL'),
        api<CatalogPage<Summary>>('/api/attempts?state=IN_PROGRESS&size=1'),
        api<CatalogPage<RoundProgress>>('/api/me/rounds?size=6'),
      ]);
      const active = attempts.items[0] ?? null;
      const [snapshot, next] = await Promise.all([active ? api<Snapshot>('/api/attempts/' + active.attemptId) : Promise.resolve(null), recommendedRound(rounds)]);
      if (live) setData({ overview, active: snapshot?.state === 'IN_PROGRESS' ? active : null, snapshot, rounds, next });
    }
    void load().catch(e => { if (live) setError(errorMessage(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [user.id, refresh]);
  useEffect(() => {
    const changed = () => setRefresh(r => r + 1);
    window.addEventListener('gamification:changed', changed); window.addEventListener('review-changed', changed); window.addEventListener('learning-recover', changed); window.addEventListener('learning-progress-changed', changed);
    return () => { window.removeEventListener('gamification:changed', changed); window.removeEventListener('review-changed', changed); window.removeEventListener('learning-recover', changed); window.removeEventListener('learning-progress-changed', changed); };
  }, []);
  const review = data?.overview.review;
  const resume = () => { if (data?.active) navigate({ view: 'learning', tab: 'history', attempt: String(data.active.attemptId) }, { reset: true }); };
  const action = data?.active ? resume : data?.next ? () => openRound(data.next!) : () => openTab('catalog');
  const actionLabel = data?.active ? data.active.mode === 'LEARN' ? 'Chơi tiếp bài đang làm' : data.active.mode === 'PRACTICE' ? 'Tiếp tục lượt cũ' : 'Tiếp tục ôn tập' : data?.next ? 'Vào chơi vòng ' + data.next.number : 'Mở bản đồ vòng chơi';
  const title = data?.active ? data.snapshot?.lesson?.lesson.title ?? modeNames[data.active.mode] : data?.next ? 'Vòng ' + data.next.number + ' · ' + data.next.unit.title : 'Sẵn sàng cho thử thách tiếp theo?';
  return <section className="student-home">
    <div className="learning-heading"><div><span className="eyebrow">HỌC TIẾNG ANH QUA TỪNG VÒNG CHƠI</span><h1>Chào {user.fullName.split(' ').at(-1)}<span>.</span></h1><p className="muted">Chơi từng vòng, hoàn thành các bài và mở khóa thử thách tiếp theo.</p></div><button className="button secondary" disabled={loading} onClick={() => setRefresh(r => r + 1)}>Cập nhật trang chủ</button></div>
    {error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(r => r + 1)}>Thử lại</button></div>}
    {loading && !data && <p role="status">Đang tải hành trình học tập…</p>}
    {data && <>
      <article className="journey-hero game-hero"><div><span className="eyebrow">{data.active ? 'TIẾP TỤC TỪ NƠI BẠN DỪNG' : 'SẴN SÀNG CHƠI CHƯA?'}</span><h2>{title}</h2>
        <p>{data.active ? `${data.active.answeredCount}/${data.active.totalQuestions} câu đã trả lời trên máy chủ. Tiếp tục lượt đang làm của bạn.` : data.next ? 'Săn kho báu qua trò ghép thẻ, chọn cổng và tìm từ. Làm đủ các bài để mở vòng tiếp theo; xem đáp án sau khi nộp.' : 'Mở bản đồ để xem hành trình hoặc chơi lại các vòng đã hoàn thành.'}</p>
        {data.next && !data.active && <RoundMeter {...data.next} />}
        <button className="button primary home-play-button" disabled={!!error} onClick={action}><span aria-hidden="true">▶</span> {actionLabel} →</button>
      </div><div className="game-hero-art" aria-hidden="true"><TreasureChest /><small>SĂN KHO BÁU</small></div></article>
      <div className="home-mode-cards" aria-label="Chọn cách học">
        <article className="panel"><span className="mode-symbol" aria-hidden="true">▶</span><h2>Chơi theo vòng</h2><p>Chơi săn kho báu trong từng bài. Vượt các vòng theo thứ tự để mở thử thách tiếp theo.</p><button className="link-button" onClick={() => openTab('catalog')}>Mở bản đồ vòng chơi →</button></article>
        <article className="panel"><span className="mode-symbol" aria-hidden="true">↻</span><h2>Ôn tập cá nhân</h2><p>{review?.needsReview ? `${review.needsReview} mục tiêu cần ôn. ` : ''}Ôn câu sai và nội dung đến hạn từ kết quả của bạn.</p><button className="link-button" onClick={openReview}>Xem nội dung cần ôn →</button></article>
      </div>
      <div className="journey-totals" aria-label="Tổng quan hành trình"><article><strong>{data.overview.activity.completedLessons}</strong><span>Bài đã hoàn thành</span></article><article><strong>{data.overview.activity.activeAttempts}</strong><span>Lượt đang làm</span></article><article><strong>{data.rounds.totalElements}</strong><span>Vòng trong hành trình</span></article></div>
      <RewardStrip />

    </>}
  </section>;
}
