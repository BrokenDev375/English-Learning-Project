import { api, requestId } from '../../api';
import type { CatalogPage } from '../content/catalog';
import type { Create, Draft, Feedback, Progress, Result, Snapshot, Summary } from './types';
export const attemptPath = (id: number) => '/api/attempts/' + id;
export const readAttempt = (id: number) => api<Snapshot>(attemptPath(id), 'GET', undefined, true);
export const readResult = (id: number) => api<Result>(attemptPath(id) + '/result', 'GET', undefined, true);
export const createCommand = (input: Create) => ({ ...input, requestId: requestId() });
export const createAttempt = (body: ReturnType<typeof createCommand>) => api<Snapshot>('/api/attempts', 'POST', body, true);
export const listAttempts = (params: URLSearchParams) => api<CatalogPage<Summary>>('/api/attempts?' + params);
export const listProgress = (unit: string, page: number) => api<CatalogPage<Progress>>('/api/me/learning-progress?size=50&page=' + page + (unit ? '&unitId=' + encodeURIComponent(unit) : ''));
export type MutationKind = 'draft' | 'hint' | 'check' | 'submit' | 'retry' | 'complete';
export interface Operation { kind: MutationKind; path: string; body: { requestId: string; baseRevision: number } & Partial<Draft> & { response?: unknown }; generation: number; itemId?: number }
export interface LessonCompletionAck { requestId: string; attemptId: number; revision: number; lessonId: number; lessonCompleted: boolean }
export type Ack = { revision: number; hint?: string } | Feedback | Result | LessonCompletionAck;
export const sendOperation = (op: Operation) => api<Ack>(op.path, op.kind === 'draft' ? 'PUT' : 'POST', op.body, true);
