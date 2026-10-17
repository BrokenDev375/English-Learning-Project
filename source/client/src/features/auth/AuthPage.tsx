import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, errorMessage, resetCsrf } from '../../api';
import type { User } from '../../types';
import { Brand } from '../../components/Brand';

export default function AuthPage({ onLogin, notice, retry }: {
  onLogin: (user: User) => void; notice: string; retry: () => void;
}) {
  const [register, setRegister] = useState(false);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess(''); setFields({});
    try {
      if (register) {
        await api('/api/auth/register', 'POST', { fullName, email, password }, true);
        setRegister(false); setPassword(''); setSuccess('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.');
      } else {
        const user = await api<User>('/api/auth/login', 'POST', { email, password }, true);
        resetCsrf(); onLogin(user);
      }
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError) setFields(failure.fields);
    } finally { setBusy(false); }
  }
  return <div className="auth-layout">
    <aside className="auth-story"><Brand />
      <div className="story-copy"><span className="eyebrow">MỞ THÊM MỘT CƠ HỘI</span>
        <h1>Tiếng Anh tốt hơn.<br />Từng ngày một.</h1>
        <p>Bắt đầu từ những điều nhỏ, tạo thói quen học tập và tiến xa hơn theo cách của bạn.</p>
        <div className="word-card"><span>Word of the day</span><strong>begin</strong><small>/bɪˈɡɪn/ · bắt đầu</small>
          <p>Every journey begins with a small step.</p></div>
      </div><p className="story-footer">A little practice goes a long way.</p>
    </aside>
    <main className="auth-main"><section className="auth-card">
      <span className="eyebrow">CHÀO MỪNG BẠN</span>
      <h2>{register ? 'Bắt đầu hành trình.' : 'Rất vui được gặp bạn.'}</h2>
      <p className="muted">{register ? 'Tạo tài khoản người học miễn phí.' : 'Đăng nhập để mở không gian học tập của bạn.'}</p>
      <div className="tabs" role="tablist" aria-label="Đăng nhập hoặc đăng ký">
        <button role="tab" aria-selected={!register} disabled={busy} onClick={() => { setRegister(false); setError(''); setSuccess(''); }}>Đăng nhập</button>
        <button role="tab" aria-selected={register} disabled={busy} onClick={() => { setRegister(true); setError(''); setSuccess(''); }}>Đăng ký</button>
      </div>
      {notice && <div className="message warning" role="alert">{notice}<button type="button" className="link-button" onClick={retry}>Thử kết nối lại</button></div>}
      {error && <div className="message error" role="alert">{error}</div>}
      {success && <div className="message success" role="status">{success}</div>}
      <form onSubmit={submit}>
        {register && <label>Họ tên<input value={fullName} onChange={e => setFullName(e.target.value)} required minLength={2} maxLength={100} autoComplete="name" disabled={busy} />{fields.fullName && <small className="field-error">{fields.fullName}</small>}</label>}
        <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} required maxLength={254} autoComplete="email" disabled={busy} placeholder="ban@example.com" />{fields.email && <small className="field-error">{fields.email}</small>}</label>
        <label>Mật khẩu<input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={register ? 8 : undefined} maxLength={72} autoComplete={register ? 'new-password' : 'current-password'} disabled={busy} />{fields.password && <small className="field-error">{fields.password}</small>}</label>
        {register && <p className="help">Mật khẩu từ 8 ký tự, tối đa 72 byte UTF-8.</p>}
        <button className="button primary full" disabled={busy}>{busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}<span aria-hidden="true">→</span></button>
      </form>
      <p className="auth-note">Học một chút hôm nay. Tự tin hơn ngày mai.</p>
    </section></main>
  </div>;
}
