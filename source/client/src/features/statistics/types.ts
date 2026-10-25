import type { CatalogPage } from '../content/catalog';
export type Page<T> = CatalogPage<T>;
export interface Metric { total: number; correct: number; incorrect: number; unanswered: number; accuracyPercent: number | null }
export interface Performance { first: Metric; guided: Metric; assisted: Metric; independentNew: Metric; independentSeen: Metric; guidedRetries: Metric; corrections: Metric }
export interface Activity { submittedAttempts: number; activeAttempts: number; completedLessons: number; learnersWithSubmissions: number; lastSubmittedAt: string | null; modes: { mode: string; submissions: number }[] }
export interface Review { trackedObjectives: number; needsReview: number; mistakeObjectives: number; pendingSubmissions: number; nextScheduledAt: string | null }
export interface Rewards { state: string; totalXp: number | null; level: number | null; streakDays: number | null; badges: { code: string; name: string; earnedAt: string }[] | null; currentWeek: LeagueWeek | null; history: LeagueWeek[] | null }
export interface LeagueWeek { week: string; tier: string; position: number | null; xp: number | null }
export interface LearnerOverview { generatedAt: string; period: string; from: string; activity: Activity; performance: Performance; review: Review; rewards: Rewards }
export interface Breakdown { id: number; name: string; topicId: number; performance: Performance }
export interface Lesson { lessonId: number; lessonVersionId: number; title: string; state: string; lastAttemptId: number | null; completedAt: string; lastScorePercent: number | null }
export interface AdminOverview { generatedAt: string; period: string; from: string; activity: Activity; performance: Performance; roles: { label: string; count: number }[]; statuses: { label: string; count: number }[]; difficulties: { difficulty: string; performance: Performance }[]; presence: { connectedAccounts: number; openConnections: number; basis: string } }
export interface Question { questionId: number; questionVersionId: number; versionNo: number; prompt: string; type: string; difficulty: string; learners: number; first: Metric }
export interface TeacherUnit { unitId: number; title: string; stage: number; sortOrder: number }
export interface TeacherOverview { generatedAt: string; period: string; from: string; unitId: number | null; units: number; learnersStarted: number; activity: Activity; performance: Performance; hintOpenedQuestions: number; difficulties: { difficulty: string; performance: Performance }[] }
export interface TeacherLesson { lessonId: number; unitId: number; unitTitle: string; title: string; learnersStarted: number; activeAttempts: number; submittedAttempts: number; completedLearners: number; hintOpenedQuestions: number; performance: Performance }
