import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import { navigate, returnTo, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import type { CatalogPage, Topic, Unit, Objective } from './catalog';
import type { BankKind, ContentRoot, Summary } from './authoring';
import { allPages, difficulties, questionTypes } from './authoring';
import { materialStatuses } from './materials';
import AuthorEditor from './AuthorEditor';
import UnitLessonOrder from './UnitLessonOrder';

export default function AuthorLibrary({ kind, user }: { kind: BankKind; user: User }) {
  const [scope, setScope] = useState(user.role === 'ADMIN' ? 'ALL' : 'OWN'), [status, setStatus] = useState(''), [type, setType] = useState(''), [difficulty, setDifficulty] = useState('');
  const [topic, setTopic] = useState(''), [unit, setUnit] = useState(''), [objective, setObjective] = useState('');
  const [topics, setTopics] = useState<Topic[]>([]), [units, setUnits] = useState<Unit[]>([]), [objectives, setObjectives] = useState<Objective[]>([]);
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<CatalogPage<Summary> | null>(null), [loading, setLoading] = useState(true), [opening, setOpening] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [item] = useRouteParam('item'), [ordering] = useRouteParam('order');
  const [editor, setEditor] = useState<{ route: string; root: ContentRoot | null; session: number } | null>(null);
  useEffect(() => {
    if (!item || editor?.route === item) { setOpening(false); return; }
    let active = true;
    if (item === 'new') { setOpening(false); setEditor({ route: item, root: null, session: Date.now() }); return; }
    setOpening(true); setError('');
    void api<ContentRoot>('/api/author/' + kind + '/' + encodeURIComponent(item))
      .then(root => { if (active) setEditor({ route: item, root, session: Date.now() }); })
      .catch(failure => { if (active) setError(errorMessage(failure)); })
      .finally(() => { if (active) setOpening(false); });
    return () => { active = false; };
  }, [item, kind]);
  const question = kind === 'questions', label = question ? 'câu hỏi' : 'bài học';
  useEffect(() => { let active = true; void Promise.all([allPages<Topic>('/api/author/topics?scope=ALL'), allPages<Unit>('/api/author/units?scope=' + (user.role === 'ADMIN' ? 'ALL' : 'OWN')), allPages<Objective>('/api/author/objectives?scope=ALL')]).then(([a, b, c]) => { if (active) { setTopics(a); setUnits(b); setObjectives(c); } }).catch(f => { if (active) setError(errorMessage(f)); }); return () => { active = false; }; }, []);
  useEffect(() => { let active = true; setLoading(true); setError(''); const params = new URLSearchParams({ scope, q: search, page: String(page), size: '9' }); if (status) params.set('status', status); if (topic) params.set('topicId', topic); if (question) { if (type) params.set('type', type); if (difficulty) params.set('difficulty', difficulty); if (objective) params.set('objectiveId', objective); } else if (unit) params.set('unitId', unit);
    api<CatalogPage<Summary>>('/api/author/' + kind + '?' + params).then(value => { if (active) setData(value); }).catch(f => { if (active) setError(errorMessage(f)); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; };
  }, [scope, status, topic, unit, objective, type, difficulty, search, page, refresh]);
  function open(id: number) { navigate({ item: String(id) }); }
  if (item && editor?.route === item) return <AuthorEditor key={editor.session} kind={kind} initial={editor.root} user={user} onRoot={root => { setEditor(current => current ? { ...current, route: String(root.id), root } : current); navigate({ item: String(root.id) }, { replace: true }); }} onClose={message => { setEditor(null); setNotice(message ?? ''); setRefresh(v => v + 1); returnTo({ item: null }); }} />;
  if (item) return <section className="panel material-editor">{error ? <div className="message error" role="alert">{error}</div> : <p role="status">Đang mở nội dung…</p>}<button className="button secondary" onClick={() => returnTo({ item: null })}>Về danh sách</button></section>;
  if (ordering) return <UnitLessonOrder id={Number(ordering)} onClose={() => { returnTo({ order: null }); setRefresh(v => v + 1); }} />;
  return <section><div className="page-heading"><div><span className="eyebrow">{question ? 'NGÂN HÀNG CÂU HỎI' : 'BIÊN SOẠN BÀI HỌC'}</span><h1>{question ? 'Câu hỏi học tập' : 'Bài học của bạn'}<span>.</span></h1><p className="muted">{question ? 'Soạn ba dạng câu hỏi, dùng chung tài liệu và giữ nguyên nguồn gốc khi sao chép.' : 'Chọn Unit, thêm hướng dẫn và sắp xếp câu hỏi vào từng nhóm sử dụng.'}</p></div><button className="button primary" onClick={() => navigate({ item: 'new' })}>+ Thêm {label}</button></div>
    {notice && <div className="message success" role="status">{notice}</div>}{error && <div className="message error" role="alert">{error}<button className="link-button" onClick={() => setRefresh(v => v + 1)}>Tải lại</button></div>}
    <div className="panel catalog-browser"><form className="filters material-filters author-filters" onSubmit={e => { e.preventDefault(); setSearch(query.trim()); setPage(0); setRefresh(v => v + 1); }}>
      <label className="search-label">Tìm {label}<input maxLength={100} value={query} onChange={e => setQuery(e.target.value)} placeholder={'Tìm theo ' + (question ? 'nội dung câu hỏi…' : 'tên bài học…')} /></label>
      <label>Chủ đề<select value={topic} onChange={e => { setTopic(e.target.value); setObjective(''); setUnit(''); setPage(0); }}><option value="">Tất cả chủ đề</option>{topics.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
      {question ? <><label>Dạng câu<select value={type} onChange={e => { setType(e.target.value); setPage(0); }}><option value="">Tất cả dạng</option>{Object.entries(questionTypes).map(([v, name]) => <option key={v} value={v}>{name}</option>)}</select></label><label>Độ khó<select value={difficulty} onChange={e => { setDifficulty(e.target.value); setPage(0); }}><option value="">Tất cả độ khó</option>{Object.entries(difficulties).map(([v, name]) => <option key={v} value={v}>{name}</option>)}</select></label><label>Mục tiêu<select value={objective} onChange={e => { setObjective(e.target.value); setPage(0); }}><option value="">Tất cả mục tiêu</option>{objectives.filter(o => !topic || o.topicId === Number(topic)).map(o => <option key={o.id} value={o.id}>{o.name}</option>)}</select></label></> : <label>Unit<select value={unit} onChange={e => { setUnit(e.target.value); setPage(0); }}><option value="">Tất cả Unit</option>{units.filter(u => !topic || u.topicId === Number(topic)).map(u => <option key={u.id} value={u.id}>{u.title}</option>)}</select></label>}
      <label>Trạng thái<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(materialStatuses).map(([v, name]) => <option key={v} value={v}>{name}</option>)}</select></label><label>Phạm vi<select value={scope} onChange={e => { setScope(e.target.value); setPage(0); }}><option value="OWN">Của tôi</option><option value="PUBLISHED">Kho đã xuất bản</option>{user.role === 'ADMIN' && <option value="ALL">Tất cả tác giả</option>}</select></label><button className="button secondary" disabled={loading}>Tìm kiếm</button>
    </form>{!question && <div className="unit-order-toolbar"><button className="link-button" disabled={!unit} onClick={() => navigate({ order: unit })}>Sắp xếp bài trong Unit đã chọn →</button></div>}
      {loading ? <p className="table-message" role="status">Đang tải {label}…</p> : !error && !data?.items.length ? <div className="catalog-empty"><strong>Chưa có {label} phù hợp</strong><p className="muted">Thêm nội dung đầu tiên hoặc thay đổi bộ lọc.</p></div> : null}
      {!loading && !error && <div className="catalog-grid">{data?.items.map(item => { const name = item.promptExcerpt || item.title || (question ? 'Câu chưa nhập nội dung' : 'Bài chưa đặt tên'); return <article className="catalog-card" key={item.id}><div className="material-card-top"><span className="material-kind-icon" aria-hidden="true">{question ? '?' : '▤'}</span><span className={'material-status ' + item.status.toLowerCase()}>{materialStatuses[item.status]}</span></div><span className="eyebrow">{item.type ? questionTypes[item.type] : units.find(u => u.id === item.unitId)?.title || 'Bài học'}</span><h2>{name}</h2><p className="muted">{item.difficulty ? difficulties[item.difficulty] + ' · ' : ''}{item.draftVersionId && item.publishedVersionId ? 'Có bản nháp chỉnh sửa.' : item.status === 'DRAFT' ? 'Đang biên soạn.' : item.status === 'RETIRED' ? 'Giữ lịch sử, không cấp mới.' : 'Đã công bố.'}</p><div className="catalog-card-footer"><span>{item.ownerId === user.id ? 'Nội dung của bạn' : 'Tác giả khác'}</span><button className="link-button" disabled={opening} aria-label={'Mở ' + name} onClick={() => void open(item.id)}>Mở {label} →</button></div></article>; })}</div>}
      <div className="pagination"><span>{data?.totalElements ?? 0} {label} · Trang {page + 1}/{Math.max(data?.totalPages ?? 0, 1)}</span><div><button className="button secondary compact" disabled={loading || page === 0} onClick={() => setPage(v => v - 1)}>Trước</button><button className="button secondary compact" disabled={loading || page + 1 >= (data?.totalPages ?? 0)} onClick={() => setPage(v => v + 1)}>Sau</button></div></div>
    </div></section>;
}
