import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import type { CatalogItem, CatalogKind, CatalogPage, Topic } from './catalog';
import { catalogNames, itemName } from './catalog';
import { roundHeading } from './roundNumbering';
import './catalog.css';

interface FormValues { code: string; name: string; description: string; topicId: string; stage: string; order: string; roundNumber: string; assignedTeacherId: string }
interface Editor { kind: CatalogKind; item: CatalogItem | null; values: FormValues }
interface Pending { path: string; method: string; body: Record<string, unknown> }

async function allTopics(): Promise<Topic[]> {
  const result: Topic[] = [];
  let page = 0, pages = 1;
  while (page < pages) {
    const response = await api<CatalogPage<Topic>>('/api/author/topics?scope=ALL&size=50&page=' + page++);
    result.push(...response.items); pages = response.totalPages;
  }
  return result;
}

export default function ContentCatalog({ user, assignmentMode = false }: { user: User; assignmentMode?: boolean }) {
  const [routeKind] = useRouteParam('catalog', 'units');
  const kind: CatalogKind = assignmentMode ? 'units' : ['topics', 'objectives', 'units'].includes(routeKind) ? routeKind as CatalogKind : 'units';
  const [editorRoute] = useRouteParam('catalogEditor');
  const [query, setQuery] = useState(''), [search, setSearch] = useState('');
  const [scope, setScope] = useState(user.role === 'ADMIN' || kind !== 'units' ? 'ALL' : 'OWN');
  const canManage = user.role === 'ADMIN';
  const [teachers, setTeachers] = useState<{ id: number; fullName: string }[]>([]);
  const [topicFilter, setTopicFilter] = useState(''), [page, setPage] = useState(0);
  const [topics, setTopics] = useState<Topic[]>([]), [pickerError, setPickerError] = useState('');
  const [data, setData] = useState<CatalogPage<CatalogItem> | null>(null);
  const [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [notice, setNotice] = useState(''), [refresh, setRefresh] = useState(0);
  const [editor, setEditor] = useState<Editor | null>(null), [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(''), [retryPending, setRetryPending] = useState(false);
  const pending = useRef<Pending | null>(null);
  const editorHeading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let active = true; setPickerError('');
    if (user.role === 'ADMIN') void (async () => {
      const rows: { id: number; fullName: string }[] = [];
      for (let page = 0; ; page++) {
        const data = await api<CatalogPage<{ id: number; fullName: string }>>('/api/author/teachers?size=50&page=' + page);
        rows.push(...data.items); if (page + 1 >= data.totalPages) break;
      }
      if (active) setTeachers(rows);
    })().catch(e => { if (active) setPickerError(errorMessage(e)); });
    allTopics().then(value => { if (active) setTopics(value); })
      .catch(failure => { if (active) setPickerError(errorMessage(failure)); });
    return () => { active = false; };
  }, [refresh]);
  useEffect(() => {
    let active = true; setLoading(true); setError(''); setData(null);
    const params = new URLSearchParams({ q: search, scope, page: String(page), size: '9' });
    if (kind !== 'topics' && topicFilter) params.set('topicId', topicFilter);
    api<CatalogPage<CatalogItem>>('/api/author/' + kind + '?' + params)
      .then(value => { if (active) setData(value); })
      .catch(failure => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [kind, search, scope, topicFilter, page, refresh]);
  useEffect(() => { if (editor) editorHeading.current?.focus(); }, [editor?.kind, editor?.item?.id]);
  useEffect(() => {
    if (!canManage) { setEditor(null); return; }
    if (!editorRoute) { setEditor(null); pending.current = null; setRetryPending(false); return; }
    if (editor?.kind === kind && editorRoute === (editor.item ? String(editor.item.id) : 'new')) return;
    if (editorRoute === 'new') { open(null, false); return; }
    let active = true;
    setEditor(null); setError('');
    void (async () => {
      if (!/^[1-9][0-9]*$/.test(editorRoute)) throw new Error('Địa chỉ nội dung không hợp lệ.');
      for (let index = 0; ; index++) {
        const result = await api<CatalogPage<CatalogItem>>('/api/author/' + kind + '?scope=' + (user.role === 'ADMIN' ? 'ALL' : 'OWN') + '&size=50&page=' + index);
        const item = result.items.find(row => String(row.id) === editorRoute);
        if (item) { if (active) open(item, false); return; }
        if (index + 1 >= result.totalPages) throw new Error('Không tìm thấy nội dung được phép chỉnh sửa.');
      }
    })().catch(failure => { if (active) setError(errorMessage(failure)); });
    return () => { active = false; };
  }, [editorRoute, kind, canManage]);

  function open(item: CatalogItem | null, recordHistory = true) {
    if (!canManage) return;
    pending.current = null; setRetryPending(false); setSaveError(''); setNotice('');
    if (recordHistory) navigate({ catalogEditor: item ? String(item.id) : 'new' });
    setEditor({ kind, item, values: {
      code: item && 'code' in item ? item.code : '', name: item ? itemName(item) : '',
      description: item?.description ?? '', topicId: item && 'topicId' in item ? String(item.topicId) : topicFilter,
      stage: item && 'stage' in item ? String(item.stage) : '1',
      order: item && 'sortOrder' in item ? String(item.sortOrder + 1) : '1',
      roundNumber: item && 'roundNumber' in item ? String(item.roundNumber) : '',
      assignedTeacherId: item && 'assignedTeacherId' in item && item.assignedTeacherId ? String(item.assignedTeacherId) : '',
    } });
  }
  function change(field: keyof FormValues, value: string) {
    setEditor(current => current ? { ...current, values: { ...current.values, [field]: value } } : null);
  }
  function switchKind(value: CatalogKind) {
    if (value !== kind) navigate({ catalog: value === 'units' ? null : value, catalogEditor: null });
    setPage(0); setQuery(''); setSearch(''); setTopicFilter(''); setNotice('');
    setScope(value === 'units' && user.role !== 'ADMIN' ? 'OWN' : 'ALL');
  }
  async function save(event: FormEvent) {
    event.preventDefault(); if (!editor || saving || !canManage) return;
    setSaving(true); setSaveError('');
    if (!pending.current) {
      const { values, item, kind: currentKind } = editor;
      const body: Record<string, unknown> = { requestId: requestId(), description: values.description.trim() };
      if (item) body.baseRevision = item.revision;
      if (currentKind === 'units') {
        if (values.roundNumber.trim()) body.roundNumber = Number(values.roundNumber);
        if (values.assignedTeacherId) body.assignedTeacherId = Number(values.assignedTeacherId); else body.clearAssignment = true;
        Object.assign(body, { topicId: Number(values.topicId), title: values.name.trim(), stage: Number(values.stage), sortOrder: Number(values.order) - 1 });
      } else {
        body.name = values.name.trim();
        if (!item) body.code = values.code;
        if (currentKind === 'objectives' && !item) body.topicId = Number(values.topicId);
      }
      pending.current = { path: '/api/author/' + currentKind + (item ? '/' + item.id : ''), method: item ? 'PUT' : 'POST', body };
    }
    try {
      const command = pending.current;
      const saved = await api<CatalogItem>(command.path, command.method, command.body);
      pending.current = null; setRetryPending(false); setEditor(null); returnTo({ catalogEditor: null }); setPage(0);
      setNotice((assignmentMode ? 'Đã phân công ' : 'Đã lưu ') + itemName(saved) + '.'); setRefresh(value => value + 1);
    } catch (failure) {
      const ambiguous = failure instanceof ApiError && (failure.status === 0 || failure.status === 503 || failure.status >= 500);
      setRetryPending(ambiguous);
      if (!ambiguous) pending.current = null;
      setSaveError(errorMessage(failure));
    } finally { setSaving(false); }
  }
  const topicName = (id: number) => topics.find(topic => topic.id === id)?.name ?? 'Chủ đề chưa tải được';
  const allowAll = kind !== 'units' || user.role === 'ADMIN';
  return <section>
    <div className="page-heading"><div><span className="eyebrow">KHÔNG GIAN BIÊN SOẠN</span>
      <h1>{assignmentMode ? 'Phân công biên soạn' : user.role === 'TEACHER' && kind === 'units' ? 'Vòng được giao' : 'Quản lý học liệu'}<span>.</span></h1><p className="muted">{assignmentMode ? 'Chọn vòng học và giao cho giảng viên phụ trách.' : user.role === 'TEACHER' && kind === 'units' ? 'Chọn vòng được giao để soạn bài và gửi duyệt.' : 'Sắp xếp chủ đề, mục tiêu học và các vòng đấu trong lộ trình.'}</p></div>
      {assignmentMode && <div className="catalog-workflow-actions"><button className="button secondary" onClick={() => navigate({ view: 'content' }, { reset: true })}>Quản lý vòng học</button><button className="button primary" onClick={() => navigate({ view: 'reviews' }, { reset: true })}>Xem bài chờ duyệt</button></div>}
      {canManage && !assignmentMode && <button className="button primary" disabled={!!editor || (kind !== 'topics' && (!topics.length || !!pickerError))} onClick={() => open(null)}>+ Thêm {catalogNames[kind].toLowerCase()}</button>}
    </div>
    {!assignmentMode && <div className="catalog-tabs" role="tablist" aria-label="Danh mục học liệu">
      {(Object.keys(catalogNames) as CatalogKind[]).map(value => <button key={value} role="tab" aria-selected={kind === value}
        disabled={!!editor} onClick={() => switchKind(value)}>{catalogNames[value]}</button>)}
    </div>}
    {notice && <div className="message success" role="status">{notice}</div>}
    {pickerError && <div className="message error" role="alert">Chưa tải được chủ đề: {pickerError} <button className="link-button" onClick={() => setRefresh(value => value + 1)}>Thử lại</button></div>}
    {!canManage && <p className="message">{kind === 'units' ? 'Bạn được biên soạn bài trong các vòng đã phân công. Quản trị viên quản lý tên, chủ đề, số thứ tự và phân công vòng.' : 'Chủ đề do quản trị viên quản lý; bạn có thể chọn chủ đề khi biên soạn.'}</p>}
    {editor && <div className="panel catalog-editor">
      <h2 ref={editorHeading} tabIndex={-1}>{assignmentMode && editor.item ? 'Phân công ' + itemName(editor.item) : (editor.item ? 'Chỉnh sửa ' : 'Thêm ') + catalogNames[editor.kind].toLowerCase()}</h2>
      <form onSubmit={save}>
        <fieldset disabled={saving || retryPending} className="catalog-fields">
          {(!assignmentMode || !editor.item) && <>
          {editor.kind !== 'topics' && <label>Chủ đề<select value={editor.values.topicId} required disabled={editor.kind === 'objectives' && !!editor.item}
            onChange={event => change('topicId', event.target.value)}><option value="">Chọn chủ đề</option>{topics.map(topic => <option key={topic.id} value={topic.id}>{topic.name}</option>)}</select></label>}
          {editor.kind !== 'units' && <label>Mã {editor.kind === 'topics' ? 'chủ đề' : 'mục tiêu'}<input value={editor.values.code}
            aria-label={editor.kind === 'topics' ? 'Mã chủ đề' : 'Mã mục tiêu'} aria-describedby="catalog-code-help"
            disabled={!!editor.item} required minLength={2} maxLength={editor.kind === 'topics' ? 64 : 96} pattern="[a-z0-9]+(-[a-z0-9]+)*"
            placeholder={editor.kind === 'topics' ? 'vi-du: daily-routines' : 'vi-du: present-simple'} onChange={event => change('code', event.target.value)} />
            <small id="catalog-code-help" className="muted">Chữ thường, số và dấu gạch ngang. Mã được giữ nguyên sau khi tạo.</small></label>}
          <label className="catalog-wide">{editor.kind === 'units' ? 'Tên vòng đấu' : editor.kind === 'topics' ? 'Tên chủ đề' : 'Tên mục tiêu'}<input autoComplete="off" required
            maxLength={editor.kind === 'topics' ? 120 : 160} value={editor.values.name} onChange={event => change('name', event.target.value)} /></label>
          <label className="catalog-wide">Mô tả<textarea rows={3} maxLength={editor.kind === 'topics' ? 1000 : 2000}
            value={editor.values.description} onChange={event => change('description', event.target.value)} /></label>
          </>}
          {editor.kind === 'units' && <label className="catalog-wide">{assignmentMode ? 'Giảng viên phụ trách' : 'Giáo viên phụ trách'}<select value={editor.values.assignedTeacherId} onChange={event => change('assignedTeacherId', event.target.value)}>
            <option value="">Chưa phân công</option>{editor.values.assignedTeacherId && !teachers.some(t => String(t.id) === editor.values.assignedTeacherId) && <option value={editor.values.assignedTeacherId}>Tài khoản #{editor.values.assignedTeacherId} không còn hoạt động với vai trò giáo viên</option>}
            {teachers.map(t => <option key={t.id} value={t.id}>{t.fullName}</option>)}</select></label>}
          {editor.kind === 'units' && (!assignmentMode || !editor.item) && <label className="catalog-wide">Số thứ tự vòng<input type="number" min={1} step={1} max={2147483647}
            aria-label="Số thứ tự vòng" aria-describedby="round-number-help"
            required={!!editor.item} value={editor.values.roundNumber} placeholder="Tự động thêm ở cuối"
            onChange={event => change('roundNumber', event.target.value)} />
            <small id="round-number-help" className="muted">Số nhỏ được học trước. Mỗi số chỉ dùng cho một vòng, kể cả vòng chưa công bố. Để trống khi tạo để tự chọn số tiếp theo.</small></label>}
        </fieldset>
        {saveError && <div className="message error" role="alert">{saveError}</div>}
        {retryPending && <p className="muted">Chưa xác nhận được kết quả lưu. Hãy thử lại cùng thao tác để tránh tạo nội dung trùng.</p>}
        <div className="catalog-form-actions"><button type="button" className="button secondary" disabled={saving || retryPending} onClick={() => returnTo({ catalogEditor: null })}>Hủy</button>
          <button className="button primary" disabled={saving}>{saving ? 'Đang lưu…' : retryPending ? 'Thử lưu lại' : assignmentMode ? 'Lưu phân công' : 'Lưu thay đổi'}</button></div>
      </form>
    </div>}
    <div className="panel catalog-browser">
      <form className="filters" onSubmit={event => { event.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(value => value + 1); }}>
        <label className="search-label">Tìm học liệu<input value={query} maxLength={100} placeholder="Tìm theo tên hoặc mã…" onChange={event => setQuery(event.target.value)} /></label>
        {kind !== 'topics' && <label>Lọc chủ đề<select value={topicFilter} onChange={event => { setTopicFilter(event.target.value); setPage(0); }}><option value="">Tất cả chủ đề</option>
          {topics.map(topic => <option key={topic.id} value={topic.id}>{topic.name}</option>)}</select></label>}
        {allowAll && !(kind === 'units' && user.role === 'ADMIN') && <label>Phạm vi<select value={scope} onChange={event => { setScope(event.target.value); setPage(0); }}>
          <option value="OWN">Của tôi</option><option value="ALL">{kind === 'units' ? 'Tất cả tác giả' : 'Danh mục dùng chung'}</option></select></label>}
        <button className="button secondary" disabled={loading}>Tìm kiếm</button>
      </form>
      {error && <div className="catalog-feedback"><div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(value => value + 1)}>Tải lại</button></div></div>}
      {loading ? <p className="table-message" role="status">Đang tải học liệu…</p> : !error && !data?.items.length ? <div className="catalog-empty"><strong>Chưa có {catalogNames[kind].toLowerCase()} phù hợp</strong>
        <p className="muted">{search || topicFilter ? 'Thử đổi từ khóa hoặc bộ lọc.' : kind === 'topics' ? 'Tạo chủ đề đầu tiên để bắt đầu sắp xếp học liệu.' : kind === 'units' && user.role === 'TEACHER' ? 'Bạn chưa có vòng được phân công hoặc bộ lọc chưa phù hợp. Quản trị viên phân công vòng để bạn biên soạn.' : 'Chọn một chủ đề và thêm nội dung của bạn.'}</p></div> : null}
      {!loading && !error && <div className="catalog-grid">{data?.items.map(item => <article className="catalog-card" key={item.id}>
        <span className="eyebrow">{kind === 'units' && 'stage' in item ? roundHeading('roundNumber' in item ? item.roundNumber : undefined).toUpperCase() : catalogNames[kind].toUpperCase()}</span>
        <h2>{itemName(item)}</h2>{'topicId' in item && <span className="catalog-topic">{topicName(item.topicId)}</span>}
        <p className="muted">{item.description || 'Chưa có mô tả.'}</p>
        {'code' in item && <small className="catalog-code">{item.code}</small>}
        <div className="catalog-card-footer"><span>{'assignedTeacherId' in item ? item.assignedTeacherId === user.id ? 'Bạn phụ trách vòng này' : item.assignedTeacherId ? 'Giáo viên: ' + (teachers.find(t => t.id === item.assignedTeacherId)?.fullName ?? '#' + item.assignedTeacherId) : 'Chưa phân công' : item.ownerId === user.id ? 'Nội dung của bạn' : 'Tác giả khác'}</span>
          {canManage && (user.role === 'ADMIN' || item.ownerId === user.id) && <button className="link-button" disabled={!!editor} aria-label={(assignmentMode ? 'Phân công ' : 'Sửa ') + itemName(item)} onClick={() => open(item)}>{assignmentMode ? 'Phân công →' : 'Chỉnh sửa →'}</button>}
          {user.role === 'TEACHER' && 'assignedTeacherId' in item && item.assignedTeacherId === user.id && <button className="link-button" onClick={() => navigate({ view: 'content', area: 'lessons', unit: String(item.id) }, { reset: true })}>Soạn bài →</button>}</div>
      </article>)}</div>}
      <div className="pagination"><span>{data?.totalElements ?? 0} {catalogNames[kind].toLowerCase()} · Trang {page + 1}/{Math.max(data?.totalPages ?? 0, 1)}</span><div>
        <button className="button secondary compact" disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Trước</button>
        <button className="button secondary compact" disabled={loading || page + 1 >= (data?.totalPages ?? 0)} onClick={() => setPage(value => value + 1)}>Sau</button>
      </div></div>
    </div>
  </section>;
}
