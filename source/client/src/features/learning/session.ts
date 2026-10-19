import { ApiError, errorMessage, requestId } from '../../api';
import { attemptPath, readAttempt, readResult, sendOperation } from './api';
import type { createCommand } from './api';
import type { MutationKind, Operation, Ack } from './api';
import type { Answer, Draft, Feedback, PlayerState, Result, Snapshot } from './types';

interface SessionState {
  snapshot: Snapshot | null; draft: Draft | null; result: Result | null; busy: boolean; loading: boolean;
  pending: Operation | null; conflict: Snapshot | null; error: string; loginRequired: boolean;
  hints: Record<number, string>; feedback: Record<number, { data: Feedback; response: Answer | null }>;
}
const clone = <T,>(value: T): T => structuredClone(value);
const equal = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
function fromSnapshot(s: Snapshot): Draft { return { answers: s.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: i.response })), playerState: s.playerState }; }

/** In-memory only. Pending commands keep their original key/payload until replay + GET succeed. */
export class AttemptSession {
  private value: SessionState = { snapshot: null, draft: null, result: null, busy: false, loading: false, pending: null, conflict: null, error: '', loginRequired: false, hints: {}, feedback: {} };
  private listeners = new Set<() => void>();
  private generation = 0;
  constructor(readonly id: number) {}
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  getSnapshot = () => this.value;
  private update(patch: Partial<SessionState>) { this.value = { ...this.value, ...patch }; this.listeners.forEach(l => l()); }
  get dirty() { return !!this.value.snapshot && !!this.value.draft && !equal(this.value.draft, fromSnapshot(this.value.snapshot)); }
  async load() {
    if (this.value.busy || this.value.loading) return;
    if (this.value.pending) {
      this.update({ loginRequired: false, error: 'Thao tác trước chưa được xác nhận đầy đủ. Hãy gửi lại đúng thao tác để đối soát.' });
      return;
    }
    this.update({ loading: true, error: '' });
    try {
      const s = await readAttempt(this.id);
      const result = s.state === 'SUBMITTED' ? await readResult(this.id) : null;
      if (this.value.snapshot && this.dirty && s.revision !== this.value.snapshot.revision) this.update({ conflict: s, result });
      else this.update({ snapshot: s, result, draft: this.dirty || this.value.pending ? this.value.draft : clone(fromSnapshot(s)), loginRequired: false });
    } catch (e) { this.failure(e); } finally { this.update({ loading: false }); }
  }
  edit(itemId: number, response: Answer | null) {
    if (!this.value.draft) return; this.generation++;
    this.update({ draft: { ...this.value.draft, answers: this.value.draft.answers.map(a => a.attemptQuestionId === itemId ? { ...a, response } : a) } });
  }
  player(playerState: PlayerState) {
    if (!this.value.draft || equal(playerState, this.value.draft.playerState)) return;
    this.generation++; this.update({ draft: { ...this.value.draft, playerState } });
  }
  async mutate(kind: MutationKind, itemId?: number, response?: Answer | null) {
    const s = this.value.snapshot;
    if (!s || !this.value.draft || this.value.pending || this.value.busy || this.value.conflict || this.value.loginRequired) return;
    const body: Operation['body'] = { requestId: requestId(), baseRevision: s.revision };
    if (kind === 'draft' || kind === 'submit') Object.assign(body, clone(this.value.draft));
    if (kind === 'check' || kind === 'retry') body.response = clone(response ?? null);
    const path = attemptPath(this.id) + (itemId === undefined ? '/' + kind : '/items/' + itemId + '/' + kind);
    const op: Operation = { kind, path, body, generation: this.generation, itemId };
    this.update({ pending: op }); await this.send(op);
  }
  retry = async () => { if (this.value.pending && !this.value.busy) await this.send(this.value.pending); };
  private async send(op: Operation) {
    this.update({ busy: true, error: '', loginRequired: false });
    let ack: Ack | undefined;
    try {
      ack = await sendOperation(op);
      const s = await readAttempt(this.id);
      const result = s.state === 'SUBMITTED' ? await readResult(this.id) : null;
      const revision = 'submittedRevision' in ack ? ack.submittedRevision : ack.revision;
      const hints = { ...this.value.hints }, feedback = { ...this.value.feedback };
      if (op.itemId && 'hint' in ack && ack.hint) hints[op.itemId] = ack.hint;
      if (op.itemId && 'trialId' in ack) feedback[op.itemId] = { data: ack, response: (op.body.response ?? null) as Answer | null };
      // A different tab may have written between ACK and GET. Never silently rebase local answers.
      if (s.state === 'IN_PROGRESS' && s.revision !== revision) {
        this.update({ pending: null, conflict: s, hints, feedback, error: 'Bài làm đã thay đổi ở nơi khác. Hãy đối chiếu trước khi tiếp tục.' });
      } else {
        const replace = (op.kind === 'draft' && this.generation === op.generation) || op.kind === 'submit';
        this.update({ snapshot: s, result, hints, feedback, pending: null, conflict: null, draft: replace ? clone(fromSnapshot(s)) : this.value.draft });
      }
    } catch (e) {
      // Once ACK is known, a failed reconciliation still retries the exact operation, then GET.
      if (!ack && e instanceof ApiError && e.status === 409) {
        try { this.update({ conflict: await readAttempt(this.id), pending: null }); } catch { /* pending blocks further mutations until reconciliation */ }
      } else if (!ack && e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) this.update({ pending: null });
      this.failure(e);
    } finally { this.update({ busy: false }); }
  }
  private failure(e: unknown) { this.update({ error: errorMessage(e), loginRequired: e instanceof ApiError && e.status === 401 }); }
  resolveConflict(keepLocal: boolean) {
    const s = this.value.conflict; if (!s || this.value.busy) return;
    this.update({ snapshot: s, conflict: null, pending: null, error: '', draft: keepLocal && s.state === 'IN_PROGRESS' ? this.value.draft : clone(fromSnapshot(s)) });
    if (s.state === 'SUBMITTED') void this.load();
  }
}
const sessions = new Map<string, AttemptSession>();
let pendingCreate: ReturnType<typeof createCommand> | null = null;
let owner: number | undefined;
export function activateLearningUser(userId: number) { if (owner !== userId) { clearLearningSessions(); owner = userId; } }
export const getPendingCreate = () => pendingCreate;
export const setPendingCreate = (command: ReturnType<typeof createCommand> | null) => { pendingCreate = command; };
export function sessionFor(userId: number, attemptId: number) {
  activateLearningUser(userId);
  const key = userId + ':' + attemptId;
  if (!sessions.has(key)) sessions.set(key, new AttemptSession(attemptId));
  return sessions.get(key)!;
}
export function clearLearningSessions() { sessions.clear(); owner = undefined; pendingCreate = null; }
export function hasUnsavedLearning() { return !!pendingCreate || [...sessions.values()].some(s => s.dirty || s.getSnapshot().pending); }
