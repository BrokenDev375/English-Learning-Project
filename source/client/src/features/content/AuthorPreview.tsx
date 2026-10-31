import type { LessonData, QuestionVersion, MaterialVersion } from './authoring';
import { activityKinds, practiceRoles } from './authoring';
import { useState } from 'react';
import type { Answer } from '../learning/types';
import InlineReading from '../learning/InlineReading';
import MaterialPresentation from './MaterialPresentation';
import QuestionPresentation from './QuestionPresentation';
export function Instructions({ lesson }: { lesson: Pick<LessonData, 'instructions'> }) { return <div className="lesson-instructions">{lesson.instructions.map((block, i) => <div key={i} className={block.kind === 'EXAMPLE' ? 'example-block' : ''}><p>{block.text}</p>{block.kind === 'EXAMPLE' && block.translation && <p className="muted">{block.translation}</p>}</div>)}</div>; }
export default function AuthorPreview({ lesson, questions, materials }: { lesson?: LessonData; questions: QuestionVersion[]; materials: MaterialVersion[] }) {
  const [answers, setAnswers] = useState<Record<number, Answer | null>>({}), [active, setActive] = useState(0);
  const order = lesson?.activities?.flatMap(a => a.questionVersionIds) ?? [];
  const slots = lesson ? order.length ? [...order.map(id => lesson.questions.find(s => s.questionVersionId === id)).filter(s => !!s), ...lesson.questions.filter(s => s.practiceRole === 'REVIEW_VARIANT')] : lesson.questions : [];
  const items = slots.map(s => ({ attemptQuestionId: s.questionVersionId }));
  return <div className="author-preview"><p className="muted">Tương tác thử; đáp án, gợi ý và bản chép lời được ẩn trong phần xem trước này.</p>{lesson && <><h2>{lesson.title}</h2><Instructions lesson={lesson} />{order.length > 0 && <p>{order.length} câu học · {slots.filter(s => s.practiceRole === 'GUIDED').length} hướng dẫn · {slots.filter(s => s.practiceRole === 'INDEPENDENT').length} tự làm · {lesson.questions.filter(s => s.practiceRole === 'REVIEW_VARIANT').length} câu ôn riêng</p>}</>}{(lesson ? slots.map(slot => ({ slot, question: questions.find(q => q.id === slot.questionVersionId) })) : questions.map(question => ({ question, slot: null }))).map(({ question: q, slot }, i) => {
    if (!q) return <div key={i} className="message error">Không tải được phiên bản câu hỏi. Hãy mở lại bài để kiểm tra.</div>;
    const material = materials.find(m => m.id === q.materialVersionId);
    const activity = lesson?.activities?.find(a => a.questionVersionIds.includes(q.id));
    const passage = activity?.passages.find(p => p.materialVersionId === q.materialVersionId);
    const firstInReading = !activity?.kind.startsWith('READING_') || activity.questionVersionIds.find(id => questions.find(q => q.id === id)?.materialVersionId === q.materialVersionId) === q.id;
    return <div key={q.id} className="preview-question">{activity?.questionVersionIds[0] === q.id && <h3>{activityKinds[activity.kind]}</h3>}{slot && <span className="eyebrow">{practiceRoles[slot.practiceRole]} · Câu {i + 1}</span>}{material && firstInReading && <MaterialPresentation material={material} readingContent={passage ? <InlineReading passage={{ materialVersionId: passage.materialVersionId, parts: passage.parts.map(p => p.kind === 'TEXT' ? p : { kind: 'BLANK', attemptQuestionId: p.questionVersionId, blankId: p.blankId }) }} items={items} answerOf={id => answers[id] ?? null} onChange={(id, a) => setAnswers(v => ({ ...v, [id]: a }))} activeId={active} onSelect={setActive} disabled={false} /> : undefined} />}{passage ? <p>{q.prompt} — nhập ở ô tương ứng trong đoạn.</p> : <QuestionPresentation key={q.id + ':' + q.revision} question={q} />}</div>;
  })}</div>;
}
