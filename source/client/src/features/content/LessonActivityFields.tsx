import type { Activity, ActivityKind, Choices, LessonData, PassagePart, Slot } from './authoring';
import { activityKinds } from './authoring';
import { useState } from 'react';

const kinds = Object.keys(activityKinds) as ActivityKind[];
const newCounts = [3, 2, 2, 3, 3, 3, 2];
const types = ['SINGLE_CHOICE', 'MATCHING', 'ORDERING', 'FILL_BLANK', 'FILL_BLANK', 'SINGLE_CHOICE', 'SINGLE_CHOICE'];
export default function LessonActivityFields({ lesson, choices, topicId, onChange }: {
  lesson: LessonData; choices: Choices; topicId?: number; onChange: (value: LessonData) => void;
}) {
  const activities = lesson.activities ?? [];
  const [sizes, setSizes] = useState<Record<string, number>>({});
  const [toAdd, setToAdd] = useState<ActivityKind>('CHOICE');
  const sizeOf = (a: Activity) => sizes[a.id] ?? (a.questionVersionIds.length ? Math.max(2, a.questionVersionIds.length) : newCounts[kinds.indexOf(a.kind)]);
  const total = activities.reduce((sum, a) => sum + sizeOf(a), 0);
  function readingText(id: number) { const m = choices.materials.find(m => m.id === id); return m?.kind === 'READING' ? m.readingText : ''; }
  function update(rows: Activity[]) {
    const positions = { GUIDED: 0, INDEPENDENT: 0, REVIEW_VARIANT: 0 };
    const learn: Slot[] = rows.flatMap(a => a.questionVersionIds.map((id, i) => ({ questionVersionId: id, practiceRole: i === 0 ? 'GUIDED' as const : 'INDEPENDENT' as const, position: 0 }))).map(s => ({ ...s, position: positions[s.practiceRole]++ }));
    const review = lesson.questions.filter(s => s.practiceRole === 'REVIEW_VARIANT').map((s, i) => ({ ...s, position: i }));
    onChange({ ...lesson, activities: rows, questions: [...learn, ...review] });
  }
  function replace(index: number, activity: Activity) { update(activities.map((a, i) => i === index ? activity : a)); }
  function choose(index: number, at: number, id: number) {
    const current = activities[index]; const ids = [...current.questionVersionIds];
    setSizes(s => ({ ...s, [current.id]: sizeOf(current) }));
    if (!id) ids.splice(at, 1); else ids[at] = id;
    replace(index, { ...current, questionVersionIds: ids, passages: [] });
  }
  function passages(index: number) {
    const activity = activities[index];
    const materialIds = [...new Set(activity.questionVersionIds.map(id => choices.questions.find(q => q.id === id)?.materialVersionId).filter((id): id is number => !!id))];
    const rows = materialIds.map(materialVersionId => {
      const text = readingText(materialVersionId);
      const segments = text.split('____');
      const qs = activity.questionVersionIds.map(id => choices.questions.find(q => q.id === id)!).filter(q => q?.materialVersionId === materialVersionId);
      if (segments.length - 1 !== qs.length) return null;
      const parts: PassagePart[] = [];
      segments.forEach((text, i) => { parts.push({ kind: 'TEXT', text }); if (i < qs.length) { const blank = qs[i].content.parts?.find(p => p.kind === 'BLANK'); if (blank?.kind === 'BLANK') parts.push({ kind: 'BLANK', questionVersionId: qs[i].id, blankId: blank.blankId }); } });
      return { materialVersionId, parts };
    });
    if (rows.some(row => !row)) return;
    replace(index, { ...activity, passages: rows.filter(row => row !== null) });
  }
  function add(kind: ActivityKind) {
    const rows = [...activities, { id: kind.toLowerCase(), kind, questionVersionIds: [], passages: [] }].sort((a, b) => kinds.indexOf(a.kind) - kinds.indexOf(b.kind));
    if (activities.length) update(rows); else onChange({ ...lesson, activities: rows });
  }
  const available = kinds.filter(kind => !activities.some(a => a.kind === kind));
  return <div className="catalog-wide question-block"><h3>Cấu trúc hoạt động của bài</h3>
    <p>Chọn các dạng phù hợp với bài, không cần đủ bảy dạng. Tổng 15–20 câu, gợi ý 18 câu. Mỗi phần có một câu hướng dẫn rồi tự làm.</p>
    {!activities.length ? <><button type="button" className="button secondary" onClick={() => add('CHOICE')}>Tự chọn hoạt động</button><button type="button" className="button secondary" onClick={() => onChange({ ...lesson, activities: kinds.map(kind => ({ id: kind.toLowerCase(), kind, questionVersionIds: [], passages: [] })) })}>Thiết lập bảy hoạt động</button></> : <>
      <p role="status">Đang phân bổ {total} câu · Đã chọn {activities.reduce((sum, a) => sum + a.questionVersionIds.length, 0)} câu. {activities.length} câu hướng dẫn; mỗi phần ít nhất 2 câu. Hai dạng đọc cần ví dụ và đoạn chính riêng.</p>
      {available.length > 0 && <div className="catalog-fields"><label>Dạng bài để thêm<select value={available.includes(toAdd) ? toAdd : available[0]} onChange={e => setToAdd(e.target.value as ActivityKind)}>{available.map(kind => <option key={kind} value={kind}>{activityKinds[kind]}</option>)}</select></label><button type="button" className="button secondary" onClick={() => add(available.includes(toAdd) ? toAdd : available[0])}>Thêm hoạt động</button></div>}
      {activities.map((activity, index) => {
        const reading = activity.kind === 'READING_CLOZE' || activity.kind === 'READING_CHOICE';
        const listening = activity.kind === 'LISTENING_CHOICE';
        const count = sizeOf(activity);
        const candidates = choices.questions.filter(q => q.topicId === topicId && q.type === types[kinds.indexOf(activity.kind)] && (!(reading || listening) || choices.materials.some(m => m.id === q.materialVersionId && m.kind === (listening ? 'AUDIO' : 'READING'))));
        const used = activities.flatMap(a => a.questionVersionIds).concat(lesson.questions.filter(s => s.practiceRole === 'REVIEW_VARIANT').map(s => s.questionVersionId));
        const selected = activity.questionVersionIds.map(id => choices.questions.find(q => q.id === id));
        const canMap = selected.length === count && selected.every(q => q && q.content.parts?.filter(p => p.kind === 'BLANK').length === 1) && [...new Set(selected.map(q => q?.materialVersionId))].every(id => id && readingText(id).split('____').length - 1 === selected.filter(q => q?.materialVersionId === id).length);
        return <section className="activity-editor" key={activity.id}><h4>{index + 1}. {activityKinds[activity.kind]} · {count} câu</h4><label>Số câu phần {activityKinds[activity.kind]}<select aria-label={'Số câu phần ' + activityKinds[activity.kind]} value={count} onChange={e => { const n = Number(e.target.value); setSizes(s => ({ ...s, [activity.id]: n })); if (n < activity.questionVersionIds.length) replace(index, { ...activity, questionVersionIds: activity.questionVersionIds.slice(0, n), passages: [] }); }}>{Array.from({ length: 19 }, (_, i) => <option key={i + 2} value={i + 2}>{i + 2}</option>)}</select></label><button type="button" className="link-button" onClick={() => update(activities.filter(a => a.id !== activity.id))}>Bỏ phần {activityKinds[activity.kind]}</button><div className="catalog-fields">{Array.from({ length: count }, (_, at) => <label key={at}>{at === 0 ? 'Câu hướng dẫn' : 'Câu tự làm ' + at}<select value={activity.questionVersionIds[at] ?? ''} disabled={at > activity.questionVersionIds.length} onChange={e => choose(index, at, Number(e.target.value))}><option value="">Chọn câu</option>{candidates.filter(q => q.id === activity.questionVersionIds[at] || !used.includes(q.id)).map(q => <option key={q.id} value={q.id}>{q.prompt} · v{q.versionNo}</option>)}</select></label>)}</div>
          {reading && <p className="muted">Ví dụ: {choices.materials.find(m => m.id === selected[0]?.materialVersionId)?.title ?? 'Chưa chọn'} · Đoạn chính: {choices.materials.find(m => m.id === selected[1]?.materialVersionId)?.title ?? 'Chưa chọn'}</p>}
          {activity.kind === 'READING_CLOZE' && <><p className="muted">Học liệu dùng ____ cho mỗi ô. Chọn câu theo thứ tự ô trong đoạn, rồi tạo ánh xạ; hệ thống lưu từng vị trí bằng ID.</p><button type="button" className="button secondary" disabled={!canMap} onClick={() => passages(index)}>Tạo ánh xạ ô trống</button><p role="status">{activity.passages.length ? 'Đã ánh xạ ' + activity.passages.flatMap(p => p.parts.filter(s => s.kind === 'BLANK')).length + ' ô. Xem trước để đối chiếu.' : 'Chưa tạo ánh xạ. Số dấu ____ phải bằng số câu thuộc từng đoạn.'}</p></>}
        </section>;
      })}
      <button type="button" className="link-button" onClick={() => onChange({ ...lesson, activities: [] })}>Dùng cấu trúc bài thông thường</button>
    </>}
  </div>;
}
