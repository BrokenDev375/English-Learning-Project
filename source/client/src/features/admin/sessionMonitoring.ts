import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage } from '../../api';
import type { Role } from '../../types';

export const activityNames = { HOME: 'Trang chủ', LEARN: 'Học bài', REVIEW: 'Ôn tập', RESULT: 'Xem kết quả', CATALOG: 'Khám phá bài học', ACCOUNT: 'Tài khoản', PROFILE: 'Hồ sơ học tập', REWARDS: 'Phần thưởng', LEADERBOARD: 'Bảng xếp hạng', AUTHORING: 'Biên soạn', ADMIN_USERS: 'Quản lý tài khoản', STATISTICS: 'Thống kê', OTHER: 'Chưa rõ chức năng' };
export type Activity = keyof typeof activityNames;
export interface MonitoredUser { userId: number; fullName: string; email: string; role: Role; accountStatus: 'ACTIVE' }
export interface ActivityContext { activity: Activity; attemptId: number | null; lessonTitle: string | null; roundNumber: number | null }
export interface MonitoredSession { sessionRef: string; loginAt: string; lastInteractionAt: string; idleExpiresAt: string; interactionState: 'ACTIVE' | 'IDLE'; connected: boolean; openConnections: number; tabCount: number }
export interface MonitoredAccount { user: MonitoredUser; primaryContext: ActivityContext; visibility: 'VISIBLE' | 'HIDDEN' | 'UNKNOWN'; interactionState: 'ACTIVE' | 'IDLE'; lastInteractionAt: string; idleExpiresAt: string; validSessionCount: number; tabCount: number; openConnections: number; sessions: MonitoredSession[] }
export interface MonitoringPage { serverTime: string; basis: string; summary: { connectedAccounts: number; validSessions: number; tabs: number; openConnections: number; activities: Partial<Record<Activity, number>>; idleAccounts: number }; items: MonitoredAccount[]; totalElements: number; page: number; size: number }
export interface MonitoringDetail { serverTime: string; basis: string; user: MonitoredUser; session: MonitoredSession; tabs: { tabRef: string; connected: boolean; visibility: 'VISIBLE' | 'HIDDEN'; lastInteractionAt: string | null; lastSeenAt: string; context: ActivityContext }[] }

/** One request in flight, with a single queued refresh; obsolete filters cannot replace current data. */
export function useMonitoring<T>(url: string, revision = 0) {
  const [state, setState] = useState<{ url: string; data: T | null; loading: boolean; error: string; missing: boolean }>({ url, data: null, loading: true, error: '', missing: false });
  const current = useRef(url), generation = useRef(0), mounted = useRef(false), pending = useRef(false), queued = useRef(false);
  const refresh = useCallback(async () => {
    if (!mounted.current || document.hidden) return;
    if (pending.current) { queued.current = true; return; }
    pending.current = true;
    const target = current.current, epoch = generation.current;
    setState(s => ({ ...s, loading: true }));
    try {
      const data = await api<T>(target);
      if (mounted.current && epoch === generation.current) setState({ url: target, data, loading: false, error: '', missing: false });
    } catch (failure) {
      if (mounted.current && epoch === generation.current) {
        const missing = failure instanceof ApiError && failure.status === 404;
        setState(s => ({ ...s, url: target, data: missing ? null : s.data, loading: false, error: errorMessage(failure), missing }));
      }
    } finally {
      pending.current = false;
      if (mounted.current && epoch === generation.current) setState(s => ({ ...s, loading: false }));
      if (mounted.current && queued.current) { queued.current = false; void refresh(); }
    }
  }, []);
  useEffect(() => {
    mounted.current = true;
    const load = () => { void refresh(); };
    const timer = window.setInterval(load, 15000);
    window.addEventListener('presence:changed', load); window.addEventListener('online', load); document.addEventListener('visibilitychange', load);
    return () => { mounted.current = false; generation.current++; queued.current = false; window.clearInterval(timer); window.removeEventListener('presence:changed', load); window.removeEventListener('online', load); document.removeEventListener('visibilitychange', load); };
  }, [refresh]);
  useEffect(() => {
    current.current = url; generation.current++;
    setState(s => s.url === url ? s : { url, data: null, loading: true, error: '', missing: false });
    void refresh();
  }, [url, revision, refresh]);
  return { ...(state.url === url ? state : { data: null, loading: true, error: '', missing: false }), refresh };
}

export function useServerNow(serverTime?: string) {
  const [tick, setTick] = useState(Date.now()), offset = useRef(0);
  useEffect(() => { if (serverTime) offset.current = Date.parse(serverTime) - Date.now(); }, [serverTime]);
  useEffect(() => { const timer = window.setInterval(() => setTick(Date.now()), 1000); return () => window.clearInterval(timer); }, []);
  return tick + offset.current;
}
export function elapsed(time: string | null, now: number) {
  if (!time) return 'Chưa có tương tác';
  const seconds = Math.max(0, Math.floor((now - Date.parse(time)) / 1000));
  return seconds < 5 ? 'Vừa xong' : seconds < 60 ? `${seconds} giây trước` : `${Math.floor(seconds / 60)} phút trước`;
}
export function dateTime(time: string) { return new Date(time).toLocaleString('vi-VN'); }
