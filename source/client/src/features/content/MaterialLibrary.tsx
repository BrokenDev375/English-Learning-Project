import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import type { CatalogPage } from './catalog';
import type { MaterialRoot, MaterialSummary } from './materials';
import { materialKinds, materialStatuses } from './materials';
import MaterialEditor from './MaterialEditor';

export default function MaterialLibrary({ user }: { user: User }) {
  const [scope, setScope] = useState(user.role === 'ADMIN' ? 'ALL' : 'OWN'), [kind, setKind] = useState(''), [status, setStatus] = useState('');
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<CatalogPage<MaterialSummary> | null>(null), [loading, setLoading] = useState(true);
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [opening, setOpening] = useState(false);
  const [item] = useRouteParam('item');
  const [editor, setEditor] = useState<{ route: string; root: MaterialRoot | null; session: number } | null>(null);
  useEffect(() => {
    if (!item || editor?.route === item) { setOpening(false); return; }
    let active = true;
    if (item === 'new') { setOpening(false); setEditor({ route: item, root: null, session: Date.now() }); return; }
    setOpening(true); setError('');
    void api<MaterialRoot>('/api/author/materials/' + encodeURIComponent(item))
      .then(root => { if (active) setEditor({ route: item, root, session: Date.now() }); })
      .catch(failure => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setOpening(false); });
    return () => { active = false; };
  }, [item]);
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    const params = new URLSearchParams({ scope, q: search, page: String(page), size: '9' });
    if (kind) params.set('kind', kind); if (status) params.set('status', status);
    api<CatalogPage<MaterialSummary>>('/api/author/materials?' + params).then(value => { if (active) setData(value); })
      .catch(failure => { if (active) setError(errorMessage(failure)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [scope, kind, status, search, page, refresh]);
  function open(id: number) { setNotice(''); navigate({ item: String(id) }); }
  if (item && editor?.route === item) return <MaterialEditor key={editor.session} initial={editor.root} user={user} onRoot={root => { setEditor(current => current ? { ...current, route: String(root.id), root } : current); navigate({ item: String(root.id) }, { replace: true }); }} onClose={message => { setEditor(null); setNotice(message ?? ''); returnTo({ item: null }); }} onChanged={() => setRefresh(value => value + 1)} />;
  if (item) return <section className="panel material-editor">{error ? <div className="message error" role="alert">{error}</div> : <p role="status">Đang mở tài liệu…</p>}<button className="button secondary" onClick={() => returnTo({ item: null })}>Về danh sách</button></section>;
  return <section>
    <div className="page-heading"><div><span className="eyebrow">KHO TÀI LIỆU</span><h1>Tài liệu học tập<span>.</span></h1><p className="muted">Soạn đoạn đọc, thêm audio và ảnh để dùng chung cho các câu hỏi.</p></div>
      <button className="button primary" onClick={() => { setNotice(''); navigate({ item: 'new' }); }}>+ Thêm tài liệu</button></div>
    {notice && <div className="message success" role="status">{notice}</div>}
    {error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(value => value + 1)}>Tải lại</button></div>}
    <div className="panel catalog-browser">
      <form className="filters material-filters" onSubmit={event => { event.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(value => value + 1); }}>
        <label className="search-label">Tìm tài liệu<input value={query} maxLength={100} placeholder="Tìm theo tên tài liệu…" onChange={event => setQuery(event.target.value)} /></label>
        <label>Loại<select value={kind} onChange={event => { setKind(event.target.value); setPage(0); }}><option value="">Tất cả loại</option>{Object.entries(materialKinds).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Trạng thái<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(materialStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Phạm vi<select value={scope} onChange={event => { setScope(event.target.value); setPage(0); }}><option value="OWN">Của tôi</option><option value="PUBLISHED">Kho đã xuất bản</option>{user.role === 'ADMIN' && <option value="ALL">Tất cả tác giả</option>}</select></label>
        <button className="button secondary" disabled={loading}>Tìm kiếm</button>
      </form>
      {loading ? <p className="table-message" role="status">Đang tải tài liệu…</p> : !error && !data?.items.length ? <div className="catalog-empty"><strong>Chưa có tài liệu phù hợp</strong><p className="muted">Thêm tài liệu đầu tiên hoặc thay đổi bộ lọc.</p></div> : null}
      {!loading && !error && <div className="catalog-grid">{data?.items.map(item => <article className="catalog-card material-card" key={item.id}>
        <div className="material-card-top"><span className="material-kind-icon" aria-hidden="true">{item.kind === 'READING' ? 'Aa' : item.kind === 'AUDIO' ? '♫' : '▧'}</span>
          <span className={'material-status ' + item.status.toLowerCase()}>{materialStatuses[item.status]}</span></div>
        <span className="eyebrow">{materialKinds[item.kind]}</span><h2>{item.title || 'Tài liệu chưa đặt tên'}</h2>
        <p className="muted">{item.draftVersionId && item.publishedVersionId ? 'Có bản nháp chỉnh sửa bên cạnh bản đã xuất bản.' : item.status === 'DRAFT' ? 'Đang biên soạn, chưa đưa vào kho dùng chung.' : item.status === 'RETIRED' ? 'Giữ lịch sử; không cấp cho nội dung mới.' : 'Có thể tái sử dụng trong nội dung học tập.'}</p>
        <div className="catalog-card-footer"><span>{item.ownerId === user.id ? 'Nội dung của bạn' : 'Tác giả khác'}</span>
          <button className="link-button" disabled={opening} aria-label={'Mở ' + (item.title || 'tài liệu chưa đặt tên')} onClick={() => void open(item.id)}>Mở tài liệu →</button></div>
      </article>)}</div>}
      <div className="pagination"><span>{data?.totalElements ?? 0} tài liệu · Trang {page + 1}/{Math.max(data?.totalPages ?? 0, 1)}</span><div>
        <button className="button secondary compact" disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Trước</button>
        <button className="button secondary compact" disabled={loading || page + 1 >= (data?.totalPages ?? 0)} onClick={() => setPage(value => value + 1)}>Sau</button>
      </div></div>
    </div>
  </section>;
}
