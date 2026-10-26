import { ApiError, errorMessage, requestId } from '../../api';
import { attemptPath, readAttempt, readResult, sendOperation } from './api';
import type { createCommand } from './api';
import type { MutationKind, Operation, Ack } from './api';
import type { Answer, Draft, Feedback, PlayerState, Result, Snapshot } from './types';
import { activateStorage, isCurrent, lease, purgeStorage, readRows, unlockStorage, userEpoch, writeRows, writerId, prepareWriter, pruneCleanDrafts } from './storage/database';
import type { Row } from './storage/database';
import { watchAttempt } from '../connection/manager';
import { syncReviewAfterSubmission } from './storage/journal';
import { validRecoveryState } from './storage/validation';

interface SessionState {
  snapshot: Snapshot | null; draft: Draft | null; result: Result | null; busy: boolean; loading: boolean;
  playerRestoreVersion: number;
  localSaved: boolean; storageError: string; copies: { key: string; updatedAt: number }[];
  pendingAck: Ack | null;
  pending: Operation | null; conflict: Snapshot | null; error: string; loginRequired: boolean;
  hints: Record<number, string>; feedback: Record<number, { data: Feedback; response: Answer | null }>;
}
const clone = <T,>(value: T): T => structuredClone(value);
const equal = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
function fromSnapshot(s: Snapshot): Draft { return { answers: s.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: i.response })), playerState: s.playerState }; }
interface WorkingRow extends Row { attemptId: number; writerId: string; generation: number; updatedAt: number; manualSaveRequired?: boolean; state: Pick<SessionState, 'snapshot' | 'draft' | 'result' | 'pending' | 'pendingAck' | 'conflict' | 'hints' | 'feedback'> }
function validOperation(op: Operation, id: number) {
  return ['draft', 'hint', 'check', 'submit', 'retry', 'complete'].includes(op.kind) && typeof op.body?.requestId === 'string' && Number.isSafeInteger(op.body.baseRevision) && op.body.baseRevision >= 0
    && op.path === attemptPath(id) + (op.itemId === undefined ? '/' + op.kind : '/items/' + op.itemId + '/' + op.kind)
    && ((op.kind === 'draft' || op.kind === 'submit' || op.kind === 'complete') ? op.itemId === undefined : Number.isSafeInteger(op.itemId) && op.itemId! > 0);
}

