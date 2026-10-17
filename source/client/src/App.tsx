import { useEffect, useState } from 'react';
import { api, ApiError, errorMessage, resetCsrf, requestId } from './api';
import type { User } from './types';
import { roleNames } from './types';
import { Brand, formatDate, initials } from './components/Brand';
import AuthPage from './features/auth/AuthPage';
import AdminAccounts from './features/admin/AdminAccounts';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true); const [notice, setNotice] = useState('');
  const [view, setView] = useState<'home' | 'account' | 'admin'>('home');
  const [connected, setConnected] = useState(false); const [busy, setBusy] = useState(false);
  async function restore() {
    setLoading(true); setNotice('');
    try { setUser(await api<User>('/api/auth/me', 'GET', undefined, true)); }
    catch (failure) { if (!(failure instanceof ApiError && failure.status === 401)) setNotice(errorMessage(failure)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void restore(); }, []);
  useEffect(() => {
    const expired = () => { setUser(null); setView('home'); resetCsrf(); setNotice('Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.'); };
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  useEffect(() => {
    if (!user) return;
    let cancelled = false, socket: WebSocket | null = null, retry: number | undefined, beat: number | undefined, attempt = 0;
    function connect() {
      if (cancelled) return;
      socket = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/events');
      socket.onopen = () => {
        attempt = 0; setConnected(true);
        beat = window.setInterval(() => { if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ version: 1, type: 'HEARTBEAT', requestId: requestId() })); }, 30000);
      };
      socket.onclose = event => {
        window.clearInterval(beat); if (cancelled) return; setConnected(false);
        if (event.code === 1008) void api<User>('/api/auth/me').then(current => { if (!cancelled) setUser(current); }).catch(() => {});
        retry = window.setTimeout(connect, Math.min(30000, 1000 * 2 ** Math.min(attempt++, 5)) + Math.random() * 500);
      };
      socket.onerror = () => socket?.close();
    }
    connect();
    return () => { cancelled = true; window.clearTimeout(retry); window.clearInterval(beat); socket?.close(); setConnected(false); };
  }, [user?.id]);
  async function logout() {
    setBusy(true);
    try { await api('/api/auth/logout', 'POST'); resetCsrf(); setUser(null); setView('home'); setNotice(''); }
    catch (failure) { setNotice(errorMessage(failure)); } finally { setBusy(false); }
  }
  if (loading) return <div className="loading-screen"><Brand /><p>Đang mở không gian học tập…</p></div>;
  if (!user) return <AuthPage notice={notice} retry={() => void restore()} onLogin={current => { setUser(current); setNotice(''); setView(current.role === 'ADMIN' ? 'admin' : 'home'); }} />;
  return <div className="app-layout">
    <aside className="sidebar"><Brand /><span className="nav-label">KHÔNG GIAN CỦA BẠN</span>
      <nav aria-label="Điều hướng chính">
        <button className={view === 'home' ? 'active' : ''} onClick={() => setView('home')}><span aria-hidden="true">⌂</span>Trang chủ</button>
        <button className={view === 'account' ? 'active' : ''} onClick={() => setView('account')}><span aria-hidden="true">◎</span>Tài khoản</button>
        {user.role === 'ADMIN' && <button className={view === 'admin' ? 'active' : ''} onClick={() => setView('admin')}><span aria-hidden="true">♧</span>Quản lý tài khoản</button>}
      </nav><div className="sidebar-note"><strong>Small steps.<br />Steady progress.</strong><span>Dành một chút thời gian cho bản thân mỗi ngày.</span></div>
      <div className="sidebar-user"><span className="avatar">{initials(user.fullName)}</span><div><strong>{user.fullName}</strong><small>{roleNames[user.role]}</small></div></div>
    </aside>
    <div className="workspace"><header className="topbar"><span>Không gian học tập <span className="slash">/</span> <strong>{view === 'admin' ? 'Quản trị' : view === 'account' ? 'Tài khoản' : 'Trang chủ'}</strong></span>
      <div className="topbar-actions"><span className={'connection ' + (connected ? 'online' : '')}><i />{connected ? 'Đã kết nối' : 'Đang kết nối lại'}</span><button className="link-button" disabled={busy} onClick={logout}>{busy ? 'Đang thoát…' : 'Đăng xuất'}</button></div></header>
      <main className="main-content">
        {notice && <div className="message error" role="alert">{notice}</div>}
        {view === 'admin' && user.role === 'ADMIN' ? <AdminAccounts currentUser={user} /> : view === 'account' ?
          <section><span className="eyebrow">THÔNG TIN CÁ NHÂN</span><h1>Tài khoản của bạn<span>.</span></h1><p className="muted">Thông tin và quyền truy cập hiện tại của bạn.</p>
            <div className="panel profile-card"><span className="avatar large">{initials(user.fullName)}</span><h2>{user.fullName}</h2><span className="role-pill">{roleNames[user.role]}</span>
              <dl><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Vai trò</dt><dd>{roleNames[user.role]}</dd></div><div><dt>Trạng thái</dt><dd>Hoạt động</dd></div><div><dt>Ngày tham gia</dt><dd>{formatDate(user.createdAt)}</dd></div></dl></div>
          </section> :
          <section><div className="welcome"><span className="eyebrow">MỖI NGÀY, MỘT BƯỚC TIẾN</span><h1>Chào {user.fullName.split(' ').at(-1)}<span>.</span><br />Sẵn sàng cho điều mới?</h1><p>Chào mừng bạn đến với không gian học tiếng Anh của mình.</p><button className="button primary" onClick={() => setView('account')}>Xem tài khoản <span aria-hidden="true">→</span></button><div className="welcome-art" aria-hidden="true"><span>Aa</span><i>hello!</i><b>keep going ↗</b></div></div>
            <div className="home-grid"><article className="panel home-card"><span className="eyebrow">KHÔNG GIAN HỌC TẬP</span><h2>Hành trình của bạn</h2><p className="muted">Các bài học và hoạt động ôn tập đang được chuẩn bị. Bạn có thể kiểm tra thông tin tài khoản trước khi bắt đầu.</p><button className="link-button" onClick={() => setView('account')}>Thông tin tài khoản →</button></article>
              <article className="panel home-card accent"><span className="eyebrow">TÀI KHOẢN</span><h2>{roleNames[user.role]}</h2><p className="muted">{user.role === 'ADMIN' ? 'Quản lý tài khoản, phân quyền và trạng thái truy cập của người dùng.' : user.role === 'TEACHER' ? 'Tài khoản của bạn có quyền biên soạn học liệu khi nội dung được mở.' : 'Tài khoản của bạn đã sẵn sàng để tham gia học tập.'}</p>{user.role === 'ADMIN' && <button className="link-button" onClick={() => setView('admin')}>Quản lý tài khoản →</button>}</article></div>
          </section>}
      </main><footer>English Learning <span>·</span> Mỗi ngày, một bước tiến.</footer>
    </div>
  </div>;
}
