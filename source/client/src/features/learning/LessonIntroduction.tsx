import { useEffect, useState } from 'react';
import { errorMessage } from '../../api';
import { navigate, returnTo } from '../../navigation';
import type { Instruction } from '../content/authoring';
import { activityKinds } from '../content/authoring';
import type { ActivityKind } from '../content/authoring';
import type { Objective } from '../content/catalog';
import { Instructions } from '../content/AuthorPreview';
import { listAttempts, readAttempt } from './api';
import { TreasureChest } from './GameArtwork';
import './gameplay.css';
import type { Progress } from './types';

export interface Introduction {
  lesson: { id: number; unitId: number; lessonVersionId: number; title: string; description: string; questionCounts: { guided: number; independent: number; reviewVariant: number } };
  instructions: Instruction[];
  objectives: Objective[];
  activitySummary?: { kind: ActivityKind; questionCount: number }[];
}
interface Activity { lessonId: number; loading: boolean; active: boolean; latestResult: number | null; error: string }
const normalized = (text: string) => text.normalize('NFC').replace(/\s+/g, ' ').trim();

export default function LessonIntroduction({ intro, progress, progressReady, student, disabled, start, retryProgress }: {
  intro: Introduction; progress?: Progress; progressReady: boolean; student: boolean; disabled: boolean;
  start: (id: number) => void; retryProgress: () => void;
}) {
  const [activity, setActivity] = useState<Activity | null>(null), [refresh, setRefresh] = useState(0);
  const lessonId = intro.lesson.id;
  useEffect(() => {
    if (!student || !progressReady) return;
    let live = true;
    setActivity({ lessonId, loading: true, active: false, latestResult: null, error: '' });
    async function load() {
      const last = progress?.lastAttemptId ? await readAttempt(progress.lastAttemptId) : null;
      const active = last?.state === 'IN_PROGRESS';
      let latestResult: number | null = last?.state === 'SUBMITTED' ? last.attemptId : null;
      // A completed lesson can have a new unfinished attempt. Find its previous result by pinned lesson identity.
      if (active) {
        const versions = new Map<number, number>([[intro.lesson.lessonVersionId, lessonId]]);
        if (progress?.lastCompletedVersionId) versions.set(progress.lastCompletedVersionId, lessonId);
        if (last.lessonVersionId) versions.set(last.lessonVersionId, lessonId);
        let latestSubmitted = 0;
        resultPages: for (let page = 0; live; page++) {
          const rows = await listAttempts(new URLSearchParams({ mode: 'LEARN', state: 'SUBMITTED', size: '50', page: String(page) }));
          for (const row of rows.items) {
            if (!live) return;
            // Lists sort by updatedAt; correcting an old attempt must not make it the latest submission.
            if (Date.parse(row.updatedAt) < latestSubmitted) break resultPages;
            if (row.lessonVersionId === null) continue;
            let root = versions.get(row.lessonVersionId);
            if (root === undefined) {
              root = (await readAttempt(row.attemptId)).lesson?.lesson.id;
              if (root !== undefined) versions.set(row.lessonVersionId, root);
            }
            const submitted = Date.parse(row.submittedAt ?? row.updatedAt);
            if (root === lessonId && submitted >= latestSubmitted) { latestResult = row.attemptId; latestSubmitted = submitted; }
          }
          if (page + 1 >= rows.totalPages) break;
        }
      }
      if (live) setActivity({ lessonId, loading: false, active, latestResult, error: '' });
    }
    void load().catch(e => { if (live) setActivity({ lessonId, loading: false, active: false, latestResult: null, error: errorMessage(e) }); });
    return () => { live = false; };
  }, [student, progressReady, lessonId, intro.lesson.lessonVersionId, progress?.lastAttemptId, progress?.lastCompletedVersionId, refresh]);
  const current = activity?.lessonId === lessonId ? activity : null;
  const loading = student && (!progressReady || !current || current.loading);
  const total = intro.lesson.questionCounts.guided + intro.lesson.questionCounts.independent;
  const instructions = intro.instructions.filter(block => block.kind !== 'TEXT' || normalized(block.text) !== normalized(intro.lesson.description));
  const label = loading ? 'Đang kiểm tra lượt học…' : current?.active ? 'Tiếp tục làm' : current?.latestResult || progress?.state === 'COMPLETED' ? 'Chơi lại bài' : 'Bắt đầu chơi';
  return <div className="panel lesson-introduction">
    <div className="lesson-intro-overview"><p className="lesson-description">{intro.lesson.description}</p>
      <div className="lesson-facts" aria-label="Thông tin lượt học"><span>{total} câu</span><span>{intro.lesson.questionCounts.guided} câu có hướng dẫn</span><span>{intro.lesson.questionCounts.independent} câu tự làm</span></div>
      {!!intro.activitySummary?.length && <div className="lesson-intro-section"><h2>Các hoạt động trong bài</h2><ol>{intro.activitySummary.map(a => <li key={a.kind}>{activityKinds[a.kind]} · {a.questionCount} câu</li>)}</ol><p>Mỗi phần bắt đầu bằng một câu hướng dẫn; các câu còn lại tự làm. Các phần đọc nâng cao, nếu có, dùng đoạn ví dụ riêng.</p></div>}
      {progress && <div className="lesson-state"><span className={'lesson-state-badge ' + progress.state.toLowerCase()}><span aria-hidden="true">{progress.state === 'COMPLETED' ? '✓' : progress.state === 'STARTED' ? '◷' : '○'}</span><span>{progress.state === 'COMPLETED' ? 'Đã hoàn thành bài học' : progress.state === 'STARTED' ? 'Đã bắt đầu · Chưa hoàn thành' : 'Chưa bắt đầu'}</span></span>{progress.updateAvailable && <span className="lesson-update">Có phiên bản bài học mới</span>}</div>}
    </div>
    <div className="lesson-intro-section"><h2>Bạn sẽ học gì?</h2><ul>{intro.objectives.map(o => <li key={o.id}>{o.name}</li>)}</ul></div>
    <div className="game-intro"><TreasureChest small /><div><h2>Săn kho báu tiếng Anh</h2><p>Chọn cổng, tìm từ còn thiếu hoặc ghép thẻ theo câu hỏi của bài. Mỗi câu đúng mở một rương khi nộp bài. Bài có câu xếp từ sẽ đưa bạn đến thử thách xây cầu.</p></div></div>
    <div className="lesson-intro-section"><h2>Cách chơi</h2><p>Đọc, nghe hoặc xem học liệu đi kèm rồi trả lời {total} câu hỏi. Bạn có thể mở gợi ý và kiểm tra đáp án ở {intro.lesson.questionCounts.guided} câu có hướng dẫn. Kiểm tra trước khi nộp là tùy chọn. Nộp đủ đáp án để hoàn thành bài; sau đó xem lời giải và xác nhận về vòng học.</p><Instructions lesson={{ instructions }} /></div>
    {current?.error && <div className="message error" role="alert">{current.error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Tải lại trạng thái lượt học</button></div>}
    {student && !progressReady && <p className="muted" role="status">Đang tải tiến độ… <button className="link-button" onClick={retryProgress}>Tải lại tiến độ</button></p>}
    {!student && <p className="muted">Giáo viên và quản trị viên xem giới thiệu tại đây; xem trước câu hỏi trong mục quản lý học liệu.</p>}
    <div className="lesson-intro-actions">
      {student && <button className="button primary" disabled={disabled || loading || !!current?.error} onClick={() => start(lessonId)}>{label}</button>}
      {student && current?.latestResult && <button className="button secondary" disabled={disabled} onClick={() => navigate({ attempt: String(current.latestResult) })}>Xem kết quả gần nhất</button>}
      <button className="button secondary lesson-back" onClick={() => returnTo({ lesson: null })}>{student ? 'Về danh sách bài trong vòng' : 'Về danh sách bài trong vòng đấu'}</button>
    </div>
  </div>;
}
