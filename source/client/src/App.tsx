import { useEffect, useState } from 'react';
import { api, ApiError, errorMessage, resetCsrf, requestId } from './api';
import type { User } from './types';
import { roleNames } from './types';
import { Brand, formatDate, initials } from './components/Brand';
import AuthPage from './features/auth/AuthPage';
import AdminAccounts from './features/admin/AdminAccounts';
import ContentWorkspace from './features/content/ContentWorkspace';
import LearningWorkspace from './features/learning/LearningWorkspace';
import { activateLearningUser, clearLearningSessions, hasUnsavedLearning } from './features/learning/session';
import { navigate, useRouteParam } from './navigation';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true); const [notice, setNotice] = useState('');
  const [routeView] = useRouteParam('view', 'home');
  const view = ['home', 'account', 'admin', 'content', 'learning'].includes(routeView) ? routeView : 'home';
  const setView = (next: string, replace = false) => navigate({ view: next }, { reset: true, replace });
  const [connected, setConnected] = useState(false); const [busy, setBusy] = useState(false);
  async function restore() {
    setLoading(true); setNotice('');
    try { setUser(await api<User>('/api/auth/me', 'GET', undefined, true)); }
    catch (failure) { if (!(failure instanceof ApiError && failure.status === 401)) setNotice(errorMessage(failure)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void restore(); }, []);
  useEffect(() => { if (user) activateLearningUser(user.id); }, [user?.id]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (hasUnsavedLearning()) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, []);
  useEffect(() => {
    const expired = () => { setUser(null); if (!window.location.hash.startsWith('#/learning')) setView('home', true); resetCsrf(); setNotice('Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.'); };
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
    if (hasUnsavedLearning() && !window.confirm('Có thay đổi hoặc thao tác học chưa được xác nhận. Đăng xuất sẽ bỏ dữ liệu chưa đồng bộ trong phiên này. Bạn vẫn muốn đăng xuất?')) return;
    setBusy(true);
    try { await api('/api/auth/logout', 'POST'); clearLearningSessions(); resetCsrf(); setUser(null); setView('home', true); setNotice(''); }
    catch (failure) { setNotice(errorMessage(failure)); } finally { setBusy(false); }
  }
  if (loading) return <div className="loading-screen"><Brand /><p>Đang mở không gian học tập…</p></div>;
  if (!user) return <AuthPage notice={notice} retry={() => void restore()} onLogin={current => { setUser(current); setNotice(''); if (view === 'home') setView(current.role === 'ADMIN' ? 'admin' : 'home', true); }} />;
  return <div className="app-layout">
    <aside className="sidebar"><Brand /><span className="nav-label">KHÔNG GIAN CỦA BẠN</span>
      <nav aria-label="Điều hướng chính">
        <button className={view === 'home' ? 'active' : ''} onClick={() => setView('home')}><span aria-hidden="true">⌂</span>Trang chủ</button>
        <button className={view === 'account' ? 'active' : ''} onClick={() => setView('account')}><span aria-hidden="true">◎</span>Tài khoản</button>
        <button className={view === 'learning' ? 'active' : ''} onClick={() => setView('learning')}><span aria-hidden="true">▦</span>Khám phá bài học</button>
        {user.role !== 'STUDENT' && <button className={view === 'content' ? 'active' : ''} onClick={() => setView('content')}><span aria-hidden="true">▤</span>Quản lý học liệu</button>}
        {user.role === 'ADMIN' && <button className={view === 'admin' ? 'active' : ''} onClick={() => setView('admin')}><span aria-hidden="true">♧</span>Quản lý tài khoản</button>}
      </nav><div className="sidebar-note"><strong>Small steps.<br />Steady progress.</strong><span>Dành một chút thời gian cho bản thân mỗi ngày.</span></div>
      <div className="sidebar-user"><span className="avatar">{initials(user.fullName)}</span><div><strong>{user.fullName}</strong><small>{roleNames[user.role]}</small></div></div>
    </aside>
    <div className="workspace"><header className="topbar"><span>Không gian học tập <span className="slash">/</span> <strong>{view === 'learning' ? 'Bài học' : view === 'content' ? 'Học liệu' : view === 'admin' ? 'Quản trị' : view === 'account' ? 'Tài khoản' : 'Trang chủ'}</strong></span>
      <div className="topbar-actions"><span className={'connection ' + (connected ? 'online' : '')}><i />{connected ? 'Đã kết nối' : 'Đang kết nối lại'}</span><button className="link-button" disabled={busy} onClick={logout}>{busy ? 'Đang thoát…' : 'Đăng xuất'}</button></div></header>
      <main className="main-content">
        {notice && <div className="message error" role="alert">{notice}</div>}
        {view === 'learning' ? <LearningWorkspace key={user.id} user={user} /> : view === 'content' && user.role !== 'STUDENT' ? <ContentWorkspace user={user} /> : view === 'admin' && user.role === 'ADMIN' ? <AdminAccounts currentUser={user} /> : view === 'account' ?
          <section><span className="eyebrow">THÔNG TIN CÁ NHÂN</span><h1>Tài khoản của bạn<span>.</span></h1><p className="muted">Thông tin và quyền truy cập hiện tại của bạn.</p>
            <div className="panel profile-card"><span className="avatar large">{initials(user.fullName)}</span><h2>{user.fullName}</h2><span className="role-pill">{roleNames[user.role]}</span>
              <dl><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Vai trò</dt><dd>{roleNames[user.role]}</dd></div><div><dt>Trạng thái</dt><dd>Hoạt động</dd></div><div><dt>Ngày tham gia</dt><dd>{formatDate(user.createdAt)}</dd></div></dl></div>
          </section> :
          <section><div className="welcome"><span className="eyebrow">MỖI NGÀY, MỘT BƯỚC TIẾN</span><h1>Chào {user.fullName.split(' ').at(-1)}<span>.</span><br />Sẵn sàng cho điều mới?</h1><p>Chào mừng bạn đến với không gian học tiếng Anh của mình.</p><button className="button primary" onClick={() => setView('account')}>Xem tài khoản <span aria-hidden="true">→</span></button><div className="welcome-art" aria-hidden="true"><span>Aa</span><i>hello!</i><b>keep going ↗</b></div></div>
            <div className="home-grid"><article className="panel home-card"><span className="eyebrow">KHÔNG GIAN HỌC TẬP</span><h2>Hành trình của bạn</h2><p className="muted">Khám phá các Unit, xem hướng dẫn và mục tiêu của từng bài đã công bố.</p><button className="link-button" onClick={() => setView('learning')}>Xem danh sách bài →</button></article>
              <article className="panel home-card accent"><span className="eyebrow">TÀI KHOẢN</span><h2>{roleNames[user.role]}</h2><p className="muted">{user.role === 'ADMIN' ? 'Quản lý tài khoản, phân quyền và học liệu của hệ thống.' : user.role === 'TEACHER' ? 'Soạn tài liệu, câu hỏi và bài học có phiên bản.' : 'Tài khoản của bạn đã sẵn sàng để tham gia học tập.'}</p>{user.role !== 'STUDENT' && <button className="link-button" onClick={() => setView('content')}>Quản lý học liệu →</button>}</article></div>
          </section>}
      </main><footer>English Learning <span>·</span> Mỗi ngày, một bước tiến.</footer>
    </div>
  </div>;
}