/** Persisted per user/Attempt/writer. Commands remain immutable through replay and reconciliation. */
export class AttemptSession {
  private value: SessionState = { snapshot: null, draft: null, result: null, busy: false, loading: false, playerRestoreVersion: 0, pending: null, pendingAck: null, conflict: null, error: '', loginRequired: false, hints: {}, feedback: {}, localSaved: false, storageError: '', copies: [] };
  private listeners = new Set<() => void>();
  private generation = 0;
  private stamp: number;
  private hydrated = false;
  private localTimer?: number;
  private serverTimer?: number;
  private poll?: number;
  private unwatch?: () => void;
  private writes: Promise<void> = Promise.resolve();
  private maxRevision = -1;
  private manualSaveRequired = false;
  private quarantined = false;
  constructor(readonly id: number, readonly userId: number) { this.stamp = userEpoch(); }
  private current() { return isCurrent(this.userId, this.stamp); }
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    if (this.listeners.size === 1) { this.unwatch = watchAttempt(this.id); window.addEventListener('learning-recover', this.recover); window.addEventListener('attempt-invalidation', this.invalidate); this.poll = window.setInterval(this.recover, 30000); }
    return () => { this.listeners.delete(listener); if (!this.listeners.size) { this.unwatch?.(); window.clearInterval(this.poll); window.removeEventListener('learning-recover', this.recover); window.removeEventListener('attempt-invalidation', this.invalidate); } };
  };
  getSnapshot = () => this.value;
  private update(patch: Partial<SessionState>, persist = true) {
    if (!this.current()) return;
    this.value = { ...this.value, ...patch }; this.listeners.forEach(l => l());
    if (persist && this.hydrated && this.value.snapshot) { window.clearTimeout(this.localTimer); this.localTimer = window.setTimeout(() => { void this.persist().catch(() => {}); }, 250); }
  }
  private key() { return `${this.userId}:${this.id}:${writerId()}`; }
  private persist(deleteCommand?: string) {
    window.clearTimeout(this.localTimer);
    const { snapshot, draft, result, pending, pendingAck, conflict, hints, feedback } = this.value;
    const row: WorkingRow = { key: this.key(), userId: this.userId, version: 1, writerId: writerId(), attemptId: this.id, generation: this.generation, updatedAt: Date.now(), manualSaveRequired: this.manualSaveRequired, state: clone({ snapshot, draft, result, pending, pendingAck, conflict, hints, feedback }) };
    this.writes = this.writes.catch(() => {}).then(async () => {
      if (!this.current()) return;
      try {
        await writeRows(this.userId, this.stamp, { workingDrafts: [row] }, deleteCommand ? { commands: [`${this.userId}:${deleteCommand}`] } : {});
        if (this.current() && this.generation === row.generation) this.update({ localSaved: true, storageError: this.quarantined ? 'Có bản tại máy không hợp lệ đã được cách ly, không phát lại. Bản máy chủ được dùng để đối chiếu.' : '' }, false);
      } catch (e) { this.update({ localSaved: false, storageError: 'Chưa lưu được tại máy. ' + errorMessage(e) }, false); throw e; }
    });
    return this.writes;
  }
  private validRow(row: WorkingRow) {
    return row.version === 1 && row.state?.snapshot?.attemptId === this.id && validRecoveryState(row.state) && Number.isSafeInteger(row.generation) && (!row.state.pending || validOperation(row.state.pending, this.id));
  }
  private async hydrate() {
    if (this.hydrated) return;
    try {
      const rows = (await readRows<WorkingRow>('workingDrafts', this.userId)).filter(r => r.attemptId === this.id);
      const row = rows.find(r => r.key === this.key());
      if (row) {
        if (!this.validRow(row)) {
          this.quarantined = true;
          await writeRows(this.userId, this.stamp, { workingDrafts: [{ ...row, key: 'quarantine:' + requestId(), attemptId: -1 } as WorkingRow] }, { workingDrafts: [row.key] });
          throw new Error('Bản tại máy không hợp lệ; đã cách ly, chưa được phát lại.');
        }
        this.generation = row.generation; this.manualSaveRequired = row.manualSaveRequired ?? false; this.update({ ...row.state, result: null, localSaved: true, playerRestoreVersion: this.value.playerRestoreVersion + 1 }, false);
      }
      this.update({ copies: rows.filter(r => r.key !== this.key() && this.validRow(r) && (r.state.pending || !equal(r.state.draft, fromSnapshot(r.state.snapshot!)))).map(r => ({ key: r.key, updatedAt: r.updatedAt })) }, false);
    } catch (e) { this.update({ storageError: errorMessage(e), localSaved: false }, false); }
    this.hydrated = true;
  }
  async restoreCopy(key: string) {
    if (this.value.busy) return;
    const row = (await readRows<WorkingRow>('workingDrafts', this.userId)).find(r => r.key === key && this.validRow(r)); if (!row) return;
    this.generation = row.generation; this.update({ ...row.state, result: null, copies: [], playerRestoreVersion: this.value.playerRestoreVersion + 1 });
    await this.persist(); await this.load();
  }
  get unpersisted() { return (this.dirty || !!this.value.pending) && !this.value.localSaved; }
  flushLocal() { if (this.hydrated && this.value.snapshot) void this.persist().catch(() => {}); }
  private invalidate = (e: Event) => { const d = (e as CustomEvent<{ attemptId: number; revision: number }>).detail; if (d.attemptId !== this.id) return; this.maxRevision = Math.max(this.maxRevision, d.revision); if (!this.value.busy && d.revision !== this.value.snapshot?.revision) void this.load(); };
  private recover = () => { if (!this.current() || !navigator.onLine || this.value.busy || this.value.loading || this.value.loginRequired || this.value.conflict) return; if (this.value.pending) void this.retry(); else void this.load(); };
  private schedule() {
    window.clearTimeout(this.serverTimer);
    if (this.manualSaveRequired || this.value.pending || this.value.conflict || this.value.loginRequired || this.value.snapshot?.state !== 'IN_PROGRESS') return;
    this.serverTimer = window.setTimeout(() => { if (this.current() && navigator.onLine && this.dirty && !this.value.pending && !this.value.conflict && !this.value.busy && !this.value.loading) void this.mutate('draft'); }, 1500);
  }
  get dirty() { return !!this.value.snapshot && !!this.value.draft && !equal(this.value.draft, fromSnapshot(this.value.snapshot)); }
  async load() {
    if (!this.current() || this.value.busy || this.value.loading) return;
    this.update({ loading: true }, false);
    await this.hydrate();
    if (this.value.pending) {
      this.update({ loading: false, loginRequired: false, error: 'Thao tác trước chưa được xác nhận đầy đủ. Hãy gửi lại đúng thao tác để đối soát.' });
      await this.retry(); return;
    }
    this.update({ loading: true, error: '' });
    try {
      const s = await readAttempt(this.id);
      if (!this.current()) return;
      const result = s.state === 'SUBMITTED' ? await readResult(this.id) : null;
      if (this.value.snapshot && this.dirty && s.revision !== this.value.snapshot.revision) this.update({ conflict: s, result });
      else {
        const keepDraft = this.dirty || !!this.value.pending;
        this.update({ snapshot: s, result, draft: keepDraft ? this.value.draft : clone(fromSnapshot(s)), loginRequired: false,
          playerRestoreVersion: this.value.playerRestoreVersion + (keepDraft ? 0 : 1) });
      }
      await this.persist();
      if (this.dirty) this.schedule();
      if (this.maxRevision > s.revision) { this.maxRevision = -1; window.setTimeout(() => void this.load(), 100); }
    } catch (e) { this.failure(e); } finally { this.update({ loading: false }); }
  }
  edit(itemId: number, response: Answer | null) {
    if (!this.value.draft || this.value.pending?.kind === 'submit' || this.value.loginRequired || this.value.snapshot?.state !== 'IN_PROGRESS') return; this.generation++;
    this.update({ localSaved: false, draft: { ...this.value.draft, answers: this.value.draft.answers.map(a => a.attemptQuestionId === itemId ? { ...a, response } : a) } }); this.schedule();
  }
  player(playerState: PlayerState) {
    if (!this.value.draft || equal(playerState, this.value.draft.playerState)) return;
    this.generation++; this.update({ localSaved: false, draft: { ...this.value.draft, playerState } }); this.schedule();
  }
  async mutate(kind: MutationKind, itemId?: number, response?: Answer | null) {
    const s = this.value.snapshot;
    if (!this.current() || !s || !this.value.draft || this.value.pending || this.value.busy || this.value.conflict || this.value.loginRequired) return;
    if (!navigator.onLine && ['hint', 'check', 'retry'].includes(kind)) { this.failure(new ApiError(0, 'OFFLINE', 'Cần kết nối máy chủ để dùng hỗ trợ hoặc chữa bài.')); return; }
    window.clearTimeout(this.serverTimer);
    this.manualSaveRequired = false;
    const body: Operation['body'] = { requestId: requestId(), baseRevision: s.revision };
    if (kind === 'draft' || kind === 'submit') Object.assign(body, clone(this.value.draft));
    if (kind === 'check' || kind === 'retry') body.response = clone(response ?? null);
    const path = attemptPath(this.id) + (itemId === undefined ? '/' + kind : '/items/' + itemId + '/' + kind);
    const op: Operation = { kind, path, body, generation: this.generation, itemId };
    this.update({ pending: op, localSaved: false }); return this.send(op);
  }
  retry = async () => { if (this.current() && this.value.pending && !this.value.busy) await this.send(this.value.pending); };
  private async send(op: Operation) {
    if (!validOperation(op, this.id)) { this.failure(new Error('Thao tác tại máy không hợp lệ.')); return; }
    this.update({ busy: true, error: '', loginRequired: false });
    let ack: Ack | undefined, completed: Ack | undefined;
    try {
      await this.persist();
      if (!navigator.onLine) throw new ApiError(0, 'OFFLINE', op.kind === 'submit' ? 'Chờ nộp khi có mạng. Yêu cầu đã lưu tại máy.' : 'Đã lưu tại máy, chờ kết nối để đồng bộ.');
      await lease(this.userId, 'attempt:' + this.id, async () => {
      if (!this.current()) return;
      ack = await sendOperation(op);
      if (!this.current()) return;
      this.update({ pendingAck: ack }, false); await this.persist();
      const s = await readAttempt(this.id);
      const result = s.state === 'SUBMITTED' ? await readResult(this.id) : null;
      if (!this.current()) return;
      const revision = 'submittedRevision' in ack ? ack.submittedRevision : ack.revision;
      const hints = { ...this.value.hints }, feedback = { ...this.value.feedback };
      if (op.itemId && 'hint' in ack && ack.hint) hints[op.itemId] = ack.hint;
      if (op.itemId && 'trialId' in ack) feedback[op.itemId] = { data: ack, response: (op.body.response ?? null) as Answer | null };
      // A different tab may have written between ACK and GET. Never silently rebase local answers.
      if (s.state === 'IN_PROGRESS' && s.revision !== revision) {
        this.update({ pending: null, pendingAck: null, conflict: s, hints, feedback, error: 'Bài làm đã thay đổi ở nơi khác. Hãy đối chiếu trước khi tiếp tục.' });
      } else {
        const replace = (op.kind === 'draft' && this.generation === op.generation) || op.kind === 'submit';
        this.update({ snapshot: s, result, hints, feedback, pending: null, pendingAck: null, conflict: null, draft: replace ? clone(fromSnapshot(s)) : this.value.draft });
      }
      await this.persist();
      if (op.kind === 'submit' && s.state === 'SUBMITTED') void syncReviewAfterSubmission(this.userId).catch(() => {});
      if (op.kind === 'complete') window.dispatchEvent(new Event('learning-progress-changed'));
      completed = ack;
      });
    } catch (e) {
      // Once ACK is known, a failed reconciliation still retries the exact operation, then GET.
      if (!ack && e instanceof ApiError && e.status === 409) {
        try { this.update({ conflict: await readAttempt(this.id), pending: null }); await this.persist(); } catch { /* pending blocks further mutations until reconciliation */ }
      } else if (!ack && e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) this.update({ pending: null });
      this.failure(e);
    } finally { this.update({ busy: false }); if (!this.value.pending && !this.value.conflict && this.dirty) this.schedule(); }
    return completed;
  }
  private failure(e: unknown) {
    if (e instanceof ApiError && (e.status === 403 || (e.status === 404 && e.code !== 'HINT_NOT_AVAILABLE'))) this.update({ snapshot: null, draft: null, result: null, hints: {}, feedback: {} }, false);
    this.update({ error: errorMessage(e), loginRequired: e instanceof ApiError && e.status === 401 });
  }
  resolveConflict(keepLocal: boolean) {
    window.clearTimeout(this.serverTimer);
    const s = this.value.conflict; if (!s || this.value.busy) return;
    const keepDraft = keepLocal && s.state === 'IN_PROGRESS';
    this.manualSaveRequired = keepDraft;
    this.update({ snapshot: s, conflict: null, pending: null, error: '', draft: keepDraft ? this.value.draft : clone(fromSnapshot(s)),
      playerRestoreVersion: this.value.playerRestoreVersion + (keepDraft ? 0 : 1) });
    if (s.state === 'SUBMITTED') void this.load();
  }
  dispose() { window.clearTimeout(this.serverTimer); window.clearTimeout(this.localTimer); window.clearInterval(this.poll); this.unwatch?.(); window.removeEventListener('learning-recover', this.recover); window.removeEventListener('attempt-invalidation', this.invalidate); }
}
const sessions = new Map<string, AttemptSession>();
let pendingCreate: ReturnType<typeof createCommand> | null = null;
let owner: number | undefined;
export function activateLearningUser(userId: number) { if (owner !== userId) { sessions.forEach(s => s.dispose()); sessions.clear(); pendingCreate = null; owner = userId; activateStorage(userId); } }
export async function prepareLearningUser(userId: number) { await prepareWriter(); await unlockStorage(userId); activateLearningUser(userId); await pruneCleanDrafts(userId).catch(() => {}); }
export const getPendingCreate = () => pendingCreate;
export const setPendingCreate = (command: ReturnType<typeof createCommand> | null) => { pendingCreate = command; };
export function sessionFor(userId: number, attemptId: number) {
  activateLearningUser(userId);
  const key = userId + ':' + attemptId;
  if (!sessions.has(key)) sessions.set(key, new AttemptSession(attemptId, userId));
  return sessions.get(key)!;
}
export async function clearLearningSessions() { const previous = owner; sessions.forEach(s => s.dispose()); sessions.clear(); owner = undefined; pendingCreate = null; activateStorage(undefined); if (previous !== undefined) await purgeStorage(previous); }
export function forgetLearningUser() { sessions.forEach(s => s.dispose()); sessions.clear(); owner = undefined; pendingCreate = null; activateStorage(undefined); }
export function hasUnsavedLearning() { return !!pendingCreate || [...sessions.values()].some(s => s.dirty || s.getSnapshot().pending); }
export async function hasStoredUnsavedLearning(userId: number) {
  try {
    if ((await readRows<Row>('commands', userId)).length) return true;
    return (await readRows<WorkingRow>('workingDrafts', userId)).some(row => !row.state?.snapshot || row.state.pending || row.state.conflict || !equal(row.state.draft, fromSnapshot(row.state.snapshot)));
  } catch { return true; } // Cannot confirm storage is clean: ask before discarding it.
}
export function hasUnpersistedLearning() { return [...sessions.values()].some(s => s.unpersisted); }
