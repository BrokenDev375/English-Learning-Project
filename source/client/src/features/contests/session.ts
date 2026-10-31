import { api, ApiError, errorMessage, requestId } from '../../api';
import { isCurrent, readRows, userEpoch, writeRows, writerId } from '../learning/storage/database';
import type { Row } from '../learning/storage/database';
import type { Answer } from '../learning/types';
import type { Learner, Answers } from './types';
type Action = 'register' | 'check-in' | 'claim' | 'save' | 'submit' | 'visibility';
interface Pending { action: Action; body: { requestId: string; clientId?: string; baseRevision?: number; answers?: Answers; hidden?: boolean } }
interface Local extends Row { kind: 'contest'; contestId: number; writerId: string; updatedAt: number; view: Learner; answers: Answers; pending: Pending | null; visibility: boolean[] }
interface State { view: Learner | null; answers: Answers; busy: boolean; error: string; pending: Pending | null; conflict: Learner | null; recovery: Local | null; active: boolean; localSaved: boolean }
const same = (a: unknown, b: unknown) => JSON.stringify(a ?? {}) === JSON.stringify(b ?? {});
const sessions = new Map<string, ContestSession>();
export class ContestSession {
  private state: State = { view: null, answers: {}, busy: false, error: '', pending: null, conflict: null, recovery: null, active: false, localSaved: true };
  private listeners = new Set<() => void>(); private stamp = userEpoch(); private disposed = false; private authPaused = false; private timer = 0; private heartbeat = 0;
  private visibility: boolean[] = []; private attached = 0; private loading = false;
  private lane: Promise<void> = Promise.resolve(); private needsLocal = false;
  constructor(private user: number, private contest: number) {}
  private current = () => !this.disposed && !this.authPaused && isCurrent(this.user, this.stamp);
  getSnapshot = () => this.state;
  subscribe = (f: () => void) => { this.listeners.add(f); return () => { this.listeners.delete(f); }; };
  get dirty() { return !!this.state.pending || this.visibility.length > 0 || !same(this.state.answers, this.state.view?.answers); }
  get unpersisted() { return this.needsLocal; }
  private update(next: Partial<State>) { if (!this.current()) return; this.state = { ...this.state, ...next }; this.listeners.forEach(f => f()); }
  private key() { return `contest:${this.user}:${this.contest}:${writerId()}`; }
  private persist() {
    const { view, answers, pending } = this.state, visibility = [...this.visibility], dirty = this.dirty, key = this.key();
    this.needsLocal = true;
    this.lane = this.lane.catch(() => {}).then(async () => {
      if (this.disposed || !isCurrent(this.user, this.stamp) || !view) return;
      await writeRows(this.user, this.stamp, dirty ? { workingDrafts: [{ key, userId: this.user, version: 1, kind: 'contest', contestId: this.contest, writerId: writerId(), updatedAt: Date.now(), view, answers, pending, visibility } as Local] } : {}, dirty ? {} : { workingDrafts: [key] });
    });
    const latest = this.lane;
    return latest.then(() => { if (latest === this.lane) { this.needsLocal = false; this.update({ localSaved: true }); } });
  }
  private accept(view: Learner, preserve: boolean) {
    const old = this.state.view;
    if (old?.participation && view.participation && view.participation.revision < old.participation.revision) return;
    if (preserve && view.paper && old && !same(view.answers, old.answers) && !same(view.answers, this.state.answers)) { this.update({ conflict: view }); return; }
    const closed = !!view.participation?.submitted || view.participation?.status !== 'APPROVED' || ['FINALIZED', 'ARCHIVED', 'CANCELLED', 'CLOSING'].includes(view.contest.state);
    this.update({ view, ...(preserve && !closed ? {} : { answers: view.answers ?? {} }), ...(closed ? { active: false } : {}) });
  }
  async load(initial = false) {
    if (!this.current() || this.loading || this.state.busy) return;
    this.loading = true;
    try {
      const view = await api<Learner>(`/api/me/contests/${this.contest}`); if (!this.current()) return;
      if (initial && !this.state.view) {
        const rows = await readRows<Local>('workingDrafts', this.user); if (!this.current()) return;
        const recovery = rows.filter(r => r.kind === 'contest' && r.contestId === this.contest && r.view?.contest?.id === this.contest && r.key === `contest:${this.user}:${this.contest}:${r.writerId}` && r.answers && typeof r.answers === 'object' && Array.isArray(r.visibility) && r.visibility.every(v => typeof v === 'boolean') && (!r.pending || (['register','check-in','claim','save','submit','visibility'].includes(r.pending.action) && /^[0-9a-f-]{36}$/.test(r.pending.body?.requestId))))
          .sort((a,b) => Number(b.writerId === writerId()) - Number(a.writerId === writerId()) || b.updatedAt - a.updatedAt)[0] ?? null;
        this.update({ view, answers: view.answers ?? {}, recovery });
      } else this.accept(view, this.dirty);
      this.syncVisibility();
    } catch (e) { this.update({ error: errorMessage(e) }); }
    finally { this.loading = false; }
  }
  edit(id: string, answer: Answer | null) {
    if (!this.current() || !this.state.active || this.state.busy || this.state.pending || this.state.recovery || this.state.conflict) return;
    this.update({ answers: { ...this.state.answers, [id]: answer }, localSaved: false });
    void this.persist().catch(e => this.update({ error: errorMessage(e) }));
    window.clearTimeout(this.timer); this.timer = window.setTimeout(() => { void this.send('save'); }, 1000);
  }
  async send(action: Action) {
    if (!this.current() || this.state.busy || this.state.recovery || this.state.conflict || !this.state.view) return;
    window.clearTimeout(this.timer);
    const pending: Pending = this.state.pending ?? { action, body: { requestId: requestId(), ...(action === 'register' ? {} : { clientId: writerId() }), ...(['save','submit'].includes(action) ? { baseRevision: this.state.view.participation!.revision, answers: this.state.answers } : {}), ...(action === 'visibility' ? { hidden: this.visibility[0] } : {}) } };
    if (pending.action === 'visibility' && pending.body.hidden === undefined) return;
    this.update({ busy: true, pending, error: '' });
    let failed = false;
    try {
      await this.persist(); if (!this.current()) return;
      const view = await api<Learner>(`/api/me/contests/${this.contest}/${pending.action}`, 'POST', pending.body); if (!this.current()) return;
      if (pending.action === 'visibility') this.visibility.shift();
      const preserve = ['visibility','claim','check-in'].includes(pending.action);
      this.update({ pending: null }); this.accept(view, preserve);
      if (['check-in','claim'].includes(pending.action)) this.update({ active: true });
      // A receipt can contain an earlier snapshot after a lost acknowledgement.
      await this.persist(); await this.loadAfterCommand(); this.syncVisibility();
    } catch (e) {
      failed = true;
      if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) {
        if (pending.action === 'visibility') this.visibility.shift();
        this.update({ pending: null });
        try {
          const fresh = await api<Learner>(`/api/me/contests/${this.contest}`); if (!this.current()) return;
          if (e.code === 'CONTEST_REVISION_CONFLICT') this.update({ conflict: fresh }); else this.accept(fresh, this.dirty);
          if (e.code === 'CONTEST_OTHER_TAB') this.update({ active: false });
        } catch { /* Retain the last confirmed view and local answers. */ }
        await this.persist().catch(() => {});
      }
      this.update({ error: errorMessage(e) });
    } finally { this.update({ busy: false }); }
    if (!failed && this.current()) {
      if (this.visibility.length && this.state.active) void this.send('visibility');
      else if (this.state.active && this.dirty && !this.state.pending) this.timer = window.setTimeout(() => { void this.send('save'); }, 300);
    }
  }
  private async loadAfterCommand() {
    const view = await api<Learner>(`/api/me/contests/${this.contest}`); if (this.current()) this.accept(view, this.dirty);
  }
  async pulse() {
    if (this.state.pending || this.visibility.length) { if (!this.state.busy && !this.state.recovery && !this.state.conflict) await this.send(this.state.pending?.action ?? 'visibility'); return; }
    if (!this.current() || this.state.busy || this.state.pending || this.state.recovery || this.state.conflict) return;
    if (!this.state.active) { await this.load(); return; }
    try { const view = await api<Learner>(`/api/me/contests/${this.contest}/heartbeat`, 'POST', { clientId: writerId() }); if (this.current()) { this.accept(view, this.dirty); this.syncVisibility(); } }
    catch (e) { this.update({ error: errorMessage(e) }); if (e instanceof ApiError && e.status >= 400 && e.status < 500) { this.update({ active: false }); await this.load(); } }
  }
  leave(hidden: boolean) {
    if (!this.current() || !this.state.active || this.state.view?.contest.state !== 'RUNNING' || this.state.recovery || this.state.conflict) return;
    // Compare against the last queued signal, or the server after reload/claim.
    // A local default of false would leave a returning candidate marked hidden.
    if (hidden === (this.visibility.at(-1) ?? this.state.view.participation?.tabHidden)) return;
    this.visibility.push(hidden);
    void this.persist().then(() => this.send('visibility')).catch(e => this.update({ error: errorMessage(e) }));
  }
  private syncVisibility() { this.leave(this.attached === 0 || document.hidden); }
  attach() {
    this.attached++;
    if (this.attached === 1) { void this.load(true); this.heartbeat = window.setInterval(() => { void this.pulse(); }, 15000); this.leave(document.hidden); }
    return () => { if (--this.attached === 0) { this.leave(true); window.clearInterval(this.heartbeat); } };
  }
  async recover(keep: boolean) {
    const row = this.state.recovery; if (!row || !this.current()) return;
    if (!keep && row.pending) { this.update({ error: 'Cần khôi phục và gửi lại thao tác chưa rõ kết quả trước khi bỏ bản tại máy.' }); return; }
    try {
      if (keep) {
        if (row.writerId !== writerId() && navigator.locks) {
          const available = await navigator.locks.request('learning-writer:' + row.writerId, { ifAvailable: true }, lock => !!lock);
          if (!available) { this.update({ error: 'Tab chứa bản lưu này vẫn đang mở. Trở lại hoặc đóng tab đó trước khi khôi phục ở đây.' }); return; }
        }
        this.visibility = row.visibility;
        this.update({ view: row.view, answers: row.answers, pending: row.pending, recovery: null, localSaved: true }); await this.persist();
      } else this.update({ recovery: null });
      if (!keep || row.key !== this.key()) await writeRows(this.user, this.stamp, {}, { workingDrafts: [row.key] });
    } catch (e) { this.update({ error: errorMessage(e) }); }
  }
  async resolve(keep: boolean) {
    const fresh = this.state.conflict; if (!fresh) return;
    this.update({ view: fresh, answers: keep && fresh.paper ? this.state.answers : fresh.answers ?? {}, conflict: null, pending: null });
    await this.persist().catch(e => this.update({ error: errorMessage(e) }));
  }
  pauseForAuthentication() { this.authPaused = true; window.clearTimeout(this.timer); window.clearInterval(this.heartbeat); return this.persist(); }
  dispose() { this.disposed = true; window.clearTimeout(this.timer); window.clearInterval(this.heartbeat); }
}
export function contestSession(user: number, contest: number) { const key = `${user}:${contest}`; if (!sessions.has(key)) sessions.set(key, new ContestSession(user, contest)); return sessions.get(key)!; }
export function clearContestSessions() { sessions.forEach(s => s.dispose()); sessions.clear(); }
export const contestDirty = () => [...sessions.values()].some(s => s.dirty);
export const contestUnpersisted = () => [...sessions.values()].some(s => s.unpersisted);

export const suspendContestSessions = () => Promise.all([...sessions.values()].map(s => s.pauseForAuthentication())).then(() => {});
