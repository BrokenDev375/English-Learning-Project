import type { MonitoringDetail, MonitoringPage, MonitoredAccount } from '../src/features/admin/sessionMonitoring';

export function monitoredAccount(id: number, name = 'Người đang kết nối'): MonitoredAccount {
  const now = Date.now(), sessionRef = `00000000-0000-4000-8000-${String(id).padStart(12, '0')}`;
  return { user: { userId: id, fullName: name, email: `monitor${id}@test.local`, role: 'STUDENT', accountStatus: 'ACTIVE' }, primaryContext: { activity: 'HOME', attemptId: null, lessonTitle: null, roundNumber: null }, visibility: 'VISIBLE', interactionState: 'ACTIVE', lastInteractionAt: new Date(now).toISOString(), idleExpiresAt: new Date(now + 300000).toISOString(), validSessionCount: 1, tabCount: 1, openConnections: 1, sessions: [{ sessionRef, loginAt: new Date(now - 10000).toISOString(), lastInteractionAt: new Date(now).toISOString(), idleExpiresAt: new Date(now + 300000).toISOString(), interactionState: 'ACTIVE', connected: true, openConnections: 1, tabCount: 1 }] };
}
export function monitoringPage(items: MonitoredAccount[], total = items.length, page = 0): MonitoringPage {
  return { serverTime: new Date().toISOString(), basis: 'LOCAL_BACKEND_LIVE_SOCKET', items, totalElements: total, page, size: 10, summary: { connectedAccounts: total, validSessions: total, tabs: total, openConnections: total, activities: { HOME: total }, idleAccounts: 0 } };
}
export function monitoringDetail(account: MonitoredAccount): MonitoringDetail {
  return { serverTime: new Date().toISOString(), basis: 'LOCAL_BACKEND_LIVE_SOCKET', user: account.user, session: account.sessions[0], tabs: [{ tabRef: 'tab-ref', connected: true, visibility: 'VISIBLE', lastInteractionAt: account.lastInteractionAt, lastSeenAt: account.lastInteractionAt, context: account.primaryContext }] };
}
