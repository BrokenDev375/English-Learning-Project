import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import type { User } from '../../types';
import type { Asset, MaterialContent, MaterialKind, MaterialPreview, MaterialRoot, MaterialVersion } from './materials';
import { materialKinds, materialStatuses } from './materials';
import MaterialPresentation from './MaterialPresentation';
import AudioUpdatePanel from './AudioUpdatePanel';

interface Fields { kind: MaterialKind; title: string; readingText: string; transcript: string; altText: string; caption: string; start: string; end: string; assetId: number | null; source: string; url: string; license: string }
interface Pending { path: string; method: string; body?: unknown; complete: (value: unknown) => void; label: string }
function fields(version: MaterialVersion | null): Fields {
  return { kind: version?.kind ?? 'READING', title: version?.title ?? '', readingText: version?.kind === 'READING' ? version.readingText : '',
    transcript: version?.kind === 'AUDIO' ? version.transcript : '', altText: version && 'altText' in version ? version.altText ?? '' : '',
    caption: version?.kind === 'IMAGE' ? version.caption : '', start: version?.kind === 'AUDIO' ? String(version.startSeconds) : '0',
    end: version?.kind === 'AUDIO' && version.endSeconds !== null ? String(version.endSeconds) : '', assetId: version?.assetId ?? null,
    source: version?.source.label ?? '', url: version?.source.url ?? '', license: version?.source.license ?? '' };
}
function content(form: Fields): MaterialContent {
  const common = { title: form.title, source: { label: form.source.trim(), url: form.url.trim() || null, license: form.license.trim() || null } };
  if (form.kind === 'READING') return { ...common, kind: 'READING', readingText: form.readingText, assetId: form.assetId, altText: form.assetId ? form.altText : null };
  if (form.kind === 'AUDIO') return { ...common, kind: 'AUDIO', assetId: form.assetId!, transcript: form.transcript, startSeconds: Number(form.start), endSeconds: form.end === '' ? null : Number(form.end) };
  return { ...common, kind: 'IMAGE', assetId: form.assetId!, altText: form.altText, caption: form.caption };
}

