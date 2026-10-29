export interface Criterion { id: string; text: string; points: number }
export interface Definition { title: string; prompt: string; minWords: number; maxWords: number; criteria: Criterion[] }
export interface Version { id: number; definition: Definition; createdAt: string }
export interface Task { unitId: number; revision: number; draft: Definition | null; published: Version | null }
export interface Mark { criterionId: string; met: boolean; comment: string }
export interface Grade { id: number; earnedPoints: number; totalPoints: number; marks: Mark[]; comment: string; allowRevision: boolean; gradedAt: string }
export interface Work { id: number; versionId: number; revision: number; state: string; text: string; latestSubmissionId: number | null; updatedAt: string }
export interface Submission { id: number; workId: number; versionId: number; text: string; submittedAt: string; grade: Grade | null }
export interface Learner { unitId: number; available: boolean; unlocked: boolean; blockReason: string; version: Version | null; work: Work | null; history: Submission[] }
export interface QueueItem { id: number; unitId: number; studentName: string; title: string; submittedAt: string; graded: boolean }
export interface Grading { unitId: number; studentName: string; workRevision: number; canGrade: boolean; version: Version; submission: Submission }
export const writingStates: Record<string, string> = { NOT_STARTED: 'Chưa bắt đầu', DRAFT: 'Đang viết', SUBMITTED: 'Chờ giáo viên chấm', RETURNED: 'Được phép sửa và nộp lại', GRADED: 'Đã chấm' };
export const wordCount = (text: string) => text.trim() ? text.trim().split(/\s+/u).length : 0;
