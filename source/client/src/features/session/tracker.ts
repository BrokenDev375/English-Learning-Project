import { api, ApiError, authenticationEpoch } from '../../api';
import { renewActivityTab } from './tab';

export interface SessionDeadline {
  sessionGeneration: string; serverTime: string; lastInteractionAt: string; idleExpiresAt: string; warningAt: string;
}
export interface IdleView { remaining: number; warning: boolean; uncertain: boolean; busy: boolean; error: string }
let learningContext: { attemptId: number; activity: 'LEARN' | 'REVIEW' | 'RESULT' } | null = null;
export function publishLearningContext(attemptId: number, activity: 'LEARN' | 'REVIEW' | 'RESULT') {
  const value = { attemptId, activity }; learningContext = value;
  window.dispatchEvent(new Event('session-context'));
  return () => { if (learningContext === value) learningContext = null; };
}
export class ActivityTracker {
  private tabId = renewActivityTab();
  private seq = 0;
  private stopped = false;
  private registered = false;
  private pending = false;
  private interacting = false;
  private lastSend = 0;
  private deadline?: SessionDeadline;
  private offset = 0;
  private timer?: number;
  private pulse?: number;
  private queued?: number;
  private channel?: BroadcastChannel;
  private epoch = authenticationEpoch();
  private lastCheck = 0;
  private lastAttempt = -Infinity;
  private latestInteraction = 0;
  private error = '';
  private deniedAttempt: number | null = null;
  constructor(private publish: (view: IdleView) => void) {}
  start() {
    for (const name of ['pointerdown', 'keydown', 'input', 'wheel', 'touchstart']) window.addEventListener(name, this.interaction, { passive: true });
    window.addEventListener('hashchange', this.context); document.addEventListener('visibilitychange', this.context);
    window.addEventListener('app-navigation', this.context); window.addEventListener('popstate', this.context);
    window.addEventListener('session-context', this.context);
    window.addEventListener('online', this.wake); window.addEventListener('focus', this.wake);
    if (typeof BroadcastChannel !== 'undefined') {
      this.channel = new BroadcastChannel('english-session-deadline');
      this.channel.onmessage = e => { if (e.data?.generation === this.deadline?.sessionGeneration) void this.check(); };
    }
    this.timer = window.setInterval(this.render, 1000);
    this.pulse = window.setInterval(() => { if (!document.hidden) void this.check(); else this.render(); }, 15000);
    void this.check();
  }
  stop() {
    this.stopped = true;
    for (const name of ['pointerdown', 'keydown', 'input', 'wheel', 'touchstart']) window.removeEventListener(name, this.interaction);
    window.removeEventListener('hashchange', this.context); document.removeEventListener('visibilitychange', this.context);
    window.removeEventListener('app-navigation', this.context); window.removeEventListener('popstate', this.context);
    window.removeEventListener('session-context', this.context);
    window.removeEventListener('online', this.wake); window.removeEventListener('focus', this.wake);
    window.clearInterval(this.timer); window.clearInterval(this.pulse); window.clearTimeout(this.queued); this.channel?.close();
  }
  private current() { return !this.stopped && this.epoch === authenticationEpoch(); }
  private contextValue() {
    const [path, query] = location.hash.slice(1).split('?'); const p = new URLSearchParams(query);
    const routes: Record<string, string> = { '': 'HOME', '/': 'HOME', '/home': 'HOME', '/profile': 'PROFILE', '/rewards': 'REWARDS', '/league': 'LEADERBOARD', '/leaderboard': 'LEADERBOARD', '/content': 'AUTHORING', '/admin': 'ADMIN_USERS', '/statistics': 'STATISTICS' };
    const id = Number(p.get('attempt'));
    return { activity: id === this.deniedAttempt ? 'OTHER' : path === '/learning' ? (id > 0 ? learningContext?.attemptId === id ? learningContext.activity : p.get('tab') === 'review' ? 'REVIEW' : 'LEARN' : p.get('tab') === 'review' ? 'REVIEW' : 'CATALOG') : routes[path] ?? 'OTHER',
      visibility: document.hidden ? 'HIDDEN' : 'VISIBLE', ...(Number.isSafeInteger(id) && id > 0 && id !== this.deniedAttempt ? { attemptId: id } : {}) };
  }
  private accept(next: SessionDeadline) {
    if (this.deadline && next.sessionGeneration !== this.deadline.sessionGeneration) {
      this.stop(); window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: 'SESSION_CHANGED', epoch: this.epoch } })); return;
    }
    this.error = '';
    // Sequential transport plus server generation; no client timestamps extend a deadline.
    this.deadline = next; this.offset = Date.parse(next.serverTime) - Date.now(); this.lastCheck = Date.now();
  }
  private failure(e: unknown) {
    if (!this.current()) return;
    if (e instanceof ApiError && e.status === 401) {
      this.stop(); window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: e.code, epoch: this.epoch } })); return;
    }
    if (e instanceof ApiError && e.code === 'TAB_EXPIRED') {
      this.registered = false; this.tabId = renewActivityTab(); this.seq = 0;
      this.error = ''; window.clearTimeout(this.queued);
      this.queued = window.setTimeout(() => void this.check(), 0); return;
    }
    if (e instanceof ApiError && e.status === 404 && e.code === 'RESOURCE_NOT_FOUND') {
      this.deniedAttempt = Number(new URLSearchParams(location.hash.split('?')[1]).get('attempt'));
      this.error = ''; window.clearTimeout(this.queued);
      this.queued = window.setTimeout(() => void this.check(), 0); return;
    }
    this.error = 'Chưa xác minh được phiên. Kiểm tra kết nối và thử lại.';
    this.render();
  }
  private remaining() { return this.deadline ? Math.max(0, Math.ceil((Date.parse(this.deadline.idleExpiresAt) - Date.now() - this.offset) / 1000)) : 0; }
  private render = () => {
    if (!this.current()) return;
    this.publish({ remaining: this.remaining(), warning: !!this.deadline && Date.now() + this.offset >= Date.parse(this.deadline.warningAt), uncertain: !!this.error || (!!this.deadline && Date.now() - this.lastCheck > 30000), busy: this.pending, error: this.error });
    if (this.deadline && this.remaining() === 0 && !this.pending && Date.now() - this.lastAttempt >= 15000) void this.check();
  };
  private interaction = (event: Event) => {
    if (!event.isTrusted || !this.current() || document.hidden) return;
    this.interacting = true; this.latestInteraction = Date.now();
    window.clearTimeout(this.queued);
    const delay = this.remaining() <= 30 ? 0 : Math.max(0, 10000 - (Date.now() - this.lastSend));
    this.queued = window.setTimeout(() => void this.report(), delay);
  };
  private context = () => { if (this.registered) void this.report(); else void this.check(); };
  private wake = () => { this.interacting = false; void this.check(); };
  async check() {
    if (!this.current() || this.pending) return;
    this.pending = true; this.lastAttempt = Date.now();
    try {
      const state = await api<SessionDeadline>('/api/auth/session', 'GET', undefined, true);
      if (!this.current()) return;
      this.accept(state);
      if (!this.current()) return;
      const registration = !this.registered;
      const next = await api<SessionDeadline>('/api/auth/activity', 'POST', { tabId: this.tabId, seq: registration ? 0 : ++this.seq, ...this.contextValue(), interaction: false }, true);
      if (!this.current()) return;
      this.registered = true; this.accept(next);
    } catch (e) { this.failure(e); }
    finally { this.pending = false; this.render(); if (this.current() && this.interacting && this.registered) void this.report(); }
  }
  async continueWorking() { this.interacting = true; this.latestInteraction = Date.now(); if (!this.registered) await this.check(); else await this.report(); }
  private async report() {
    if (!this.current() || this.pending || !this.registered) return;
    this.pending = true;
    // Never turn an event buffered during a long network interruption into fresh activity.
    const interaction = this.interacting && Date.now() - this.latestInteraction <= 15000 && navigator.onLine;
    this.interacting = false; this.lastSend = Date.now(); this.render();
    try {
      const next = await api<SessionDeadline>('/api/auth/activity', 'POST', { tabId: this.tabId, seq: ++this.seq, ...this.contextValue(), interaction }, true);
      if (!this.current()) return;
      this.accept(next); if (interaction && this.current()) this.channel?.postMessage({ generation: next.sessionGeneration });
    } catch (e) { this.failure(e); }
    finally { this.pending = false; this.render(); }
  }
}
