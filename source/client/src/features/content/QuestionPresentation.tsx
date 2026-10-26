import { useId, useState } from 'react';
import type { QuestionData } from './authoring';
import type { Answer } from '../learning/types';
import AnswerFields from '../learning/AnswerFields';
/** Preview receives only the public projection, never an answer key or feedback. */
export default function QuestionPresentation({ question: q }: { question: QuestionData }) {
  const prefix = useId(), [value, setValue] = useState<Answer | null>(null);
  return <article className="question-presentation"><AnswerFields question={{ type: q.type, prompt: q.prompt || 'Câu hỏi chưa nhập nội dung', content: q.content }} value={value} onChange={setValue} prefix={prefix} /></article>;
}
