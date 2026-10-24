import { api, ApiError, requestId } from '../../api';

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
  private pending = new Map<string, { timer: number; subscribed?: number }>();
  private resources = new Set<number>();
  private ready = new Set<number>();
  constructor(private userId: number, private random = Math.random) {}
  start() { window.addEventListener('online', this.wake); window.addEventListener('focus', this.wake); this.connect(); }
  stop() {
    this.stopped = true; this.generation++; window.removeEventListener('online', this.wake); window.removeEventListener('focus', this.wake);
    this.clear(); this.socket?.close(); this.socket = null;
  }
  private clear() {
    window.clearTimeout(this.retry); window.clearTimeout(this.deadline); window.clearInterval(this.beat);
    this.pending.forEach(p => window.clearTimeout(p.timer)); this.pending.clear(); this.ready.clear();
  }
  private wake = () => { recoverLearning(); if (!this.stopped && !this.socket && state !== 'auth-required') { window.clearTimeout(this.retry); this.connect(); } };
  subscribe(id: number) { this.resources.add(id); if (state === 'connected') this.send('SUBSCRIBE', id); }
  unsubscribe(id: number) { this.resources.delete(id); this.ready.delete(id); if (state === 'connected') this.send('UNSUBSCRIBE', id); }
  private send(type: string, attemptId?: number) {
    const socket = this.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    const id = requestId();
    const timer = window.setTimeout(() => { if (socket === this.socket) socket.close(); }, connectionConfig.ackMs);
    this.pending.set(id, { timer, subscribed: type === 'SUBSCRIBE' ? attemptId : undefined });
    socket.send(JSON.stringify({ version: 1, type, requestId: id, ...(attemptId === undefined ? {} : { attemptId }) }));
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
          this.resources.forEach(id => this.send('SUBSCRIBE', id)); recoverLearning();
        }
        if (command.subscribed) { this.ready.add(command.subscribed); window.dispatchEvent(new CustomEvent('attempt-invalidation', { detail: { attemptId: command.subscribed, revision: -1 } })); }
      } else if (m.type === 'ERROR' && m.requestId) {
        const command = this.pending.get(m.requestId); if (command) window.clearTimeout(command.timer); this.pending.delete(m.requestId);
      } else if (m.type === 'ATTEMPT_CHANGED' && Number.isSafeInteger(m.attemptId) && Number.isSafeInteger(m.revision) && this.ready.has(m.attemptId!)) {
        window.dispatchEvent(new CustomEvent('attempt-invalidation', { detail: { attemptId: m.attemptId, revision: m.revision } }));
      }
    };
    socket.onerror = () => socket.close();
    socket.onclose = async event => {
      if (this.stopped || socket !== this.socket) return;
      this.socket = null; this.clear(); publish('interrupted');
      // A restart/failed handshake often closes with 1006, not the explicit 1008.
      // A focus/online event may start a replacement while this check awaits HTTP.
      // Only the current connection generation may expire auth or schedule retries.
      void event;
      try { const user = await api<{ id: number }>('/api/auth/me', 'GET', undefined, true); if (!current()) return; if (user.id !== this.userId) throw new ApiError(401, 'USER_CHANGED', 'Tài khoản đã thay đổi.'); }
      catch (e) { if (!current()) return; if (e instanceof ApiError && e.status === 401) { publish('auth-required'); window.dispatchEvent(new Event('session-expired')); return; } }
      if (current()) this.retry = window.setTimeout(() => { if (current()) this.connect(); }, Math.min(connectionConfig.maxBackoffMs, connectionConfig.backoffMs * 2 ** Math.min(this.retries++, 5)) * (0.75 + this.random() * 0.25));
    };
  }
}
let active: ConnectionManager | null = null;
const watched = new Map<number, number>();
export function startConnections(userId: number) {
  active?.stop(); const manager = new ConnectionManager(userId); active = manager;
  watched.forEach((_, id) => manager.subscribe(id)); manager.start();
  return () => { manager.stop(); if (active === manager) active = null; };
}
export function watchAttempt(id: number) {
  watched.set(id, (watched.get(id) ?? 0) + 1); active?.subscribe(id);
  return () => { const count = (watched.get(id) ?? 1) - 1; if (count) watched.set(id, count); else { watched.delete(id); active?.unsubscribe(id); } };
}
