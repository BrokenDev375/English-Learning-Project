import { useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { navigate } from '../../navigation';
import { sessionFor } from './session';
import { answered, complete, modeNames, outcomeNames } from './types';
import type { Item } from './types';
import AnswerFields, { KeyExplanation, responseText } from './AnswerFields';
import LearningMaterial from './LearningMaterial';
import AttemptResult from './AttemptResult';
import { GameWorld, sameGameAnswer } from './GameWorld';
import { useOnline } from '../connection/useOnline';
import InlineReading from './InlineReading';
import { activityKinds } from '../content/authoring';

export default function AttemptRunner({ id, userId }: { id: number; userId: number }) {
  const session = useMemo(() => sessionFor(userId, id), [userId, id]);
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot);
  const { snapshot: s, draft, pending, conflict } = state;
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const online = useOnline();
  useEffect(() => { void session.load(); }, [session]);
  const disabled = state.busy || !!pending || !!conflict || state.loginRequired || state.loading;
  const answerOf = (item: Item) => draft?.answers.find(a => a.attemptQuestionId === item.attemptQuestionId)?.response ?? null;
  if (!s || !draft) return <section className="panel learning-panel"><h1>Mở lượt làm</h1><p role={state.error ? 'alert' : 'status'}>{state.error || 'Đang khôi phục bài làm…'}</p>{state.loginRequired && <button className="button secondary" onClick={() => window.dispatchEvent(new Event('session-expired'))}>Đăng nhập lại</button>}<button className="button secondary" disabled={state.loading} onClick={() => void session.load()}>Thử tải lại</button><button className="link-button" onClick={() => navigate({ attempt: null })}>Về danh sách</button></section>;
  const item = s.items.find(i => i.attemptQuestionId === draft.playerState.currentAttemptQuestionId) ?? s.items[0];
  const index = s.items.indexOf(item), response = answerOf(item);
  const material = s.materials.find(m => m.materialVersionId === item.materialVersionId);
  const activity = s.activities?.find(a => a.attemptQuestionIds.includes(item.attemptQuestionId));
  const passage = activity?.passages.find(p => p.materialVersionId === item.materialVersionId);
  const count = draft.answers.filter(a => answered(a.response)).length;
  const feedback = state.feedback[item.attemptQuestionId];
  const fieldsDisabled = s.state === 'SUBMITTED' || state.loginRequired || pending?.kind === 'submit';
  function select(next: Item, focus = true) { session.player({ ...session.getSnapshot().draft!.playerState, currentAttemptQuestionId: next.attemptQuestionId }); session.flushLocal();
    if (focus) requestAnimationFrame(() => document.getElementById('reading-blank-' + next.attemptQuestionId)?.focus()); }
  return <section className="learning-space" onBlur={() => session.flushLocal()}>
    <div className="learning-heading"><div><span className="eyebrow">{modeNames[s.mode]} · LƯỢT #{s.attemptId}</span><h1>{s.lesson?.lesson.title ?? modeNames[s.mode]}<span>.</span></h1></div><button className="button secondary" onClick={() => navigate({ attempt: null, tab: 'history', unit: null, lesson: null })}>Về danh sách lượt</button></div>
    <div className="learning-status" role="status">{state.busy ? 'Đang gửi và đối soát với máy chủ…' : pending ? pending.kind === 'submit' ? 'Chờ nộp · Chưa nhận đủ xác nhận.' : 'Chưa nhận đủ xác nhận. Chưa thể gửi thao tác mới.' : conflict ? 'Cần đối chiếu thay đổi từ máy chủ.' : s.state === 'SUBMITTED' ? 'Đã nộp bài trên máy chủ' : session.dirty ? state.localSaved ? 'Đã lưu tại máy · Chưa lưu lên máy chủ' : 'Đang sửa · Chưa lưu lên máy chủ' : 'Đã lưu trên máy chủ'}<span>Đã trả lời {count}/{s.items.length} câu</span></div>
    {state.storageError && <div className="message warning" role="alert">{state.storageError} Giữ trang đang mở để tránh mất bản chưa lưu.</div>}
    {state.copies.length > 0 && <div className="message warning"><p>Có bản nhập từ tab khác. Chỉ khôi phục nếu bạn muốn dùng bản đó.</p>{state.copies.map(copy => <button className="button secondary" key={copy.key} onClick={() => { if (window.confirm('Khôi phục bản nhập này rồi đối chiếu với máy chủ?')) void session.restoreCopy(copy.key); }}>Khôi phục bản {new Date(copy.updatedAt).toLocaleString('vi-VN')}</button>)}</div>}
    {state.error && <div className="message error" role="alert">{state.error}{pending && !state.busy && !state.loginRequired && <button className="button secondary" onClick={() => void session.retry()}>Gửi lại thao tác và đối soát</button>}</div>}
    {state.loginRequired && <div className="message warning">Phiên đã hết hạn. Câu trả lời đang nhập vẫn được giữ trong phiên trình duyệt này.<button className="button secondary" onClick={() => window.dispatchEvent(new Event('session-expired'))}>Đăng nhập lại</button></div>}
    {conflict && <div className="panel conflict-panel"><h2>Đối chiếu bài làm</h2><p>Máy chủ đang ở phiên bản {conflict.revision}{conflict.state === 'SUBMITTED' ? ' và đã nộp bài' : ''}. Câu trả lời đang nhập chưa bị ghi đè.</p>
      <div className="table-wrap"><table><thead><tr><th>Câu</th><th>Đang nhập</th><th>Trên máy chủ</th></tr></thead><tbody>{s.items.map((i, n) => <tr key={i.attemptQuestionId}><td>{n + 1}</td><td>{responseText(i.question, answerOf(i))}</td><td>{responseText(i.question, conflict.items.find(v => v.attemptQuestionId === i.attemptQuestionId)?.response ?? null)}</td></tr>)}</tbody></table></div>
      <div className="learning-actions"><button className="button secondary" onClick={() => { if (window.confirm('Bỏ các thay đổi đang nhập và dùng bản máy chủ?')) session.resolveConflict(false); }}>Dùng bản máy chủ</button>{conflict.state === 'IN_PROGRESS' && <button className="button secondary" onClick={() => { if (window.confirm('Giữ toàn bộ câu trả lời đang nhập sau khi đối chiếu? Lần lưu tiếp theo sẽ thay bản máy chủ bằng các câu trả lời này.')) session.resolveConflict(true); }}>Giữ bản đang nhập sau đối chiếu</button>}</div></div>}
    {s.state === 'SUBMITTED' ? state.result ? <AttemptResult session={session} state={state} /> : <p>Đang tải kết quả… <button onClick={() => void session.load()}>Tải lại</button></p> : <>
      <GameWorld items={s.items} activities={s.activities} current={item} filled={s.items.filter(row => complete(row.question, answerOf(row))).length} select={select} />
      {activity && <div className="panel activity-heading"><h2>{activityKinds[activity.kind]}</h2><p>{item.practiceRole === 'GUIDED' ? 'Một câu hướng dẫn để làm quen. Bạn có thể mở gợi ý hoặc kiểm tra, rồi chuyển sang phần tự làm.' : 'Phần tự làm. Đáp án và giải thích sẽ mở sau khi nộp bài.'}</p><nav className="activity-navigation" aria-label="Các câu trong hoạt động">{activity.attemptQuestionIds.map(id => { const row = s.items.find(q => q.attemptQuestionId === id)!; return <button type="button" className="button secondary compact" key={id} aria-current={id === item.attemptQuestionId ? 'step' : undefined} onClick={() => select(row)}>Câu {s.items.indexOf(row) + 1}{row.practiceRole === 'GUIDED' ? ' · Hướng dẫn' : ''}</button>; })}</nav></div>}
      {s.lesson && <details className="panel learning-panel"><summary>Hướng dẫn và mục tiêu bài học</summary><ul>{s.lesson.objectives.map(o => <li key={o.id}>{o.name}</li>)}</ul>{s.lesson.instructions.map((block, n) => <div key={n}><p>{block.text}</p>{block.kind === 'EXAMPLE' && block.translation && <p className="muted">{block.translation}</p>}</div>)}</details>}
      <div className="runner-layout"><nav className="panel question-nav" aria-label="Các câu trong lượt làm">{s.items.map((i, n) => <button key={i.attemptQuestionId} className={i === item ? 'current' : answered(answerOf(i)) ? 'answered' : ''} aria-current={i === item ? 'step' : undefined} aria-label={'Câu ' + (n + 1) + (answered(answerOf(i)) ? ', đã trả lời' : ', chưa trả lời')} onClick={() => select(i)}>{n + 1}</button>)}</nav>
        <div className={'runner-main' + (activity?.kind === 'READING_CHOICE' ? ' reading-choice-layout' : '')}>{material && <LearningMaterial key={material.materialVersionId} material={material} collapsibleReading={activity?.kind === 'READING_CHOICE'} readingContent={passage ? <InlineReading passage={passage} items={s.items} answerOf={id => answerOf(s.items.find(q => q.attemptQuestionId === id)!)} onChange={(id, a) => session.edit(id, a)} onSelect={id => { if (id !== item.attemptQuestionId) select(s.items.find(q => q.attemptQuestionId === id)!, false); }} activeId={item.attemptQuestionId} disabled={fieldsDisabled} /> : undefined} restoreVersion={state.playerRestoreVersion} savedSeconds={draft.playerState.audioPositions.find(p => p.materialVersionId === material.materialVersionId)?.seconds} onPosition={seconds => {
          if (fieldsDisabled) return;
          const player = session.getSnapshot().draft!.playerState;
          const previous = player.audioPositions.find(p => p.materialVersionId === material.materialVersionId)?.seconds;
          if (previous !== undefined && Math.abs(previous - seconds) < 0.5) return;
          session.player({ ...player, audioPositions: [...player.audioPositions.filter(p => p.materialVersionId !== material.materialVersionId), { materialVersionId: material.materialVersionId, seconds }] });
        }} />}
          <article id="active-game-question" className="panel learning-panel"><span className="eyebrow">CÂU {index + 1} · {item.practiceRole === 'GUIDED' ? 'CÓ HƯỚNG DẪN' : 'TỰ LÀM'}</span>
            {passage ? <><h2>{item.question.prompt}</h2><p className="muted">Nhập đáp án trực tiếp tại ô câu {index + 1} trong đoạn văn phía trên.</p><button type="button" className="link-button" onClick={() => document.getElementById('reading-blank-' + item.attemptQuestionId)?.focus()}>Đến ô trả lời</button></> : <AnswerFields play verified={feedback?.data.outcome === 'CORRECT' && sameGameAnswer(feedback.response, response)} key={item.attemptQuestionId} question={item.question} prefix={'item-' + item.attemptQuestionId} value={response} onChange={a => session.edit(item.attemptQuestionId, a)} disabled={fieldsDisabled} />}
            {item.practiceRole === 'GUIDED' && <div className="learning-actions"><button className="button secondary" disabled={disabled || !online || !item.hintAvailable} onClick={() => void session.mutate('hint', item.attemptQuestionId)}>{item.hintOpened ? 'Xem lại gợi ý' : 'Mở gợi ý'}</button><button className="button secondary" disabled={disabled || !online || !complete(item.question, response)} onClick={() => void session.mutate('check', item.attemptQuestionId, response)}>Kiểm tra đáp án</button></div>}
            {state.hints[item.attemptQuestionId] && <div className="message warning"><strong>Gợi ý:</strong> {state.hints[item.attemptQuestionId]}</div>}
            {feedback && <div className={"message " + (feedback.data.outcome === "CORRECT" ? "success" : "warning")}><strong>{outcomeNames[feedback.data.outcome]}</strong><p>Phản hồi cho đáp án đã kiểm tra: {responseText(item.question, feedback.response)}</p><KeyExplanation question={item.question} answerKey={feedback.data.answerKey} explanation={feedback.data.explanation} /></div>}
          </article>
          <div className="learning-actions runner-pagination"><button className="button secondary" disabled={index === 0} onClick={() => select(s.items[index - 1])}>Câu trước</button><button className="button secondary" disabled={index === s.items.length - 1} onClick={() => select(s.items[index + 1])}>Câu tiếp theo</button></div>
        </div></div>
      <div className="panel runner-save"><p className="muted">Câu trả lời và vị trí nghe được tự lưu tại máy, rồi đồng bộ khi có kết nối. Hãy kiểm tra trạng thái lưu trước khi đóng trang.</p><div className="learning-actions"><button className="button secondary" disabled={disabled || !session.dirty} onClick={() => void session.mutate('draft')}>Lưu nháp lên máy chủ</button><button className="button primary" disabled={disabled} onClick={() => setConfirmSubmit(true)}>Nộp bài</button></div></div>
      {confirmSubmit && <div className="modal-backdrop"><div className="modal" role="dialog" aria-modal="true" aria-labelledby="submit-heading" onKeyDown={e => {
        if (e.key === 'Escape') setConfirmSubmit(false);
        if (e.key === 'Tab') {
          const buttons = [...e.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
          const first = buttons[0], last = buttons.at(-1);
          if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
          else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
        }
      }}><h2 id="submit-heading">Nộp bài này?</h2><p>Đã nhập {count}/{s.items.length} câu. {s.items.filter(i => !complete(i.question, answerOf(i))).length} câu chưa đầy đủ.</p><p>Bạn vẫn có thể nộp sớm. Điểm nộp sẽ được giữ nguyên khi chữa bài; trả lời đủ mọi câu là điều kiện hoàn thành bài, không bắt buộc kiểm tra từng câu hướng dẫn.</p><div className="modal-actions"><button autoFocus className="button secondary" onClick={() => setConfirmSubmit(false)}>Tiếp tục làm</button><button className="button primary" disabled={disabled} onClick={() => { setConfirmSubmit(false); void session.mutate('submit'); }}>Xác nhận nộp bài</button></div></div></div>}
    </>}
  </section>;
}
