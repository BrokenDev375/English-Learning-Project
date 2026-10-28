import { useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import { navigate, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import type { CatalogPage } from './catalog';
import type { ContentPreview, LessonPreviewData, MaterialVersion, QuestionVersion, Review } from './authoring';
import { reviewStates } from './authoring';
import AuthorPreview from './AuthorPreview';
import './authoring.css';

interface Detail { review: Review; snapshot: ContentPreview & { materials?: MaterialVersion[] }; stale: boolean; canWithdraw: boolean }
interface Pending { id: number; action: string; body: { requestId: string; baseRevision: number; note: string } }
const date = (value: string) => new Date(value).toLocaleString('vi-VN');
export default function ContentReviews({ user }: { user: User }) {
  const [selected] = useRouteParam('review'), [state, setState] = useState('PENDING'), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [rows, setRows] = useState<CatalogPage<Review> | null>(null), [detail, setDetail] = useState<Detail | null>(null);
  const [loading, setLoading] = useState(true), [error, setError] = useState(''), [notice, setNotice] = useState(''), [note, setNote] = useState('');
  const [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false);
  const pending = useRef<Pending | null>(null), generation = useRef(0);
  const admin = user.role === 'ADMIN';
  useEffect(() => {
    let live = true; generation.current++; pending.current = null; setUncertain(false); setLoading(true); setError(''); setDetail(null); setNote('');
    const read = selected ? api<Detail>('/api/author/reviews/' + selected) : api<CatalogPage<Review>>('/api/author/reviews?state=' + state + '&size=12&page=' + page);
    void read.then(value => { if (!live) return; if (selected) setDetail(value as Detail); else setRows(value as CatalogPage<Review>); })
      .catch(e => { if (live) setError(errorMessage(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; generation.current++; };
  }, [selected, state, page, refresh]);
  async function act(action: 'approve' | 'return' | 'withdraw') {
    if (!detail || busy) return;
    if (!pending.current && action === 'return' && !note.trim()) { setError('Cần ghi lý do trả lại để giáo viên chỉnh sửa.'); return; }
    const command = pending.current ?? { id: detail.review.id, action, body: { requestId: requestId(), baseRevision: detail.review.revision, note: note.trim() } };
    pending.current = command; setBusy(true); setError(''); setNotice(''); const epoch = generation.current;
    try {
      const next = await api<Review>('/api/author/reviews/' + command.id + '/' + command.action, 'POST', command.body);
      if (epoch !== generation.current) return;
      pending.current = null; setUncertain(false); setDetail({ ...detail, review: next, stale: false, canWithdraw: false });
      setNotice(command.action === 'approve' ? 'Đã duyệt và công bố nội dung.' : command.action === 'return' ? 'Đã trả lại kèm lý do. Giáo viên có thể chỉnh sửa và gửi lại.' : 'Đã rút yêu cầu. Có thể tiếp tục chỉnh sửa bản nháp.');
    } catch (e) {
      if (epoch !== generation.current) return;
      const ambiguous = e instanceof ApiError && (e.status === 0 || e.status >= 500);
      setUncertain(ambiguous); if (!ambiguous) pending.current = null; setError(errorMessage(e));
    } finally { if (epoch === generation.current) setBusy(false); }
  }
  const review = detail?.review, locked = busy || uncertain;
  function openContent() { if (review) navigate({ view: 'content', area: review.kind === 'LESSON' ? 'lessons' : 'questions', item: String(review.resourceId) }, { reset: true }); }
  return <section>
    <span className="eyebrow">{admin ? 'KIỂM DUYỆT HỌC LIỆU' : 'THEO DÕI BIÊN SOẠN'}</span>
    <h1>{admin ? 'Duyệt học liệu' : 'Bài gửi duyệt'}<span>.</span></h1>
    <p className="muted">{admin ? 'Xem bản nội dung đã gửi, duyệt để công bố hoặc ghi lý do trả lại.' : 'Theo dõi bài học trong vòng được giao và câu hỏi của bạn. Bản sửa chỉ thay thế bản đang học sau khi được duyệt.'}</p>
    {error && <div className="message error" role="alert">{error}</div>}{notice && <div className="message success" role="status">{notice}</div>}
    {uncertain && <div className="message warning"><p>Chưa nhận được xác nhận. Thử lại cùng thao tác để đối chiếu kết quả.</p><button className="button secondary" disabled={busy} onClick={() => pending.current && void act(pending.current.action as 'approve' | 'return' | 'withdraw')}>Thử lại thao tác duyệt</button></div>}
    {loading ? <p role="status">Đang tải yêu cầu duyệt…</p> : selected && detail && review ? <article className="panel material-editor review-detail">
      <span className="material-status">{reviewStates[review.state]}</span><h2>{review.title}</h2>
      <p className="muted">{review.kind === 'LESSON' ? 'Bài học' : 'Câu hỏi'} · {review.submittedByName} gửi lúc {date(review.submittedAt)}</p>
      {review.reviewedAt && <p className="muted">{review.reviewedByName} xử lý lúc {date(review.reviewedAt)}</p>}
      {review.note && <div className="message"><strong>Nhận xét</strong><p className="review-note">{review.note}</p></div>}
      {detail.stale && review.state === 'PENDING' && <p className="message warning">Nội dung hoặc tài liệu liên quan đã thay đổi sau khi gửi. Cần rút yêu cầu hoặc trả lại để xem trước và gửi lại bản mới.</p>}
      <div className="material-preview"><span className="eyebrow">BẢN NỘI DUNG ĐÃ GỬI</span>{review.kind === 'QUESTION' ? <AuthorPreview questions={[detail.snapshot.data as QuestionVersion]} materials={detail.snapshot.materials ?? []} /> : <AuthorPreview lesson={(detail.snapshot.data as LessonPreviewData).lesson} questions={(detail.snapshot.data as LessonPreviewData).questions} materials={(detail.snapshot.data as LessonPreviewData).materials} />}</div>
      {review.state === 'PENDING' && admin && <label className="review-comment">Nhận xét / lý do trả lại<textarea rows={3} maxLength={2000} value={note} disabled={locked} onChange={e => setNote(e.target.value)} /></label>}
      <div className="material-actions">
        {review.state === 'PENDING' && admin && <><button className="button primary" disabled={locked || detail.stale} onClick={() => void act('approve')}>Duyệt và công bố</button><button className="button secondary" disabled={locked} onClick={() => void act('return')}>Trả lại để chỉnh sửa</button></>}
        {review.state === 'PENDING' && detail.canWithdraw && <button className="button secondary" disabled={locked} onClick={() => void act('withdraw')}>Rút yêu cầu</button>}
        <button className="link-button" disabled={locked} onClick={openContent}>{review.state === 'PENDING' ? 'Mở bài đang biên soạn' : 'Mở nội dung hiện tại'} →</button>
        <button className="link-button" disabled={locked} onClick={() => navigate({ review: null })}>Về danh sách yêu cầu</button>
      </div>
    </article> : !selected ? <div className="panel catalog-browser">
      <div className="filters"><label>Trạng thái duyệt<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="ALL">Tất cả trạng thái</option>{Object.entries(reviewStates).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><button className="button secondary" onClick={() => setRefresh(v => v + 1)}>Cập nhật danh sách</button></div>
      {!rows?.items.length && !error && <p className="table-message">Chưa có yêu cầu phù hợp.</p>}
      <div className="catalog-grid">{rows?.items.map(item => <article key={item.id} className="catalog-card"><span className="eyebrow">{item.kind === 'LESSON' ? 'BÀI HỌC' : 'CÂU HỎI'} · {reviewStates[item.state]}</span><h2>{item.title}</h2><p className="muted">{item.submittedByName} · {date(item.submittedAt)}</p>{item.note && <p className="review-note">{item.note}</p>}<button className="link-button" onClick={() => navigate({ review: String(item.id) })}>Xem yêu cầu #{item.id} →</button></article>)}</div>
      <div className="pagination"><span>{rows?.totalElements ?? 0} yêu cầu · Trang {page + 1}/{Math.max(1, rows?.totalPages ?? 0)}</span><div><button className="button secondary compact" disabled={page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={page + 1 >= (rows?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button></div></div>
    </div> : <button className="button secondary" onClick={() => setRefresh(v => v + 1)}>Thử tải lại yêu cầu</button>}
  </section>;
}
