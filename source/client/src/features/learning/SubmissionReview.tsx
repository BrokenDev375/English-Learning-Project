import { navigate } from '../../navigation';
import { useEffect, useRef } from 'react';
import { KeyExplanation, responseText } from './AnswerFields';
import type { AttemptSession } from './session';
import type { Result, Snapshot } from './types';

export default function SubmissionReview({ result, snapshot, session, disabled }: { result: Result; snapshot: Snapshot; session: AttemptSession; disabled: boolean }) {
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, [session]);
  const eligible = result.items.length > 0 && result.items.every(item => item.finalResponseComplete);
  async function confirm() {
    const reply = await session.mutate('complete');
    if (active.current && reply && 'lessonId' in reply && reply.lessonCompleted) {
      navigate({ attempt: null, tab: 'catalog', unit: snapshot.lesson ? String(snapshot.lesson.lesson.unitId) : null, lesson: null });
    }
  }
  return <section className="panel learning-panel submission-review" aria-label="Đáp án và giải thích sau khi nộp">
    <h2>Đáp án và giải thích</h2><p className="muted">Xem lại toàn bộ bài đã nộp. Bạn có thể luyện sửa từng câu ở phần bên dưới.</p>
    <ol className="submission-review-items">{result.items.map((fact, i) => {
      const item = snapshot.items.find(q => q.attemptQuestionId === fact.attemptQuestionId)!;
      return <li key={fact.attemptQuestionId}><h3>Câu {i + 1}: {item.question.prompt}</h3><p><strong>Bạn đã trả lời:</strong> {responseText(item.question, fact.finalResponse)}</p><KeyExplanation question={item.question} answerKey={fact.answerKey} explanation={fact.explanation} /></li>;
    })}</ol>
    {result.mode === 'LEARN' && <div className="submission-review-complete">
      <p>{eligible ? 'Bạn đã trả lời đủ các câu. Sau khi xem đáp án, xác nhận để trở về vòng học.' : 'Bài nộp còn đáp án chưa đầy đủ. Bạn có thể xem lời giải và học lại để hoàn thành bài.'}</p>
      <button className="button primary" disabled={disabled || !eligible} onClick={() => void confirm()}>Xác nhận hoàn thành bài học</button>
    </div>}
  </section>;
}
