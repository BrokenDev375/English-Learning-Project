import { api, ApiError, errorMessage, requestId } from '../../api';
import { isCurrent, lease, readRows, userEpoch, writeRows, writerId } from '../learning/storage/database';
import type { Row } from '../learning/storage/database';
import type { Learner } from './types';
type Action = 'start' | 'draft' | 'submit';
interface Pending { action: Action; body: { requestId: string; baseRevision?: number; text?: string } }
interface Local extends Row { kind: 'writing'; unitId: number; writerId: string; updatedAt: number; view: Learner; text: string; pending: Pending | null }
interface State { view: Learner | null; text: string; busy: boolean; error: string; pending: Pending | null; conflict: Learner | null; recovery: Local | null; localSaved: boolean }
const sessions = new Map<string, WritingSession>();
export class WritingSession {
  private state: State = { view: null, text: '', busy: false, error: '', pending: null, conflict: null, recovery: null, localSaved: false };
  private listeners = new Set<() => void>(); private stamp = userEpoch(); private localTimer = 0; private serverTimer = 0; private disposed = false; private authPaused = false;
  private lane: Promise<void> = Promise.resolve(); private unsavedLocal = false;
  private generation = 0;
  constructor(private user: number, private unit: number) {}
  private current = () => !this.disposed && !this.authPaused && isCurrent(this.user, this.stamp);
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  get dirty() { return !!this.state.pending || (!!this.state.view?.work && this.state.text !== this.state.view.work.text); }
  get unpersisted() { return this.unsavedLocal; }
  private update(next: Partial<State>) { if (!this.current()) return; this.state = { ...this.state, ...next }; this.listeners.forEach(f => f()); }
  private key() { return `writing:${this.user}:${this.unit}:${writerId()}`; }
  private persist() {
    const { view, text, pending } = this.state; const dirty = this.dirty; const key = this.key();
    this.lane = this.lane.catch(() => {}).then(async () => {
      if (this.disposed || !isCurrent(this.user, this.stamp) || !view) return;
      await writeRows(this.user, this.stamp, dirty ? { workingDrafts: [{ key, userId: this.user, version: 1, kind: 'writing', unitId: this.unit, writerId: writerId(), updatedAt: Date.now(), view, text, pending } as Local] } : {}, dirty ? {} : { workingDrafts: [key] });
    }); return this.lane;
  }
  async load() {
    if (!this.current() || this.state.busy) return;
    this.update({ busy: true, error: '' });
    try {
      const view = await api<Learner>(`/api/me/units/${this.unit}/writing`); if (!this.current()) return;
      if (this.dirty) { if (view.work?.revision !== this.state.view?.work?.revision) this.update({ conflict: view }); }
      else {
        const rows = await readRows<Local>('workingDrafts', this.user); if (!this.current()) return;
        const recovery = rows.filter(r => r.kind === 'writing' && r.unitId === this.unit && r.version === 1 && validView(r.view, this.unit) && typeof r.text === 'string' && r.text.length <= 30000 && r.key === `writing:${this.user}:${this.unit}:${r.writerId}` && (!r.pending || validPending(r.pending)))
          .sort((a, b) => Number(b.writerId === writerId()) - Number(a.writerId === writerId()) || b.updatedAt - a.updatedAt)[0] ?? null;
        this.update({ view, text: view.work?.text ?? '', recovery });
      }
    } catch (e) { this.update({ error: errorMessage(e) }); }
    finally { this.update({ busy: false }); }
  }
  edit(text: string) {
    if (!this.current() || this.state.busy || this.state.pending || this.state.conflict) return;
    const generation = ++this.generation;
    this.unsavedLocal = true; this.update({ text, localSaved: false, error: '' });
    window.clearTimeout(this.localTimer); window.clearTimeout(this.serverTimer);
    this.localTimer = window.setTimeout(() => { void this.persist().then(() => { if (generation === this.generation) { this.unsavedLocal = false; this.update({ localSaved: true }); } }).catch(e => this.update({ error: errorMessage(e) })); }, 250);
    this.serverTimer = window.setTimeout(() => { if (this.dirty && !this.state.recovery) void this.send('draft'); }, 1500);
  }
  async send(action: Action) {
    if (!this.current() || this.state.busy || this.state.conflict || this.state.recovery || !this.state.view) return;
    window.clearTimeout(this.localTimer); window.clearTimeout(this.serverTimer);
    const pending = this.state.pending ?? { action, body: action === 'start' ? { requestId: requestId() } : { requestId: requestId(), baseRevision: this.state.view.work!.revision, text: this.state.text } };
    this.update({ busy: true, pending, error: '' }); this.unsavedLocal = true;
    try {
      await this.persist(); this.unsavedLocal = false; this.update({ localSaved: true });
      await lease(this.user, `writing:${this.unit}`, async () => {
        if (!this.current()) return;
        await api(`/api/me/units/${this.unit}/writing/${pending.action}`, 'POST', pending.body);
        if (!this.current()) return;
        const view = await api<Learner>(`/api/me/units/${this.unit}/writing`); if (!this.current()) return;
        this.update({ view, text: view.work?.text ?? '', pending: null, conflict: null }); await this.persist();
        window.dispatchEvent(new Event('learning-progress-changed'));
      });
    } catch (e) {
      if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) {
        this.update({ pending: null });
        if (e.status === 409) { try { this.update({ conflict: await api<Learner>(`/api/me/units/${this.unit}/writing`) }); } catch { /* Keep the local text. */ } }
        await this.persist().catch(() => {});
      }
      this.update({ error: errorMessage(e) });
    } finally { this.update({ busy: false }); }
  }
  async recover(keep: boolean) {
    const row = this.state.recovery; if (!row || !this.current()) return;
    try {
    if (keep) {
      // Restore the old revision too: a newer server draft must lead to an explicit comparison.
      this.update({ view: row.view, text: row.text, pending: row.pending, recovery: null, localSaved: true }); await this.persist();
      if (row.key !== this.key()) await writeRows(this.user, this.stamp, {}, { workingDrafts: [row.key] });
    } else {
      if (row.pending) { this.update({ error: 'Thao tác cũ chưa rõ kết quả. Khôi phục rồi gửi lại để đối soát trước khi bỏ bản tại máy.' }); return; }
      await writeRows(this.user, this.stamp, {}, { workingDrafts: [row.key] }); this.update({ recovery: null });
    }
    } catch (e) { this.update({ error: errorMessage(e) }); }
  }
  async useServer() {
    if (!this.state.conflict || this.state.busy) return;
    const view = this.state.conflict; this.update({ view, text: view.work?.text ?? '', pending: null, conflict: null }); await this.persist().catch(e => this.update({ error: errorMessage(e) }));
  }
  async keepLocal() {
    if (!this.state.conflict || this.state.busy || !['DRAFT', 'RETURNED'].includes(this.state.conflict.work?.state ?? '')) return;
    this.update({ view: this.state.conflict, conflict: null, pending: null }); await this.persist().catch(e => this.update({ error: errorMessage(e) }));
    // No automatic overwrite after selecting local text; the learner must save explicitly.
  }
  pauseForAuthentication() { this.authPaused = true; window.clearTimeout(this.localTimer); window.clearTimeout(this.serverTimer); return this.persist(); }
  dispose() { this.disposed = true; window.clearTimeout(this.localTimer); window.clearTimeout(this.serverTimer); }
}
function validView(v: Learner, unit: number) {
  if (!v || v.unitId !== unit || typeof v.available !== 'boolean' || typeof v.unlocked !== 'boolean' || typeof v.blockReason !== 'string' || !Array.isArray(v.history)) return false;
  const d = v.version?.definition;
  if (v.version && (!Number.isSafeInteger(v.version.id) || !d || typeof d.title !== 'string' || typeof d.prompt !== 'string' || !Number.isFinite(d.minWords) || !Number.isFinite(d.maxWords) || !Array.isArray(d.criteria) || d.criteria.length < 1 || d.criteria.length > 20 || !d.criteria.every(c => c && typeof c.id === 'string' && typeof c.text === 'string' && Number.isInteger(c.points)))) return false;
  if (v.work && (!v.version || v.work.versionId !== v.version.id || !Number.isSafeInteger(v.work.id) || !Number.isSafeInteger(v.work.revision) || v.work.revision < 0 || typeof v.work.text !== 'string' || !['DRAFT', 'SUBMITTED', 'RETURNED', 'GRADED'].includes(v.work.state))) return false;
  return v.history.every(s => s && Number.isSafeInteger(s.id) && s.versionId === v.version?.id && typeof s.text === 'string' && typeof s.submittedAt === 'string' && (!s.grade || (Number.isInteger(s.grade.earnedPoints) && s.grade.totalPoints === 10 && typeof s.grade.comment === 'string' && Array.isArray(s.grade.marks) && s.grade.marks.every(m => m && typeof m.criterionId === 'string' && typeof m.met === 'boolean' && typeof m.comment === 'string'))));
}
function validPending(p: Pending) {
  if (!['start', 'draft', 'submit'].includes(p.action) || !p.body || typeof p.body.requestId !== 'string' || !/^[0-9a-f-]{36}$/.test(p.body.requestId)) return false;
  const keys = Object.keys(p.body).sort().join(',');
  return p.action === 'start' ? keys === 'requestId' : keys === 'baseRevision,requestId,text' && Number.isSafeInteger(p.body.baseRevision) && p.body.baseRevision! >= 0 && typeof p.body.text === 'string' && p.body.text.length <= 30000;
}
export function writingSession(user: number, unit: number) { const key = `${user}:${unit}`; if (!sessions.has(key)) sessions.set(key, new WritingSession(user, unit)); return sessions.get(key)!; }
export function clearWritingSessions() { sessions.forEach(s => s.dispose()); sessions.clear(); }
export const writingDirty = () => [...sessions.values()].some(s => s.dirty);
export const writingUnpersisted = () => [...sessions.values()].some(s => s.unpersisted);

export const suspendWritingSessions = () => Promise.all([...sessions.values()].map(s => s.pauseForAuthentication())).then(() => {});
