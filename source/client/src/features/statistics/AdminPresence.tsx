import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { watchPresence } from '../connection/manager';
import { initials } from '../../components/Brand';
import { roleNames } from '../../types';
import type { Role } from '../../types';
import './AdminPresence.css';

type Presence = { users: { id: number; fullName: string; role: Role }[] };

export default function AdminPresence() {
  const [data, setData] = useState<Presence | null>(null), [error, setError] = useState('');
  useEffect(() => {
    let active = true, pending = false, queued = false;
    async function load() {
      if (!active || document.hidden) return;
      if (pending) { queued = true; return; }
      pending = true;
      try { const next = await api<Presence>('/api/admin/statistics/presence'); if (active) { setData(next); setError(''); } }
      catch (failure) { if (active) setError(errorMessage(failure)); }
      finally { pending = false; if (active && queued) { queued = false; void load(); } }
    }
    const refresh = () => { void load(); };
    refresh();
    const timer = window.setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', refresh); window.addEventListener('online', refresh); window.addEventListener('presence:changed', refresh);
    const unwatch = watchPresence();
    return () => { active = false; unwatch(); window.clearInterval(timer); document.removeEventListener('visibilitychange', refresh); window.removeEventListener('online', refresh); window.removeEventListener('presence:changed', refresh); };
  }, []);
  return <section className="panel admin-presence" aria-label="Người đang online">
    <h2>Người đang online{data && <span className="admin-presence-count" data-testid="online-accounts">{data.users.length}</span>}</h2>
    {data && <ul className="admin-presence-users" aria-label="Danh sách người đang online">{data.users.map(user => <li key={user.id} data-user-id={user.id}><span className="avatar small">{initials(user.fullName)}</span><div><strong>{user.fullName}</strong><small>{roleNames[user.role]}</small></div><i className="admin-presence-dot" aria-label="Online" /></li>)}</ul>}
    {data && !data.users.length && <p className="muted">Chưa có ai online.</p>}
    {error && <p className="message error" role="alert">{data ? 'Chưa thể cập nhật danh sách.' : 'Chưa tải được danh sách online.'}</p>}
    {!data && !error && <p className="muted" role="status">Đang tải…</p>}
  </section>;
}
