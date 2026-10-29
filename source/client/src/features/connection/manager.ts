import { api, ApiError, requestId } from '../../api';
import { activityTabId } from '../session/tab';

export type ConnectionState = 'connecting' | 'connected' | 'interrupted' | 'auth-required';
export const connectionConfig = { heartbeatMs: 25000, ackMs: 10000, backoffMs: 1000, maxBackoffMs: 30000, pollMs: 30000 };
let state: ConnectionState = 'connecting';
const listeners = new Set<() => void>();
export const subscribeConnection = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
export const connectionSnapshot = () => state;
function publish(next: ConnectionState) { state = next; listeners.forEach(l => l()); }
export function recoverLearning() { window.dispatchEvent(new Event('learning-recover')); }

/** One manager per authenticated user. HTTP remains available while the socket is down. */
export class ConnectionManager {
  private socket: WebSocket | null = null;
  private stopped = false;
  private retry?: number;
  private beat?: number;
  private deadline?: number;
  private retries = 0;
  private generation = 0;
  private pending = new Map<string, { timer: number; subscribed?: number; presence?: boolean }>();
  private presenceWanted = false;
  private presenceReady = false;
  private resources = new Set<number>();
  private ready = new Set<number>();
  constructor(private userId: number, private random = Math.random, private student = false) {}
  private tabChanged = () => { if (state === 'connected') this.send('HEARTBEAT'); };
  start() { window.addEventListener('session-tab-changed', this.tabChanged); window.addEventListener('online', this.wake); window.addEventListener('focus', this.wake); this.connect(); }
  stop() {
    this.stopped = true; this.generation++; window.removeEventListener('online', this.wake); window.removeEventListener('focus', this.wake);
    window.removeEventListener('session-tab-changed', this.tabChanged);
    this.clear(); this.socket?.close(); this.socket = null;
  }
  private clear() {
    window.clearTimeout(this.retry); window.clearTimeout(this.deadline); window.clearInterval(this.beat);
    this.pending.forEach(p => window.clearTimeout(p.timer)); this.pending.clear(); this.ready.clear(); this.presenceReady = false;
  }
  private wake = () => { recoverLearning(); if (!this.stopped && !this.socket && state !== 'auth-required') { window.clearTimeout(this.retry); this.connect(); } };
  subscribe(id: number) { this.resources.add(id); if (state === 'connected') this.send('SUBSCRIBE', id); }
  unsubscribe(id: number) { this.resources.delete(id); this.ready.delete(id); if (state === 'connected') this.send('UNSUBSCRIBE', id); }
  get presenceLive() { return this.presenceReady && state === 'connected'; }
  watchPresence(enabled: boolean) {
    this.presenceWanted = enabled;
    if (!enabled) { this.presenceReady = false; publish(state); }
    if (state === 'connected') this.send(enabled ? 'SUBSCRIBE' : 'UNSUBSCRIBE', undefined, 'PRESENCE');
  }
  private send(type: string, attemptId?: number, resource?: 'GAMIFICATION' | 'LEAGUE' | 'PRESENCE') {
    const socket = this.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    const id = requestId();
    const timer = window.setTimeout(() => { if (socket === this.socket) socket.close(); }, connectionConfig.ackMs);
    this.pending.set(id, { timer, subscribed: type === 'SUBSCRIBE' ? attemptId : undefined, presence: type === 'SUBSCRIBE' && resource === 'PRESENCE' });
    socket.send(JSON.stringify({ version: 1, type, requestId: id, ...(attemptId === undefined ? {} : { attemptId }), ...(resource ? { resource } : {}), ...(['HELLO', 'HEARTBEAT'].includes(type) ? { tabId: activityTabId() } : {}) }));
  }
  private connect() {
    if (this.stopped || this.socket) return;
    const generation = ++this.generation;
    const current = () => !this.stopped && generation === this.generation;
    publish('connecting');
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/events`);
    this.socket = socket;
    this.deadline = window.setTimeout(() => socket.close(), connectionConfig.ackMs);
    socket.onmessage = e => {
      if (this.stopped || this.socket !== socket) return;
      let m: { version?: number; type?: string; userId?: number; requestId?: string; attemptId?: number; revision?: number };
      try { m = JSON.parse(e.data); } catch { socket.close(); return; }
      if (m.version !== 1) { socket.close(); return; }
      if (m.type === 'CONNECTED') {
        if (m.userId !== this.userId) { socket.close(); return; }
        window.clearTimeout(this.deadline); this.send('HELLO');
      } else if (m.type === 'ACK' && m.requestId && this.pending.has(m.requestId)) {
        const command = this.pending.get(m.requestId)!; window.clearTimeout(command.timer); this.pending.delete(m.requestId);
        if (state !== 'connected') {
          this.retries = 0; publish('connected');
          this.beat = window.setInterval(() => this.send('HEARTBEAT'), connectionConfig.heartbeatMs);
          this.resources.forEach(id => this.send('SUBSCRIBE', id));
          if (this.student) {
            for (const resource of ['GAMIFICATION', 'LEAGUE'] as const) this.send('SUBSCRIBE', undefined, resource);
            window.dispatchEvent(new Event('gamification:changed')); window.dispatchEvent(new Event('league:changed'));
          }
          if (this.presenceWanted) this.send('SUBSCRIBE', undefined, 'PRESENCE');
          recoverLearning();
        }
        if (command.subscribed) { this.ready.add(command.subscribed); window.dispatchEvent(new CustomEvent('attempt-invalidation', { detail: { attemptId: command.subscribed, revision: -1 } })); }
        if (command.presence && this.presenceWanted) { this.presenceReady = true; publish(state); window.dispatchEvent(new Event('presence:changed')); }
      } else if (m.type === 'ERROR' && m.requestId) {
        const command = this.pending.get(m.requestId); if (command) window.clearTimeout(command.timer); this.pending.delete(m.requestId);
      } else if (m.type === 'ATTEMPT_CHANGED' && Number.isSafeInteger(m.attemptId) && Number.isSafeInteger(m.revision) && this.ready.has(m.attemptId!)) {
        window.dispatchEvent(new CustomEvent('attempt-invalidation', { detail: { attemptId: m.attemptId, revision: m.revision } }));
      } else if (this.student && m.type === 'GAMIFICATION_CHANGED') {
        window.dispatchEvent(new Event('gamification:changed'));
      } else if (this.student && (m.type === 'LEAGUE_CHANGED' || m.type === 'LEADERBOARD_CHANGED')) {
        window.dispatchEvent(new Event('league:changed'));
      } else if (m.type === 'PRESENCE_CHANGED' && this.presenceWanted && this.presenceReady) {
        window.dispatchEvent(new Event('presence:changed'));
      }
    };
    socket.onerror = () => socket.close();
    socket.onclose = async event => {
      if (this.stopped || socket !== this.socket) return;
      this.socket = null; this.clear(); publish('interrupted');
      // A restart/failed handshake often closes with 1006, not the explicit 1008.
      // A focus/online event may start a replacement while this check awaits HTTP.
      // Only the current connection generation may expire auth or schedule retries.
      if (event.reason === 'IDLE_TIMEOUT') {
        publish('auth-required'); window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: 'SESSION_IDLE_TIMEOUT' } })); return;
      }
      try { const user = await api<{ id: number }>('/api/auth/me', 'GET', undefined, true); if (!current()) return; if (user.id !== this.userId) throw new ApiError(401, 'USER_CHANGED', 'Tài khoản đã thay đổi.'); }
      catch (e) { if (!current()) return; if (e instanceof ApiError && e.status === 401) { publish('auth-required'); window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: e.code } })); return; } }
      if (current()) this.retry = window.setTimeout(() => { if (current()) this.connect(); }, Math.min(connectionConfig.maxBackoffMs, connectionConfig.backoffMs * 2 ** Math.min(this.retries++, 5)) * (0.75 + this.random() * 0.25));
    };
  }
}
let active: ConnectionManager | null = null;
const watched = new Map<number, number>();
let presenceWatchers = 0;
export const presenceSnapshot = () => active?.presenceLive ?? false;
export function watchPresence() {
  presenceWatchers++; if (presenceWatchers === 1) active?.watchPresence(true);
  return () => { if (--presenceWatchers === 0) active?.watchPresence(false); };
}
export function startConnections(userId: number, student = false) {
  active?.stop(); const manager = new ConnectionManager(userId, Math.random, student); active = manager;
  watched.forEach((_, id) => manager.subscribe(id)); manager.start();
  if (presenceWatchers) manager.watchPresence(true);
  return () => { manager.stop(); if (active === manager) active = null; };
}
export function watchAttempt(id: number) {
  watched.set(id, (watched.get(id) ?? 0) + 1); active?.subscribe(id);
  return () => { const count = (watched.get(id) ?? 1) - 1; if (count) watched.set(id, count); else { watched.delete(id); active?.unsubscribe(id); } };
}
