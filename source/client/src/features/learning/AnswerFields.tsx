import type { Answer, AnswerKey, PublicQuestion } from './types';

export default function AnswerFields({ question: q, value, onChange, disabled = false, prefix, legend }: { question: PublicQuestion; value: Answer | null; onChange: (a: Answer | null) => void; disabled?: boolean; prefix: string; legend?: string }) {
  let blank = 0;
  return <fieldset className="answer-fields" disabled={disabled}><legend>{legend ?? q.prompt}</legend>
    {q.type === 'SINGLE_CHOICE' && <div className="answer-options">{q.content.options!.map((o, i) => <label key={o.id} className={value?.type === 'SINGLE_CHOICE' && value.optionId === o.id ? 'selected' : ''}><input type="radio" name={prefix} value={o.id} checked={value?.type === 'SINGLE_CHOICE' && value.optionId === o.id} onChange={() => onChange({ type: 'SINGLE_CHOICE', optionId: o.id })} /><b aria-hidden="true">{String.fromCharCode(65 + i)}</b><span>{o.text}</span></label>)}</div>}
    {q.type === 'FILL_BLANK' && <div className="answer-fill">{q.content.parts!.map((p, i) => p.kind === 'TEXT' ? <span key={i}>{p.text}</span> : <label key={p.blankId}><span className="sr-only">Trả lời ô trống {++blank}</span><input maxLength={200} autoComplete="off" value={value?.type === 'FILL_BLANK' ? value.values.find(v => v.blankId === p.blankId)?.value ?? '' : ''} onChange={e => {
      const values = value?.type === 'FILL_BLANK' ? value.values.filter(v => v.blankId !== p.blankId) : [];
      onChange({ type: 'FILL_BLANK', values: [...values, { blankId: p.blankId, value: e.target.value }] });
    }} /></label>)}</div>}
    {q.type === 'MATCHING' && <div className="answer-matching">{q.content.leftItems!.map((l, index) => <label key={l.id}><span>{l.text}</span><select aria-label={'Ghép câu trả lời ' + (index + 1)} value={value?.type === 'MATCHING' ? value.pairs.find(p => p.leftId === l.id)?.rightId ?? '' : ''} onChange={e => {
      // Matching is one-to-one. Choosing an occupied right item clears its previous pair.
      const pairs = value?.type === 'MATCHING' ? value.pairs.filter(p => p.leftId !== l.id && p.rightId !== e.target.value) : [];
      onChange({ type: 'MATCHING', pairs: e.target.value ? [...pairs, { leftId: l.id, rightId: e.target.value }] : pairs });
    }}><option value="">Chọn mục tương ứng</option>{q.content.rightItems!.map(r => <option key={r.id} value={r.id}>{r.text}</option>)}</select></label>)}</div>}
    <button className="link-button" type="button" onClick={() => onChange(null)}>Xóa câu trả lời</button>
  </fieldset>;
}
export function responseText(q: PublicQuestion, answer: Answer | null) {
  if (!answer) return 'Chưa trả lời';
  if (answer.type === 'SINGLE_CHOICE') return q.content.options?.find(o => o.id === answer.optionId)?.text ?? 'Chưa chọn';
  if (answer.type === 'FILL_BLANK') return q.content.parts?.filter(p => p.kind === 'BLANK').map(p => p.kind === 'BLANK' ? answer.values.find(v => v.blankId === p.blankId)?.value || '…' : '').join(' / ') ?? '';
  return q.content.leftItems?.map(l => l.text + ' → ' + (q.content.rightItems?.find(r => r.id === answer.pairs.find(p => p.leftId === l.id)?.rightId)?.text ?? '…')).join('; ') ?? '';
}
export function KeyExplanation({ question: q, answerKey: key, explanation }: { question: PublicQuestion; answerKey: AnswerKey; explanation: string }) {
  const answer: Answer = q.type === 'SINGLE_CHOICE' ? { type: q.type, optionId: key.correctOptionId ?? null } : q.type === 'MATCHING' ? { type: q.type, pairs: key.pairs ?? [] } : { type: q.type, values: (key.blanks ?? []).map(b => ({ blankId: b.blankId, value: b.acceptedAnswers.join(' / ') })) };
  return <div className="key-explanation"><strong>Đáp án: {responseText(q, answer)}</strong><p>{explanation}</p></div>;
}
