import type { QuestionData } from '../content/authoring';
import type { Answer, PublicQuestion, AnswerKey, Outcome } from '../learning/types';
export interface Page<T> { items: T[]; page: number; totalPages: number; totalElements: number }
export const stateNames: Record<string, string> = { DRAFT: 'Đang soạn', PUBLISHED: 'Sắp thi', RUNNING: 'Đang thi', CLOSING: 'Đang chốt kết quả', FINALIZED: 'Đã kết thúc', ARCHIVED: 'Đã lưu trữ', CANCELLED: 'Đã hủy' };
export const statusNames: Record<string, string> = { REQUESTED: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Không được duyệt', REVOKED: 'Đã thu hồi quyền thi' };
export interface Contest { id: number; title: string; description: string; state: string; revision: number; registrationClosesAt: string; startsAt: string; endsAt: string; penaltyMinutes: number; questionCount: number; participationStatus: string | null; serverTime: string }
export type PrivateQuestion = Pick<QuestionData, 'type' | 'prompt' | 'difficulty' | 'content' | 'answerKey' | 'explanation'> & { id: string; context: string | null };
export interface Settings { title: string; description: string; registrationClosesAt: string; startsAt: string; endsAt: string; penaltyMinutes: number; shuffleQuestions: boolean; proctorIds: number[]; paperId: number | null }
export interface Author extends Contest { canManage: boolean; canEdit: boolean; shuffleQuestions: boolean; proctorIds: number[]; proctors: Proctor[]; questions: PrivateQuestion[]; paperId: number | null; paperTitle: string | null; paperRevision: number | null }
export interface Proctor { userId:number; fullName:string; email:string; eligible:boolean }
export interface Capabilities { canAuthor: boolean; canProctor: boolean; canManage: boolean }
export interface TeacherPermission { userId: number; fullName: string; email: string; canAuthor: boolean; canProctor: boolean; revision: number }
export interface Paper { id: number; ownerId: number; ownerName: string; revision: number; title: string; description: string; state: string; questionCount: number; usageCount: number; reviewNote: string; reviewedBy: number | null; reviewedAt: string | null; updatedAt: string; canEdit: boolean; canReview: boolean; questions: PrivateQuestion[] }
export const paperStates: Record<string,string> = { DRAFT:'Bản nháp', PENDING:'Chờ duyệt', READY:'Đã duyệt', ARCHIVED:'Đã lưu trữ' };
export interface Participant { id: number; userId: number; displayName: string; status: string; revision: number; controlRevision: number; requestedAt: string; checkedInAt: string | null; lastSeenAt: string | null; lastSavedAt: string | null; online: boolean; deadline: string; tabHidden: boolean; tabLeaves: number; flagged: boolean; penaltySeconds: number; answeredCount: number; questionCount: number; submitted: boolean }
export interface Question extends PublicQuestion { id: string; context: string | null; difficulty: string }
export interface Result { correctCount: number; totalCount: number; scorePercent: number; elapsedMillis: number; submittedAt: string; source: string; penaltySeconds: number; items: (Question & { answerKey: AnswerKey; explanation: string; response: Answer | null; outcome: Outcome })[] }
export type Answers = Record<string, Answer | null>;
export interface Learner { contest: Contest; serverTime: string; participation: Participant | null; canRegister: boolean; canCheckIn?: boolean; canResume?: boolean; paper: Question[] | null; answers: Answers | null; result: Result | null }
export interface Monitor { contest: Contest; canManage: boolean; statistics: Record<string, number>; participants: Page<Participant>; serverTime: string }
export interface Audit { id: number; type: string; reason: string; createdAt: string; participantId: number | null; actorId: number | null; details: Record<string, unknown> }
export interface Board { finalized: boolean; items: { rank: number; displayName: string; correctCount: number; totalCount: number; elapsedMillis: number; penaltySeconds: number; isSelf: boolean }[] }
export const date = (value: string | null) => value ? new Date(value).toLocaleString('vi-VN') : '—';
export function duration(ms: number) { const s = Math.max(0, Math.ceil(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; }
export function raceTime(ms: number) { const n = Math.max(0, Math.floor(ms)); return `${Math.floor(n/60000)}:${String(Math.floor(n/1000)%60).padStart(2,'0')}.${String(n%1000).padStart(3,'0')}`; }