export default function MaterialEditor({ initial, user, onClose, onChanged, onRoot }: { initial: MaterialRoot | null; user: User; onClose: (message?: string) => void; onChanged: () => void; onRoot: (root: MaterialRoot) => void }) {
  const [root, setRoot] = useState(initial);
  const [form, setForm] = useState<Fields>(() => fields(initial?.draft ?? initial?.published ?? null));
  const [mode, setMode] = useState<'draft' | 'published'>(initial?.draft || !initial ? 'draft' : 'published');
  const [dirty, setDirty] = useState(!initial), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState(''), [details, setDetails] = useState<string[]>([]), [notice, setNotice] = useState('');
  const [preview, setPreview] = useState<MaterialPreview | null>(null), [discarding, setDiscarding] = useState(false);
  const [file, setFile] = useState<File | null>(null), [attachmentName, setAttachmentName] = useState('File đã lưu');
  const pending = useRef<Pending | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const own = !root || root.ownerId === user.id || user.role === 'ADMIN';
  const [audioUpdating, setAudioUpdating] = useState(false);
  const locked = busy || uncertain || audioUpdating;
  const editing = own && mode === 'draft' && (!root || !!root.draft);
  function change<K extends keyof Fields>(key: K, value: Fields[K]) { setForm(current => ({ ...current, [key]: value })); setDirty(true); setPreview(null); }
  function received(value: unknown, message: string) {
    const next = value as MaterialRoot; setRoot(next); setMode(next.draft ? 'draft' : 'published');
    setForm(fields(next.draft ?? next.published)); setDirty(false); setPreview(null); setNotice(message); onChanged(); onRoot(next);
  }
  async function run(command: Pending) {
    if (busy) return; pending.current = command; setBusy(true); setError(''); setDetails([]); setNotice('');
    try {
      const result = await api<unknown>(command.path, command.method, command.body);
      pending.current = null; setUncertain(false); command.complete(result);
    } catch (failure) {
      const ambiguous = failure instanceof ApiError && (failure.status === 0 || failure.status >= 500);
      setUncertain(ambiguous); if (!ambiguous) pending.current = null;
      setError(errorMessage(failure)); if (failure instanceof ApiError) setDetails(Object.values(failure.fields));
    } finally { setBusy(false); }
  }
  async function save(event: FormEvent) {
    event.preventDefault(); if (locked) return;
    if (form.kind !== 'READING' && !form.assetId) { setError('Hãy tải file lên trước khi lưu tài liệu.'); return; }
    const body: Record<string, unknown> = { requestId: requestId(), ...content(form) };
    if (root?.draft) Object.assign(body, { draftVersionId: root.draft.id, baseRevision: root.draft.revision });
    await run({ path: root ? '/api/author/materials/' + root.id + '/draft' : '/api/author/materials', method: root ? 'PUT' : 'POST', body,
      label: 'lưu bản nháp', complete: value => received(value, 'Đã lưu bản nháp trên máy chủ.') });
  }
  async function upload() {
    if (!file || locked) return;
    if (file.size > 5242880) { setError('File tối đa 5 MiB.'); return; }
    const body = new FormData(); body.append('requestId', requestId()); body.append('file', file);
    await run({ path: '/api/assets', method: 'POST', body, label: 'tải file', complete: value => {
      const asset = value as Asset;
      const image = asset.mimeType.startsWith('image/');
      if ((form.kind === 'AUDIO' && image) || (form.kind !== 'AUDIO' && !image)) { setError('File không phù hợp với loại tài liệu đã chọn.'); return; }
      change('assetId', asset.id); setAttachmentName(asset.name); setFile(null); if (fileInput.current) fileInput.current.value = '';
      setNotice('Đã tải file. Hãy lưu bản nháp để gắn file vào tài liệu.');
    } });
  }
  async function showPreview() {
    if (!root || locked || dirty) return; setBusy(true); setError(''); setDetails([]);
    try { setPreview(await api<MaterialPreview>('/api/author/materials/' + root.id + '/preview')); }
    catch (failure) { setError(errorMessage(failure)); } finally { setBusy(false); }
  }
  function startDraft() {
    if (!root) return;
    if (root.draft) { setMode('draft'); return; }
    void run({ path: '/api/author/materials/' + root.id + '/draft', method: 'POST', body: { requestId: requestId(), baseRevision: root.rootRevision },
      label: 'tạo bản chỉnh sửa', complete: value => received(value, 'Đã mở bản nháp mới. Bản đã xuất bản được giữ nguyên.') });
  }
  function publish() {
    if (!root || !preview || dirty) return;
    void run({ path: '/api/author/materials/' + root.id + '/publish', method: 'POST', body: { requestId: requestId(), baseRevision: preview.baseRevision,
      draftVersionId: preview.draftVersionId, previewToken: preview.previewToken }, label: 'xuất bản', complete: value => received(value, 'Đã xuất bản tài liệu.') });
  }
  function discard() {
    if (!root?.draft) return;
    const params = new URLSearchParams({ requestId: requestId(), draftVersionId: String(root.draft.id), baseRevision: String(root.draft.revision) });
    void run({ path: '/api/author/materials/' + root.id + '/draft?' + params, method: 'DELETE', label: 'bỏ bản nháp', complete: () => { onChanged(); onClose('Đã bỏ bản nháp.'); } });
  }
  async function reload() {
    if (!root || locked) return; setBusy(true); setError(''); setDetails([]);
    try { received(await api<MaterialRoot>('/api/author/materials/' + root.id), 'Đã tải lại tài liệu từ máy chủ.'); }
    catch (failure) { setError(errorMessage(failure)); } finally { setBusy(false); }
  }
  return <section className="material-editor panel">
    <div className="material-editor-heading"><div><span className="eyebrow">BIÊN SOẠN TÀI LIỆU</span><h2>{root ? (root.draft ?? root.published)?.title || 'Tài liệu chưa đặt tên' : 'Thêm tài liệu'}</h2>
      {root && <span className={'material-status ' + root.status.toLowerCase()}>{materialStatuses[root.status]}</span>}</div>
      <button className="button secondary compact" disabled={locked} onClick={() => onClose()}>Về danh sách</button></div>
    {notice && <div className="message success" role="status">{notice}</div>}
    {error && <div className="message error" role="alert">{error}{details.length > 0 && <ul>{details.map((detail, index) => <li key={index}>{detail}</li>)}</ul>}</div>}
    {uncertain && <div className="message warning"><p>Chưa xác nhận được kết quả. Thử lại cùng thao tác để tránh tạo trùng.</p>
      <button className="button secondary" disabled={busy} onClick={() => { if (pending.current) void run(pending.current); }}>{busy ? 'Đang xử lý…' : 'Thử lại thao tác'}</button></div>}
    {root?.draft && root.published && <div className="catalog-tabs" role="tablist" aria-label="Phiên bản tài liệu">
      <button role="tab" aria-selected={mode === 'draft'} disabled={locked} onClick={() => setMode('draft')}>Bản nháp</button>
      <button role="tab" aria-selected={mode === 'published'} disabled={locked} onClick={() => setMode('published')}>Bản đã xuất bản</button></div>}
    {editing ? <form onSubmit={save}>
      <fieldset className="catalog-fields" disabled={locked}>
        <label>Loại tài liệu<select value={form.kind} onChange={event => { change('kind', event.target.value as MaterialKind); change('assetId', null); setFile(null); }}>
          {Object.entries(materialKinds).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Tên tài liệu<input value={form.title} maxLength={160} onChange={event => change('title', event.target.value)} placeholder="Ví dụ: A day with Linh" /></label>
        {form.kind === 'READING' && <label className="catalog-wide">Nội dung đoạn đọc<textarea rows={7} maxLength={12000} value={form.readingText} onChange={event => change('readingText', event.target.value)} /></label>}
        <div className="catalog-wide material-upload"><label>{form.kind === 'AUDIO' ? 'File audio' : form.kind === 'IMAGE' ? 'File ảnh' : 'Ảnh minh họa (tùy chọn)'}<input ref={fileInput} type="file"
          accept={form.kind === 'AUDIO' ? '.mp3,.wav,.ogg,audio/mpeg,audio/wav,audio/ogg' : '.png,.jpg,.jpeg,image/png,image/jpeg'}
          onChange={event => { setFile(event.target.files?.[0] ?? null); setError(''); }} /></label>
          <p className="muted">PNG/JPEG hoặc MP3/WAV/OGG, tối đa 5 MiB mỗi file.</p>
          <button type="button" className="button secondary" disabled={!file} onClick={() => void upload()}>Tải file lên</button>
          {form.assetId && <div className="material-attachment"><strong>{attachmentName}</strong><a href={'/api/assets/' + form.assetId} target="_blank" rel="noreferrer">Mở file</a>
            {form.kind === 'READING' && <button type="button" className="link-button" onClick={() => change('assetId', null)}>Gỡ ảnh khỏi bản nháp</button>}</div>}
        </div>
        {form.kind !== 'AUDIO' && form.assetId && <label className="catalog-wide">Mô tả ảnh<textarea rows={2} maxLength={500} value={form.altText} onChange={event => change('altText', event.target.value)} /></label>}
        {form.kind === 'IMAGE' && <label className="catalog-wide">Chú thích ảnh<input maxLength={1000} value={form.caption} onChange={event => change('caption', event.target.value)} /></label>}
        {form.kind === 'AUDIO' && <><label className="catalog-wide">Bản chép lời<textarea rows={5} maxLength={12000} value={form.transcript} onChange={event => change('transcript', event.target.value)} /></label>
          <label>Bắt đầu từ (giây)<input type="number" min={0} max={86400} step="0.001" required value={form.start} onChange={event => change('start', event.target.value)} /></label>
          <label>Kết thúc tại (giây, để trống để nghe hết)<input type="number" min={0} max={86400} step="0.001" value={form.end} onChange={event => change('end', event.target.value)} /></label></>}
        <label>Tác giả hoặc nguồn<input required maxLength={200} value={form.source} onChange={event => change('source', event.target.value)} placeholder="Ghi rõ tác giả hoặc nơi cung cấp tài liệu" /></label>
        <label>Liên kết nguồn (tùy chọn)<input type="url" maxLength={2000} value={form.url} onChange={event => change('url', event.target.value)} placeholder="https://…" /></label>
        <label className="catalog-wide">Giấy phép / điều kiện sử dụng<input maxLength={200} value={form.license} onChange={event => change('license', event.target.value)} /></label>
      </fieldset>
      <div className="material-actions"><button className="button primary" disabled={locked}>{busy ? 'Đang xử lý…' : 'Lưu bản nháp'}</button>
        <button type="button" className="button secondary" disabled={locked || dirty || !root?.draft} onClick={() => void showPreview()}>Xem trước</button>
        {root?.draft && <button type="button" className="link-button danger-text" disabled={locked} onClick={() => setDiscarding(true)}>Bỏ bản nháp</button>}</div>
      {dirty && <p className="muted material-save-note">Có thay đổi chưa lưu. Lưu bản nháp để xem trước và xuất bản.</p>}
    </form> : root?.published ? <>
      <p className="muted">Phiên bản {root.published.versionNo}{root.status === 'RETIRED' ? ' · Tài liệu đã ngừng cấp mới.' : ''}</p>
      <MaterialPresentation material={root.published} />
      {own && <div className="material-actions"><button className="button primary" disabled={locked} onClick={startDraft}>{root.draft ? 'Mở bản nháp' : 'Tạo bản chỉnh sửa'}</button>
        {root.status !== 'RETIRED' && <button className="button secondary" disabled={locked} onClick={() => { void run({ path: '/api/author/materials/' + root.id + '/retire', method: 'POST',
          body: { requestId: requestId(), baseRevision: root.rootRevision }, label: 'ngừng cấp mới', complete: value => received(value, 'Đã ngừng cấp tài liệu cho nội dung mới.') }); }}>Ngừng cấp mới</button>}</div>}
      {user.role === 'ADMIN' && root.status === 'PUBLISHED' && root.published.kind === 'AUDIO' &&
        <AudioUpdatePanel key={root.published.id} materialId={root.id} disabled={busy || uncertain} onBlockingChange={setAudioUpdating} />}
    </> : <p className="muted">Tài liệu chưa có phiên bản để hiển thị.</p>}
    {preview && editing && <div className="material-preview"><span className="eyebrow">XEM TRƯỚC CHO NGƯỜI HỌC</span>
      <MaterialPresentation material={preview.data} />
      {preview.errors.length > 0 && <div className="message error" role="alert"><strong>Cần bổ sung trước khi xuất bản:</strong><ul>{preview.errors.map(issue => <li key={issue.path}>{issue.message}</li>)}</ul></div>}
      {preview.warnings.length > 0 && <div className="message warning"><ul>{preview.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul></div>}
      <p className="muted">Bản chép lời được giữ riêng, không hiển thị trong phần xem trước cho người học.</p>
      <button className="button primary" disabled={locked || dirty || preview.errors.length > 0} onClick={publish}>Xuất bản tài liệu</button>
    </div>}
    {root && error && !uncertain && <div className="material-reload"><p className="muted">Tải lại sẽ thay nội dung đang nhập bằng bản trên máy chủ.</p><button className="button secondary" disabled={locked} onClick={() => void reload()}>Tải lại tài liệu</button></div>}
    {discarding && <div className="message warning"><strong>Bỏ bản nháp này?</strong><p>Nội dung nháp sẽ bị bỏ. Các phiên bản đã xuất bản và file vẫn được giữ.</p>
      <div className="material-actions"><button className="button secondary" disabled={locked} onClick={() => setDiscarding(false)}>Giữ bản nháp</button>
        <button className="button danger" disabled={locked} onClick={discard}>Xác nhận bỏ bản nháp</button></div></div>}
  </section>;
}
