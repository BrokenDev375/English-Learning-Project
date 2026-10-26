import type { Answer, AnswerKey, PublicQuestion } from './types';
import { MatchingCards, OrderingWords } from './InteractiveAnswers';
import { gameNames } from './GameWorld';
import { GameKey } from './GameArtwork';

export default function AnswerFields({ question: q, value, onChange, disabled = false, prefix, legend, play = false, verified = false }: { question: PublicQuestion; value: Answer | null; onChange: (a: Answer | null) => void; disabled?: boolean; prefix: string; legend?: string; play?: boolean; verified?: boolean }) {
  let blank = 0;
  return <fieldset className={'answer-fields' + (play ? ' game-answer game-' + q.type.toLowerCase() : '') + (verified ? ' game-verified' : '')} disabled={disabled}><legend>{legend ?? q.prompt}</legend>
    {play && <div className="game-task-title"><span aria-hidden="true">✦</span><strong>{gameNames[q.type]}</strong><span>{verified ? 'Đã vượt thử thách' : 'Thử thách của bạn'}</span></div>}
    {q.type === 'SINGLE_CHOICE' && <div className="answer-options">{q.content.options!.map((o, i) => <label key={o.id} className={value?.type === 'SINGLE_CHOICE' && value.optionId === o.id ? 'selected' : ''}><input type="radio" name={prefix} value={o.id} checked={value?.type === 'SINGLE_CHOICE' && value.optionId === o.id} onChange={() => onChange({ type: 'SINGLE_CHOICE', optionId: o.id })} /><b aria-hidden="true">{String.fromCharCode(65 + i)}</b>{play && <span className="game-gate" aria-hidden="true"><span /></span>}<span>{o.text}</span></label>)}</div>}
    {play && q.type === 'FILL_BLANK' && <p className="word-lock-message"><GameKey />{verified ? 'Khóa đã mở. Bạn tìm đúng từ rồi!' : 'Tìm từ còn thiếu để mở khóa. Nộp bài để kiểm tra chìa khóa của bạn.'}</p>}
    {q.type === 'FILL_BLANK' && <div className="answer-fill">{q.content.parts!.map((p, i) => p.kind === 'TEXT' ? <span key={i}>{p.text}</span> : <label key={p.blankId}><span className="sr-only">Trả lời ô trống {++blank}</span><input maxLength={200} autoComplete="off" value={value?.type === 'FILL_BLANK' ? value.values.find(v => v.blankId === p.blankId)?.value ?? '' : ''} onChange={e => {
      const values = value?.type === 'FILL_BLANK' ? value.values.filter(v => v.blankId !== p.blankId) : [];
      onChange({ type: 'FILL_BLANK', values: [...values, { blankId: p.blankId, value: e.target.value }] });
    }} /></label>)}</div>}
    {q.type === 'MATCHING' && <MatchingCards play={play} verified={verified} left={q.content.leftItems!} right={q.content.rightItems!} pairs={value?.type === 'MATCHING' ? value.pairs : []} disabled={disabled} onChange={pairs => onChange({ type: 'MATCHING', pairs })} />}
    {q.type === 'ORDERING' && <OrderingWords play={play} verified={verified} tokens={q.content.tokens!} tokenIds={value?.type === 'ORDERING' ? value.tokenIds : []} disabled={disabled} onChange={tokenIds => onChange({ type: 'ORDERING', tokenIds })} />}
    <button className="link-button" type="button" onClick={() => onChange(null)}>Xóa câu trả lời</button>
  </fieldset>;
}
export function responseText(q: PublicQuestion, answer: Answer | null) {
  if (!answer) return 'Chưa trả lời';
  if (answer.type === 'SINGLE_CHOICE') return q.content.options?.find(o => o.id === answer.optionId)?.text ?? 'Chưa chọn';
  if (answer.type === 'FILL_BLANK') return q.content.parts?.filter(p => p.kind === 'BLANK').map(p => p.kind === 'BLANK' ? answer.values.find(v => v.blankId === p.blankId)?.value || '…' : '').join(' / ') ?? '';
  if (answer.type === 'ORDERING') return answer.tokenIds.map(id => q.content.tokens?.find(t => t.id === id)?.text ?? '…').join(' ');
  return q.content.leftItems?.map(l => l.text + ' → ' + (q.content.rightItems?.find(r => r.id === answer.pairs.find(p => p.leftId === l.id)?.rightId)?.text ?? '…')).join('; ') ?? '';
}
export function KeyExplanation({ question: q, answerKey: key, explanation }: { question: PublicQuestion; answerKey: AnswerKey; explanation: string }) {
  const answer: Answer = q.type === 'SINGLE_CHOICE' ? { type: q.type, optionId: key.correctOptionId ?? null } : q.type === 'MATCHING' ? { type: q.type, pairs: key.pairs ?? [] } : q.type === 'ORDERING' ? { type: q.type, tokenIds: key.acceptedOrders?.[0] ?? [] } : { type: q.type, values: (key.blanks ?? []).map(b => ({ blankId: b.blankId, value: b.acceptedAnswers.join(' / ') })) };
  return <div className="key-explanation"><strong>Đáp án: {responseText(q, answer)}</strong><p>{explanation}</p></div>;
}
