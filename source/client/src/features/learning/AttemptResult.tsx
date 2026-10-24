import { useState } from 'react';
import { navigate } from '../../navigation';
import type { AttemptSession } from './session';
import type { Answer } from './types';
import { complete, outcomeNames } from './types';
import AnswerFields, { KeyExplanation, responseText } from './AnswerFields';
import LearningMaterial from './LearningMaterial';
import { AttemptRewards } from '../gamification/Rewards';

export default function AttemptResult({ session, state }: { session: AttemptSession; state: ReturnType<AttemptSession['getSnapshot']> }) {
  const result = state.result!, snapshot = state.snapshot!;
  const [selected, setSelected] = useState(0), [responses, setResponses] = useState<Record<number, Answer | null>>({});
  const fact = result.items[selected], item = snapshot.items.find(i => i.attemptQuestionId === fact.attemptQuestionId)!;
  const response = Object.hasOwn(responses, item.attemptQuestionId) ? responses[item.attemptQuestionId] : fact.finalResponse;
  const feedback = state.feedback[item.attemptQuestionId];
  const disabled = state.busy || !!state.pending || !!state.conflict || state.loginRequired || state.loading;
  const material = snapshot.materials.find(m => m.materialVersionId === item.materialVersionId);
  return <div className="attempt-result">
    <AttemptRewards key={result.submissionId} submissionId={result.submissionId} />
    <div className="material-actions"><button className="button secondary" onClick={() => navigate({ attempt: null, tab: 'review', unit: null, lesson: null })}>Xem nội dung cần ôn</button></div>
    <div className="panel result-score"><div><span className="eyebrow">KẾT QUẢ ĐÃ NỘP</span><strong>{result.scorePercent.toFixed(2)}<small>/100</small></strong><p>{result.correctCount}/{result.totalQuestions} câu đúng · {result.unansweredCount} câu chưa trả lời</p></div><div><h2>{result.mode === 'LEARN' ? result.lessonCompleted ? 'Đã hoàn thành bài học' : 'Bài học chưa hoàn thành' : 'Đã hoàn tất lượt luyện tập'}</h2><p className="muted">Nộp lúc {new Date(result.submittedAt).toLocaleString('vi-VN')}. Điểm và câu trả lời khi nộp được giữ nguyên sau khi chữa.</p>{result.mode === 'LEARN' && !result.lessonCompleted && <p className="muted">Để hoàn thành bài, bắt đầu lượt học mới, trả lời đủ mọi câu và kiểm tra từng câu hướng dẫn trước khi nộp.</p>}</div></div>
    <div className="panel table-wrap"><table><caption className="sr-only">Kết quả lần đầu và khi nộp của từng câu</caption><thead><tr><th>Câu</th><th>Lần đầu</th><th>Khi nộp</th><th>Hỗ trợ ở lần đầu</th></tr></thead><tbody>{result.items.map((f, n) => <tr key={f.attemptQuestionId}><td><button className="link-button" onClick={() => setSelected(n)} aria-label={'Xem và chữa câu ' + (n + 1)}>Câu {n + 1}</button></td><td>{outcomeNames[f.firstOutcome]}{!f.firstResponseComplete && ' · chưa đủ'}</td><td>{outcomeNames[f.finalOutcome]}{!f.finalResponseComplete && ' · chưa đủ'}</td><td>{f.firstSupportUsed ? 'Có sử dụng' : 'Không sử dụng'}</td></tr>)}</tbody></table></div>
    <div className="runner-layout"><nav className="panel question-nav" aria-label="Chọn câu chữa bài">{result.items.map((f, n) => <button key={f.attemptQuestionId} aria-label={'Chữa câu ' + (n + 1)} aria-current={selected === n ? 'step' : undefined} className={selected === n ? 'current' : ''} onClick={() => setSelected(n)}>{n + 1}</button>)}</nav><div className="runner-main">
      {material && <LearningMaterial key={material.materialVersionId} material={material} />}
      <article className="panel learning-panel"><span className="eyebrow">CHỮA CÂU {selected + 1}</span><h2>{item.question.prompt}</h2>
        <p><strong>Lần đầu:</strong> {responseText(item.question, fact.firstResponse)} — {outcomeNames[fact.firstOutcome]}</p><p><strong>Khi nộp:</strong> {responseText(item.question, fact.finalResponse)} — {outcomeNames[fact.finalOutcome]}</p>
        <KeyExplanation question={item.question} answerKey={fact.answerKey} explanation={fact.explanation} />
        <AnswerFields key={item.attemptQuestionId} prefix={'correction-' + item.attemptQuestionId} question={item.question} legend="Câu trả lời chữa bài" value={response} disabled={state.loginRequired} onChange={a => setResponses(v => ({ ...v, [item.attemptQuestionId]: a }))} />
        <button className="button primary" disabled={disabled || !complete(item.question, response)} onClick={() => void session.mutate('retry', item.attemptQuestionId, response)}>Kiểm tra bài chữa</button>
        {feedback?.data.kind === 'CORRECTION' && <div className={'message ' + (feedback.data.outcome === 'CORRECT' ? 'success' : 'warning')} role="status"><strong>Bài chữa: {outcomeNames[feedback.data.outcome]}</strong><p>Đáp án đã chữa: {responseText(item.question, feedback.response)}</p><p>Điểm nộp vẫn là {result.scorePercent.toFixed(2)}/100.</p></div>}
      </article></div></div>
    {result.materialFeedback.some(m => m.transcript) && <div className="panel learning-panel"><h2>Bản chép lời</h2>{result.materialFeedback.filter(m => m.transcript).map(m => <details key={m.materialVersionId} open><summary>{snapshot.materials.find(v => v.materialVersionId === m.materialVersionId)?.title ?? 'Audio'}</summary><p className="reading-text">{m.transcript}</p></details>)}</div>}
  </div>;
}
