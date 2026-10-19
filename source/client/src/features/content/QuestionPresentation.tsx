import { useState } from 'react';
import type { QuestionData } from './authoring';
/** Author preview interactions intentionally expose no key, hint or explanation. */
export default function QuestionPresentation({ question: q }: { question: QuestionData }) {
  const [selected, setSelected] = useState(''), [values, setValues] = useState<Record<string, string>>({});
  let blank = 0;
  return <article className="question-presentation"><h3>{q.prompt || 'Câu hỏi chưa nhập nội dung'}</h3>
    {q.type === 'SINGLE_CHOICE' && <div className="preview-options">{q.content.options!.map((o, i) => <button key={o.id} type="button" className={selected === o.id ? 'selected' : ''} aria-pressed={selected === o.id} onClick={() => setSelected(o.id)}><b>{String.fromCharCode(65 + i)}</b><span>{o.text || 'Lựa chọn chưa nhập'}</span></button>)}</div>}
    {q.type === 'FILL_BLANK' && <div className="preview-fill">{q.content.parts!.map((p, i) => p.kind === 'TEXT' ? <span key={i}>{p.text}</span> : <input key={p.blankId} aria-label={'Trả lời ô trống ' + ++blank} value={values[p.blankId] ?? ''} onChange={e => setValues({ ...values, [p.blankId]: e.target.value })} />)}</div>}
    {q.type === 'MATCHING' && <div className="preview-matching">{q.content.leftItems!.map((o, i) => <label key={o.id}><span>{o.text || 'Mục trái ' + (i + 1)}</span><select aria-label={'Ghép câu trả lời ' + (i + 1)} value={values[o.id] ?? ''} onChange={e => setValues({ ...values, [o.id]: e.target.value })}><option value="">Chọn mục tương ứng</option>{q.content.rightItems!.map(r => <option key={r.id} value={r.id}>{r.text || 'Chưa nhập'}</option>)}</select></label>)}</div>}
  </article>;
}
