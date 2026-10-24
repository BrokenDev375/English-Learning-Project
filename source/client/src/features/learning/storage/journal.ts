import { api, ApiError, requestId } from '../../../api';
import { isCurrent, lease, readRows, userEpoch, writeRows } from './database';
import type { Row } from './database';
import type { Create, Snapshot } from '../types';

export interface CommandRow extends Row { kind: string; body: { requestId: string }; status: 'PREPARED' | 'ACKED'; ack?: unknown; queuedAt?: number }
export function latestCreateCommand(rows: CommandRow[]) {
  return rows.filter(r => r.kind === 'create').sort((a, b) => (b.queuedAt ?? 0) - (a.queuedAt ?? 0))[0];
}
export async function durableCall<T>(user: number, kind: 'create' | 'review-sync', body?: { requestId: string } & Partial<Create>) {
  const stamp = userEpoch(); let result: T | undefined;
  await lease(user, kind, async () => {
    const rows = (await readRows<CommandRow>('commands', user)).filter(r => r.kind === kind);
    const existing = kind === 'create' ? (body ? rows.find(r => r.body?.requestId === body.requestId) : latestCreateCommand(rows)) : rows[0];
    if (kind === 'create' && existing && body && JSON.stringify(existing.body) !== JSON.stringify(body)) throw new Error('Mã yêu cầu đã được dùng cho lựa chọn khác. Chưa gửi yêu cầu.');
    const queuedAt = Math.max(Date.now(), ...rows.map(r => (r.queuedAt ?? 0) + 1));
    const row: CommandRow | null = existing ?? (body ? { key: `${user}:${body.requestId}`, version: 1, userId: user, kind, body: structuredClone(body), status: 'PREPARED', queuedAt } : null);
    function validate(command: CommandRow | null): asserts command is CommandRow {
      if (!command || command.version !== 1 || typeof command.body?.requestId !== 'string' || command.key !== `${user}:${command.body.requestId}`) throw new Error('Thao tác tại máy không hợp lệ. Chưa gửi yêu cầu.');
      if (kind === 'create' && !['LEARN', 'PRACTICE', 'REVIEW'].includes((command.body as { mode?: string }).mode ?? '')) throw new Error('Lựa chọn lượt lưu tại máy không hợp lệ.');
    }
    validate(row);
    // Persist this intent before resolving older commands, including when their
    // response is still unavailable. A reload must recover B rather than replace it with A.
    await writeRows(user, stamp, { commands: [row] });
    async function dispatch(command: CommandRow, older = false): Promise<T | undefined> {
      validate(command);
      await writeRows(user, stamp, { commands: [command] });
      let acked = false;
      try {
        const ack = await api<T>(kind === 'create' ? '/api/attempts' : '/api/me/review/sync', 'POST', command.body, true); acked = true;
        if (!isCurrent(user, stamp)) return;
        await writeRows(user, stamp, { commands: [{ ...command, status: 'ACKED', ack } as CommandRow] });
        if (kind === 'create') await api<Snapshot>(`/api/attempts/${(ack as Snapshot).attemptId}`, 'GET', undefined, true);
        await writeRows(user, stamp, {}, { commands: [command.key] });
        return ack;
      } catch (e) {
        if (kind === 'review-sync' && e instanceof ApiError && e.status === 401 && isCurrent(user, stamp)) window.dispatchEvent(new Event('session-expired'));
        if (!acked && e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) {
          await writeRows(user, stamp, {}, { commands: [command.key] });
          if (older) return; // A definitively rejected older request no longer blocks this intent.
        }
        throw e;
      }
    }
    if (kind === 'create') {
      for (const previous of rows.filter(r => r.key !== row.key).sort((a, b) => (a.queuedAt ?? 0) - (b.queuedAt ?? 0))) {
        if (!isCurrent(user, stamp)) return;
        await dispatch(previous, true);
      }
    }
    if (isCurrent(user, stamp)) result = await dispatch(row);
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
