import type { Instruction, Option, Part, Pair } from '../content/authoring';
import type { Objective } from '../content/catalog';
export type Mode = 'LEARN' | 'PRACTICE' | 'REVIEW';
export type State = 'IN_PROGRESS' | 'SUBMITTED';
export type Answer = { type: 'SINGLE_CHOICE'; optionId: string | null } | { type: 'FILL_BLANK'; values: { blankId: string; value: string }[] } | { type: 'MATCHING'; pairs: Pair[] } | { type: 'ORDERING'; tokenIds: string[] };
export interface PublicQuestion { type: Answer['type']; prompt: string; content: { options?: Option[]; parts?: Part[]; leftItems?: Option[]; rightItems?: Option[]; tokens?: Option[] } }
export interface Item { attemptQuestionId: number; questionVersionId: number; originKey: string; objectiveId: number; difficulty: string; practiceRole: 'GUIDED' | 'INDEPENDENT' | 'REVIEW_VARIANT'; position: number; wasSeenBefore: boolean; hintAvailable: boolean; hintOpened: boolean; feedbackOpened: boolean; question: PublicQuestion; materialVersionId: number | null; response: Answer | null }
export interface Material { materialVersionId: number; kind: 'READING' | 'IMAGE' | 'AUDIO'; title: string; readingText: string | null; asset: { id: number; mimeType: string; sizeBytes: number; url: string } | null; altText: string | null; caption: string | null; startSeconds: number | null; endSeconds: number | null; source: { label: string; url: string | null; license: string | null } }
export interface PlayerState { currentAttemptQuestionId: number | null; audioPositions: { materialVersionId: number; seconds: number }[] }
export type InlinePart = { kind: 'TEXT'; text: string } | { kind: 'BLANK'; attemptQuestionId: number; blankId: string };
export interface ReadingPassage { materialVersionId: number; parts: InlinePart[] }
export interface LearningActivity { id: string; kind: import('../content/authoring').ActivityKind; attemptQuestionIds: number[]; passages: ReadingPassage[] }
export interface Snapshot { attemptId: number; mode: Mode; state: State; lessonVersionId: number | null; revision: number; createdAt: string; updatedAt: string; submittedAt: string | null; items: Item[]; materials: Material[]; playerState: PlayerState; submissionId: number | null; lesson: { lesson: { id: number; unitId: number; title: string }; instructions: Instruction[]; objectives: Objective[] } | null; activities?: LearningActivity[] }
export interface Summary { attemptId: number; mode: Mode; state: State; lessonVersionId: number | null; revision: number; totalQuestions: number; answeredCount: number; createdAt: string; updatedAt: string; submittedAt: string | null; submissionId: number | null }
export interface Progress { lessonId: number; state: 'NOT_STARTED' | 'STARTED' | 'COMPLETED'; lastAttemptId: number | null; completedAt: string | null; lastCompletedVersionId: number | null; currentPublishedVersionId: number | null; updateAvailable: boolean; lastScorePercent: number | null }
export type AnswerKey = { correctOptionId?: string; blanks?: { blankId: string; acceptedAnswers: string[] }[]; pairs?: Pair[]; acceptedOrders?: string[][] };
export type Outcome = 'CORRECT' | 'INCORRECT' | 'UNANSWERED';
export interface Feedback { requestId: string; attemptId: number; revision: number; attemptQuestionId: number; trialId: number; trialNo: number; kind: 'FIRST' | 'GUIDED_RETRY' | 'CORRECTION'; outcome: Outcome; responseComplete: boolean; supportUsed: boolean; answerKey: AnswerKey; explanation: string }
export interface ResultItem { attemptQuestionId: number; questionVersionId: number; originKey: string; objectiveId: number; practiceRole: Item['practiceRole']; wasSeenBefore: boolean; firstResponse: Answer | null; firstOutcome: Outcome; firstResponseComplete: boolean; firstSupportUsed: boolean; finalResponse: Answer | null; finalOutcome: Outcome; finalResponseComplete: boolean; answerKey: AnswerKey; explanation: string; materialVersionId: number | null }
export interface Result { factsSchemaVersion: number; submissionId: number; attemptId: number; mode: Mode; lessonVersionId: number | null; submittedAt: string; submittedRevision: number; totalQuestions: number; answeredCount: number; unansweredCount: number; correctCount: number; scorePercent: number; independentFreshAnsweredCount: number; independentFreshCorrectCount: number; lessonCompleted: boolean; items: ResultItem[]; materialFeedback: { materialVersionId: number; transcript: string | null }[] }
export interface Draft { answers: { attemptQuestionId: number; response: Answer | null }[]; playerState: PlayerState }
export type Create = { mode: 'LEARN'; lessonId: number } | { mode: 'PRACTICE'; topicId: number; difficulty: string; count: number } | { mode: 'REVIEW'; reviewKind: 'DUE' | 'MISTAKES'; topicId: number | null; count: number };
export const modeNames: Record<Mode, string> = { LEARN: 'Học bài', PRACTICE: 'Luyện tập', REVIEW: 'Ôn tập' };
export const outcomeNames: Record<Outcome, string> = { CORRECT: 'Đúng', INCORRECT: 'Chưa đúng', UNANSWERED: 'Chưa trả lời' };
export function answered(a: Answer | null) { return !!a && (a.type === 'SINGLE_CHOICE' ? a.optionId !== null : a.type === 'FILL_BLANK' ? a.values.some(v => v.value.trim()) : a.type === 'ORDERING' ? a.tokenIds.length > 0 : a.pairs.length > 0); }
export function complete(q: PublicQuestion, a: Answer | null) {
  if (!a || a.type !== q.type) return false;
  if (a.type === 'SINGLE_CHOICE') return a.optionId !== null;
  if (a.type === 'FILL_BLANK') return q.content.parts!.filter(p => p.kind === 'BLANK').every(p => p.kind === 'BLANK' && a.values.some(v => v.blankId === p.blankId && v.value.trim()));
  if (a.type === 'ORDERING') return a.tokenIds.length === q.content.tokens!.length && new Set(a.tokenIds).size === a.tokenIds.length && a.tokenIds.every(id => q.content.tokens!.some(t => t.id === id));
  return q.content.leftItems!.every(l => a.pairs.some(p => p.leftId === l.id));
}
