import type { LessonData, QuestionVersion, MaterialVersion } from './authoring';
import { practiceRoles } from './authoring';
import MaterialPresentation from './MaterialPresentation';
import QuestionPresentation from './QuestionPresentation';
export function Instructions({ lesson }: { lesson: Pick<LessonData, 'instructions'> }) { return <div className="lesson-instructions">{lesson.instructions.map((block, i) => <div key={i} className={block.kind === 'EXAMPLE' ? 'example-block' : ''}><p>{block.text}</p>{block.kind === 'EXAMPLE' && block.translation && <p className="muted">{block.translation}</p>}</div>)}</div>; }
export default function AuthorPreview({ lesson, questions, materials }: { lesson?: LessonData; questions: QuestionVersion[]; materials: MaterialVersion[] }) {
  return <div className="author-preview"><p className="muted">Tương tác thử; đáp án, gợi ý và bản chép lời được ẩn trong phần xem trước này.</p>{lesson && <><h2>{lesson.title}</h2><Instructions lesson={lesson} /></>}{(lesson ? lesson.questions.map(slot => ({ slot, question: questions.find(q => q.id === slot.questionVersionId) })) : questions.map(question => ({ question, slot: null }))).map(({ question: q, slot }, i) => {
    if (!q) return <div key={i} className="message error">Không tải được phiên bản câu hỏi. Hãy mở lại bài để kiểm tra.</div>;
    const material = materials.find(m => m.id === q.materialVersionId);
    return <div key={q.id} className="preview-question">{slot && <span className="eyebrow">{practiceRoles[slot.practiceRole]} · Câu {slot.position + 1}</span>}{material && <MaterialPresentation material={material} />}<QuestionPresentation key={q.id + ':' + q.revision} question={q} /></div>;
  })}</div>;
}
