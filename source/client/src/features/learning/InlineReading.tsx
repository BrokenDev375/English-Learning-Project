import type { Answer, Item, ReadingPassage } from './types';
import './reading.css';

export default function InlineReading({ passage, items, answerOf, onChange, onSelect, activeId, disabled, editableId }: {
  passage: ReadingPassage; items: Pick<Item, 'attemptQuestionId'>[]; answerOf: (id: number) => Answer | null;
  onChange: (id: number, answer: Answer) => void; onSelect: (id: number) => void;
  activeId: number; disabled: boolean; editableId?: number;
}) {
  return <div className="reading-text inline-reading" aria-label="Đoạn đọc điền từ">{passage.parts.map((part, i) => {
    if (part.kind === 'TEXT') return <span key={i}>{part.text}</span>;
    const item = items.find(q => q.attemptQuestionId === part.attemptQuestionId);
    if (!item) return <span key={i}>____</span>;
    const response = answerOf(item.attemptQuestionId);
    const value = response?.type === 'FILL_BLANK' ? response.values.find(v => v.blankId === part.blankId)?.value ?? '' : '';
    const number = items.indexOf(item) + 1;
    return <label key={i} className={'inline-reading-blank' + (activeId === item.attemptQuestionId ? ' active' : '')}>
      <span aria-hidden="true">({number})</span><span className="sr-only">Điền từ câu {number}</span>
      <input id={'reading-blank-' + item.attemptQuestionId} aria-label={'Điền từ câu ' + number} maxLength={200} autoComplete="off" value={value}
        disabled={disabled} readOnly={editableId !== undefined && editableId !== item.attemptQuestionId}
        aria-current={activeId === item.attemptQuestionId ? 'step' : undefined}
        onFocus={() => onSelect(item.attemptQuestionId)} onChange={e => onChange(item.attemptQuestionId, { type: 'FILL_BLANK', values: [{ blankId: part.blankId, value: e.target.value }] })} />
    </label>;
  })}</div>;
}
