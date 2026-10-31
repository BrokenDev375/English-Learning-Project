import { api, ApiError, authenticationEpoch, errorMessage, requestId } from '../../api';

interface Allowance { playsUsed: number; remaining: number; maxPlays: number }
interface Playback { used: number | null; cycle: number; seconds: number; loading: boolean; busy: boolean; pending: boolean; error: string }
/** A granted play survives question/component switches in this authenticated document. */
class AudioSession {
  private value: Playback;
  private listeners = new Set<() => void>();
  private pendingId?: string;
  constructor(private path: string, private start: number, private epoch: number) {
    this.value = { used: null, cycle: 0, seconds: start, loading: false, busy: false, pending: false, error: '' };
  }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  getSnapshot = () => this.value;
  private current() { return this.epoch === authenticationEpoch(); }
  private update(patch: Partial<Playback>) { if (!this.current()) return; this.value = { ...this.value, ...patch }; this.listeners.forEach(l => l()); }
  async load() {
    if (!this.current() || this.value.loading) return;
    this.update({ loading: true });
    try {
      const result = await api<Allowance>(this.path);
      this.update({ used: Math.max(this.value.used ?? 0, result.playsUsed), error: '' });
    } catch (e) { this.update({ error: errorMessage(e) }); }
    finally { this.update({ loading: false }); }
  }
  async reserve() {
    if (!this.current() || this.value.busy) return false;
    if (this.value.cycle) return true; // pause/resume uses the same granted play.
    if (this.value.used === null) { await this.load(); return false; }
    this.pendingId ??= requestId(); this.update({ busy: true, error: '', pending: true });
    try {
      const result = await api<Allowance>(this.path + '/start', 'POST', { requestId: this.pendingId });
      if (!this.current()) return false;
      this.pendingId = undefined;
      this.update({ used: Math.max(this.value.used ?? 0, result.playsUsed), cycle: result.playsUsed, seconds: this.start, pending: false });
      return true;
    } catch (e) {
      if (!this.current()) return false;
      if (e instanceof ApiError && e.status > 0 && e.status < 500) {
        this.pendingId = undefined; this.update({ pending: false });
        if (e.code === 'AUDIO_LIMIT_REACHED') this.update({ used: 2, cycle: 0 });
      }
      this.update({ error: this.pendingId ? 'Chưa xác nhận được lượt nghe. Bấm xác nhận lại khi có kết nối.' : errorMessage(e) });
      return false;
    } finally { this.update({ busy: false }); }
  }
  position(seconds: number) { if (Number.isFinite(seconds) && seconds !== this.value.seconds) this.update({ seconds }); }
  finish(seconds: number) { this.update({ cycle: 0, seconds }); }
  clearError() { this.update({ error: '' }); }
  playbackError() { this.update({ error: 'Chưa phát được audio. Kiểm tra kết nối và tải lại; lượt đã cấp vẫn được giữ.' }); }
}
let epoch = -1;
const sessions = new Map<string, AudioSession>();
export function audioSessionFor(userId: number, attemptId: number, materialId: number, start: number) {
  const current = authenticationEpoch();
  if (epoch !== current) { sessions.clear(); epoch = current; }
  const key = `${userId}:${attemptId}:${materialId}`;
  let session = sessions.get(key);
  if (!session) { session = new AudioSession(`/api/attempts/${attemptId}/audio/${materialId}`, start, current); sessions.set(key, session); }
  return session;
}
