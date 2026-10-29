import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { roleNames } from '../../types';
import { initials } from '../../components/Brand';
import { containDialogFocus } from '../../components/dialogFocus';
import { watchPresence } from '../connection/manager';
import { activityNames, dateTime, elapsed, useMonitoring, useServerNow } from './sessionMonitoring';
import type { ActivityContext, MonitoredAccount, MonitoredUser, MonitoringDetail, MonitoringPage } from './sessionMonitoring';
import './SessionMonitor.css';

function ContextLabel({ context }: { context: ActivityContext }) {
  return <><strong>{activityNames[context.activity]}</strong>{context.lessonTitle && <small>{context.roundNumber != null && `Vòng ${context.roundNumber} · `}{context.lessonTitle}</small>}</>;
}
function SessionDetail({ account, onClose, revision }: { account: MonitoredAccount; onClose: () => void; revision: number }) {
  const [ref, setRef] = useState(account.sessions[0].sessionRef);
  const { data, error, missing, loading, refresh } = useMonitoring<MonitoringDetail>('/api/admin/sessions/' + encodeURIComponent(ref), revision);
  const now = useServerNow(data?.serverTime), dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current!, opener = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; element.showModal();
    return () => { element.close(); document.body.style.overflow = overflow; if (opener?.isConnected) opener.focus(); };
  }, []);
  return <dialog className="session-drawer" ref={dialog} aria-labelledby="session-detail-title" onKeyDown={containDialogFocus} onCancel={e => { e.preventDefault(); onClose(); }}>
    <header><div><h2 id="session-detail-title">Chi tiết phiên</h2><p>{account.user.fullName}<small>{account.user.email}</small></p></div><button className="button secondary compact" onClick={onClose} autoFocus>Đóng</button></header>
    <div className="session-drawer-body">
      <label>Chọn phiên<select value={ref} onChange={e => setRef(e.target.value)}>{account.sessions.map((s, i) => <option key={s.sessionRef} value={s.sessionRef}>Phiên {i + 1} · Đăng nhập {dateTime(s.loginAt)}</option>)}</select></label>
      {missing ? <p role="status" className="message">Phiên đã kết thúc. Người dùng cần đăng nhập lại để tạo phiên mới.</p> : <>
        {error && <div className="message error" role="alert">{data ? 'Chưa cập nhật chi tiết phiên. Dữ liệu dưới đây là lần đọc gần nhất.' : 'Chưa tải được chi tiết phiên.'} <button className="link-button" onClick={() => void refresh()}>Thử lại</button></div>}
        {!data && loading && <p role="status">Đang tải chi tiết phiên…</p>}
        {data && <>
          <p className="session-read-time">Cập nhật lúc {new Date(data.serverTime).toLocaleTimeString('vi-VN')}{error && ' · Chưa cập nhật'}</p>
          <dl className="session-facts">
            <div><dt>Kết nối</dt><dd>{data.session.connected ? `${data.session.openConnections} kết nối đang sống` : 'Không có kết nối sống'}</dd></div>
            <div><dt>Đăng nhập</dt><dd>{dateTime(data.session.loginAt)}</dd></div>
            <div><dt>Tương tác gần nhất</dt><dd>{dateTime(data.session.lastInteractionAt)}<small>{elapsed(data.session.lastInteractionAt, now)}</small></dd></div>
            <div><dt>Hết phiên nếu không tương tác</dt><dd>{dateTime(data.session.idleExpiresAt)}</dd></div>
          </dl>
          {!data.session.connected && <p className="session-note">Phiên HTTP vẫn còn hạn tại lần cập nhật này. Mất kết nối không đồng nghĩa đã đăng xuất.</p>}
          <h3>Các tab được ghi nhận <span>({data.tabs.length})</span></h3>
          {!data.tabs.length && <p>Chưa có thông tin tab còn hiệu lực.</p>}
          <ol className="session-tabs">{data.tabs.map((tab, i) => <li key={tab.tabRef}>
            <div className="session-tab-heading"><strong>Tab {i + 1}</strong><span>{tab.connected ? 'Có kết nối' : 'Không có kết nối'}</span></div>
            <div className="session-context"><ContextLabel context={tab.context} /></div>
            <p>{tab.visibility === 'VISIBLE' ? 'Tab đang hiển thị' : 'Tab ở nền'}</p>
            <p>Tương tác: {elapsed(tab.lastInteractionAt, now)}<br />Ghi nhận: {elapsed(tab.lastSeenAt, now)}</p>
          </li>)}</ol>
        </>}
      </>}
    </div>
  </dialog>;
}

