import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, authenticationEpoch, errorMessage } from '../../api';
import type { AccountStatus, Role, User, UserPage } from '../../types';
import { roleNames, statusNames } from '../../types';
import { formatDate, initials } from '../../components/Brand';
import { containDialogFocus } from '../../components/dialogFocus';
import SessionMonitor from './SessionMonitor';

type ActionUser = Pick<User, 'id' | 'fullName' | 'email'>;
type Action = { user: ActionUser; kind: 'role'; value: Role } | { user: ActionUser; kind: 'status'; value: AccountStatus } | { user: ActionUser; kind: 'delete' };
export default function AdminAccounts({ currentUser }: { currentUser: User }) {
  const [query, setQuery] = useState(''); const [search, setSearch] = useState('');
  const [role, setRole] = useState(''); const [status, setStatus] = useState('');
  const [page, setPage] = useState(0); const [revision, setRevision] = useState(0);
  const [data, setData] = useState<UserPage | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [message, setMessage] = useState(''); const [action, setAction] = useState<Action | null>(null);
  const [busy, setBusy] = useState(false); const [actionError, setActionError] = useState('');
  const [uncertain, setUncertain] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    const params = new URLSearchParams({ q: search, page: String(page), size: '10' });
    if (role) params.set('role', role); if (status) params.set('status', status);
    api<UserPage>('/api/admin/users?' + params).then(result => { if (active) setData(result); })
      .catch(failure => { if (active) setError(errorMessage(failure)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [search, role, status, page, revision]);
  useEffect(() => {
    if (!action) return;
    setActionError(''); setUncertain(false);
    const element = dialog.current!, opener = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; element.showModal();
    return () => { element.close(); document.body.style.overflow = overflow; if (opener?.isConnected) opener.focus(); };
  }, [action]);
  function filter(event: FormEvent) { event.preventDefault(); setPage(0); setSearch(query.trim()); setRevision(v => v + 1); }
  function completed(target: Action, reconciled = false) {
    setMessage((reconciled ? 'Đã đối soát và xác nhận cập nhật tài khoản ' : 'Đã cập nhật tài khoản ') + target.user.email + '.');
    setAction(null); setUncertain(false); setRevision(v => v + 1);
  }
  async function reconcile(target: Action, manual = false) {
    const epoch = authenticationEpoch();
    const params = new URLSearchParams({ q: target.user.email.slice(0, 100), size: '50', page: '0' });
    const result = await api<UserPage>('/api/admin/users?' + params);
    if (!mounted.current || epoch !== authenticationEpoch()) return;
    const current = result.items.find(u => u.id === target.user.id);
    const matches = current && (target.kind === 'status' ? current.status === target.value : target.kind === 'role' ? current.role === target.value : current.status === 'DELETED');
    if (matches) { completed(target, true); return; }
    setRevision(v => v + 1); setUncertain(!manual);
    setActionError(current ? 'Máy chủ chưa ghi nhận thay đổi này. Bạn có thể kiểm tra lại hoặc xác nhận để thử lại thao tác.' : 'Chưa xác minh được trạng thái tài khoản. Hãy kiểm tra lại trước khi tiếp tục.');
    if (!current) setUncertain(true);
  }
  async function checkAction() {
    if (!action) return; setBusy(true);
    try { await reconcile(action, true); } catch { setActionError('Chưa kết nối được để xác minh kết quả. Hãy kiểm tra trạng thái lại khi có mạng.'); }
    finally { setBusy(false); }
  }
  async function confirm() {
    if (!action) return; setBusy(true); setActionError(''); setMessage('');
    const epoch = authenticationEpoch();
    try {
      const path = '/api/admin/users/' + action.user.id;
      if (action.kind === 'delete') await api(path, 'DELETE');
      else if (action.kind === 'role') await api(path + '/role', 'PATCH', { role: action.value });
      else await api(path + '/status', 'PATCH', { status: action.value });
      if (mounted.current && epoch === authenticationEpoch()) completed(action);
    } catch (failure) {
      if (!mounted.current || epoch !== authenticationEpoch()) return;
      if (failure instanceof ApiError && (failure.status === 0 || failure.status >= 500)) {
        setUncertain(true);
        try { await reconcile(action); }
        catch { setActionError('Chưa nhận được xác nhận. Thao tác có thể đã hoàn tất; hãy kiểm tra trạng thái khi có kết nối.'); }
      } else setActionError(errorMessage(failure));
    } finally { setBusy(false); }
  }
  const actionTitle = !action ? '' : action.kind === 'delete' ? 'Xóa tài khoản?' : action.kind === 'role' ? 'Thay đổi quyền?' : action.value === 'LOCKED' ? 'Khóa tài khoản?' : 'Mở khóa tài khoản?';
  return <section>
    <div className="page-heading"><h1>Quản lý tài khoản<span>.</span></h1></div>
    <SessionMonitor key={currentUser.id} currentUserId={currentUser.id} revision={revision} busy={busy} onLock={user => setAction({ user: { id: user.userId, fullName: user.fullName, email: user.email }, kind: 'status', value: 'LOCKED' })} />
    {message && <div className="message success" role="status">{message}</div>}
    {error && <div className="message error" role="alert">{error}</div>}
    <section className="panel" aria-labelledby="account-list-title">
      <h2 className="account-list-heading" id="account-list-title">Danh sách tài khoản</h2>
      <form className="filters" onSubmit={filter}>
        <label className="search-label">Tìm tài khoản<input value={query} maxLength={100} onChange={e => setQuery(e.target.value)} placeholder="Tên hoặc email…" /></label>
        <label>Vai trò<select value={role} onChange={e => { setRole(e.target.value); setPage(0); }}><option value="">Tất cả vai trò</option>{Object.entries(roleNames).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></label>
        <label>Trạng thái<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(statusNames).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></label>
        <button className="button primary" disabled={loading}>Tìm kiếm</button>
      </form>
      <div className="table-wrap" aria-busy={loading}>
        <table><thead><tr><th>Tài khoản</th><th>Vai trò</th><th>Trạng thái</th><th>Ngày tạo</th><th>Thao tác</th></tr></thead>
          <tbody>{data?.items.map(user => <tr key={user.id}>
            <td><div className="user-cell"><span className="avatar small">{initials(user.fullName)}</span><div><strong>{user.fullName}{user.id === currentUser.id && <small className="you-label"> (bạn)</small>}</strong><span>{user.email}</span></div></div></td>
            <td><select aria-label={'Vai trò của ' + user.email} value={user.role} disabled={loading || user.id === currentUser.id || user.status === 'DELETED'}
              onChange={e => setAction({ user, kind: 'role', value: e.target.value as Role })}>{Object.entries(roleNames).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></td>
            <td><span className={'status-pill ' + user.status.toLowerCase()}>{statusNames[user.status]}</span></td>
            <td className="no-wrap">{formatDate(user.createdAt)}</td>
            <td>{user.id === currentUser.id || user.status === 'DELETED' ? <span className="muted">—</span> : <div className="row-actions">
              <button className="link-button" disabled={loading} onClick={() => setAction({ user, kind: 'status', value: user.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE' })}>{user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button>
              <button className="link-button danger-text" disabled={loading} onClick={() => setAction({ user, kind: 'delete' })}>Xóa</button>
            </div>}</td>
          </tr>)}</tbody>
        </table>
        {loading && <p className="table-message" role="status">Đang tải tài khoản…</p>}
        {!loading && !data?.items.length && <p className="table-message">Không tìm thấy tài khoản phù hợp.</p>}
      </div>
      <div className="pagination"><span>{data?.totalElements ?? 0} tài khoản · Trang {page + 1}/{Math.max(data?.totalPages ?? 0, 1)}</span><div>
        <button className="button secondary compact" disabled={loading || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button>
        <button className="button secondary compact" disabled={loading || page + 1 >= (data?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button>
      </div></div>
    </section>
    {action && <dialog className="modal admin-confirm" ref={dialog} aria-labelledby="action-title" aria-describedby="action-description" onKeyDown={containDialogFocus}
      onCancel={e => { e.preventDefault(); if (!busy) setAction(null); }}>
      <h2 id="action-title">{actionTitle}</h2>
      <p><strong>{action.user.fullName}</strong><br /><span className="muted">{action.user.email}</span></p>
      <p id="action-description">{action.kind === 'delete' ? 'Tài khoản sẽ không thể đăng nhập. Lịch sử được giữ lại; tài khoản đã xóa không được mở lại.' : action.kind === 'role' ? 'Quyền mới: ' + roleNames[action.value] + '. Người dùng cần đăng nhập lại để sử dụng quyền mới.' : action.value === 'LOCKED' ? 'Người dùng sẽ bị đăng xuất và không thể đăng nhập cho tới khi được mở khóa.' : 'Người dùng có thể đăng nhập lại sau khi mở khóa.'}</p>
      {actionError && <div className="message error" role="alert">{actionError}</div>}
      <div className="modal-actions"><button className="button secondary" disabled={busy} onClick={() => setAction(null)} autoFocus>Hủy</button>{uncertain ? <button className="button primary" disabled={busy} onClick={checkAction}>{busy ? 'Đang kiểm tra…' : 'Kiểm tra trạng thái'}</button> : <button className={'button ' + (action.kind === 'delete' ? 'danger' : 'primary')} disabled={busy} onClick={confirm}>{busy ? 'Đang xử lý…' : 'Xác nhận'}</button>}</div>
    </dialog>}
  </section>;
}
