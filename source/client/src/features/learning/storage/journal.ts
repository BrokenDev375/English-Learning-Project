import { api, ApiError, requestId } from '../../../api';
import { isCurrent, lease, readRows, userEpoch, writeRows } from './database';
import type { Row } from './database';
import type { Create, Snapshot } from '../types';

export interface CommandRow extends Row { kind: string; body: { requestId: string }; status: 'PREPARED' | 'ACKED'; ack?: unknown }
export async function durableCall<T>(user: number, kind: 'create' | 'review-sync', body?: { requestId: string } & Partial<Create>) {
  const stamp = userEpoch(); let result: T | undefined;
  await lease(user, kind, async () => {
    const rows = (await readRows<CommandRow>('commands', user)).filter(r => r.kind === kind);
    const row = rows[0] ?? (body ? { key: `${user}:${body.requestId}`, version: 1 as const, userId: user, kind, body, status: 'PREPARED' as const } : null);
    if (!row || row.version !== 1 || typeof row.body?.requestId !== 'string' || row.key !== `${user}:${row.body.requestId}`) throw new Error('Thao tác tại máy không hợp lệ. Chưa gửi yêu cầu.');
    if (kind === 'create' && !['LEARN', 'PRACTICE', 'REVIEW'].includes((row.body as { mode?: string }).mode ?? '')) throw new Error('Lựa chọn lượt lưu tại máy không hợp lệ.');
    await writeRows(user, stamp, { commands: [row] });
    let acked = false;
    try {
      const ack = await api<T>(kind === 'create' ? '/api/attempts' : '/api/me/review/sync', 'POST', row.body, true); acked = true;
      if (!isCurrent(user, stamp)) return;
      await writeRows(user, stamp, { commands: [{ ...row, status: 'ACKED', ack } as CommandRow] });
      if (kind === 'create') await api<Snapshot>(`/api/attempts/${(ack as Snapshot).attemptId}`, 'GET', undefined, true);
      await writeRows(user, stamp, {}, { commands: [row.key] }); result = ack;
    } catch (e) {
      if (kind === 'review-sync' && e instanceof ApiError && e.status === 401 && isCurrent(user, stamp)) window.dispatchEvent(new Event('session-expired'));
      if (!acked && e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) await writeRows(user, stamp, {}, { commands: [row.key] });
      throw e;
    }
  });
  if (result === undefined) throw new Error('Phiên đồng bộ đã thay đổi.');
  return result;
}

const syncing = new Map<number, Promise<void>>();
export function syncReviewAfterSubmission(user: number) {
  if (syncing.has(user)) return syncing.get(user)!;
  const job = (async () => {
    for (let batch = 0; batch < 10; batch++) {
      const reply = await durableCall<{ remainingSubmissions: number }>(user, 'review-sync', { requestId: requestId() });
      if (reply.remainingSubmissions === 0) break;
    }
    window.dispatchEvent(new Event('review-changed'));
  })();
  syncing.set(user, job); void job.finally(() => { if (syncing.get(user) === job) syncing.delete(user); }).catch(() => {});
  return job;
}