export default function SessionMonitor({ currentUserId, onLock, revision, busy }: { currentUserId: number; onLock: (user: MonitoredUser) => void; revision: number; busy: boolean }) {
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [role, setRole] = useState(''), [activity, setActivity] = useState(''), [page, setPage] = useState(0);
  const [selected, setSelected] = useState<MonitoredAccount | null>(null), [searchRevision, setSearchRevision] = useState(0);
  const params = new URLSearchParams({ page: String(page), size: '10' });
  if (search) params.set('q', search); if (role) params.set('role', role); if (activity) params.set('activity', activity);
  const { data, error, loading, refresh } = useMonitoring<MonitoringPage>('/api/admin/sessions?' + params, revision + searchRevision);
  const now = useServerNow(data?.serverTime);
  useEffect(() => watchPresence(), []);
  useEffect(() => { if (data && !data.items.length && page > 0 && page * data.size >= data.totalElements) setPage(Math.max(0, Math.ceil(data.totalElements / data.size) - 1)); }, [data, page]);
  function filter(e: FormEvent) { e.preventDefault(); setSearch(query.trim()); setPage(0); setSearchRevision(v => v + 1); }
  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;
  return <section className="panel session-monitor" aria-labelledby="session-monitor-title">
    <div className="session-monitor-heading"><div><h2 id="session-monitor-title">Giám sát hoạt động</h2><p>Tài khoản đang online và các phiên làm việc hiện tại.</p></div><span className="session-read-time">{data ? `${error ? 'Chưa cập nhật · ' : ''}Cập nhật lúc ${new Date(data.serverTime).toLocaleTimeString('vi-VN')}` : 'Đang kết nối dữ liệu'}</span></div>
    <dl className="session-summary" aria-label="Tổng quan hoạt động">
      <div><dt>Tài khoản online</dt><dd data-testid="online-accounts">{data?.summary.connectedAccounts ?? '—'}</dd></div>
      <div><dt>Đang học bài</dt><dd>{data ? data.summary.activities.LEARN ?? 0 : '—'}</dd></div>
      <div><dt>Đang ôn tập</dt><dd>{data ? data.summary.activities.REVIEW ?? 0 : '—'}</dd></div>
      <div><dt>Không tương tác ≥ 1 phút</dt><dd>{data?.summary.idleAccounts ?? '—'}</dd></div>
    </dl>
    <form className="filters session-filters" aria-label="Bộ lọc giám sát" onSubmit={filter}>
      <label className="search-label">Tìm người đang online<input value={query} maxLength={100} onChange={e => setQuery(e.target.value)} placeholder="Tên hoặc email…" /></label>
      <label>Vai trò giám sát<select value={role} onChange={e => { setRole(e.target.value); setPage(0); }}><option value="">Tất cả vai trò</option>{Object.entries(roleNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Chức năng<select value={activity} onChange={e => { setActivity(e.target.value); setPage(0); }}><option value="">Tất cả chức năng</option>{Object.entries(activityNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <button className="button primary" type="submit">Lọc hoạt động</button>
    </form>
    <p className="session-scope">Tổng số theo bộ lọc giám sát. “Không tương tác” tính theo phiên; trạng thái tab do trình duyệt báo.</p>
    {error && <div className="message error session-error" role="alert">{data ? 'Chưa cập nhật hoạt động. Đang giữ dữ liệu lần đọc gần nhất.' : 'Chưa tải được hoạt động.'} <button className="link-button" onClick={() => void refresh()}>Thử lại</button></div>}
    <div className="table-wrap" role="region" aria-label="Bảng giám sát" tabIndex={0} aria-busy={loading}>
      <table><thead><tr><th>Tài khoản</th><th>Hoạt động</th><th>Tương tác gần nhất</th><th>Phiên / tab</th><th>Thao tác</th></tr></thead>
        <tbody>{data?.items.map(account => <tr key={account.user.userId} data-user-id={account.user.userId}>
          <td><div className="user-cell"><span className="avatar small">{initials(account.user.fullName)}</span><div><strong>{account.user.fullName}{account.user.userId === currentUserId && ' (bạn)'}</strong><span>{account.user.email}</span><small>{roleNames[account.user.role]}</small></div></div></td>
          <td><div className="session-context"><ContextLabel context={account.primaryContext} /><small>{account.visibility === 'HIDDEN' ? 'Tab ở nền' : account.visibility === 'VISIBLE' ? 'Tab đang hiển thị' : 'Chưa có thông tin tab'}</small></div></td>
          <td><time dateTime={account.lastInteractionAt} title={dateTime(account.lastInteractionAt)}>{elapsed(account.lastInteractionAt, now)}</time><small className="session-interaction">{now - Date.parse(account.lastInteractionAt) >= 60000 ? 'Không tương tác' : 'Có tương tác gần đây'}</small></td>
          <td><span className="session-counts">{account.validSessionCount} phiên · {account.tabCount} tab</span><small>{account.openConnections} kết nối</small></td>
          <td><div className="row-actions"><button className="link-button" disabled={!account.sessions.length} onClick={() => setSelected(account)}>Xem phiên</button><button className="link-button danger-text" disabled={busy || !!error || account.user.userId === currentUserId} onClick={() => onLock(account.user)} title={account.user.userId === currentUserId ? 'Không thể tự khóa tài khoản' : undefined}>Khóa</button></div></td>
        </tr>)}</tbody>
      </table>
      {!data && loading && <p className="table-message" role="status">Đang tải hoạt động…</p>}
      {data && !data.items.length && <p className="table-message">{search || role || activity ? 'Không có tài khoản online phù hợp bộ lọc.' : 'Chưa có tài khoản online.'}</p>}
    </div>
    <div className="pagination"><span>{data ? `${data.totalElements} tài khoản · Trang ${page + 1}/${totalPages}` : 'Chưa có dữ liệu'}</span><div><button className="button secondary compact" disabled={loading || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={loading || !data || page + 1 >= totalPages} onClick={() => setPage(v => v + 1)}>Sau</button></div></div>
    {selected && <SessionDetail account={selected} onClose={() => setSelected(null)} revision={revision} />}
  </section>;
}
