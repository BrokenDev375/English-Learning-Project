import { useState } from 'react';
import type { Answer, Item, Result, Snapshot, LearningActivity } from './types';
import { activityKinds } from '../content/authoring';
import { BridgeScene, GameKey, IslandScene, TreasureChest } from './GameArtwork';
import './gameplay.css';

export const gameNames = { SINGLE_CHOICE: 'Chọn cổng', FILL_BLANK: 'Mở khóa từ', MATCHING: 'Ghép thẻ mở rương', ORDERING: 'Xây cầu bằng từ' };
const symbols = { SINGLE_CHOICE: '◈', FILL_BLANK: <GameKey />, MATCHING: '♧', ORDERING: '▰' };

// Feedback belongs to the exact answer that the server checked, including word order.
export function sameGameAnswer(a: Answer | null, b: Answer | null): boolean {
  function canonical(value: Answer | null) {
    if (value?.type === 'MATCHING') return { type: value.type, pairs: [...value.pairs].sort((x, y) => x.leftId.localeCompare(y.leftId)) };
    if (value?.type === 'FILL_BLANK') return { type: value.type, values: [...value.values].sort((x, y) => x.blankId.localeCompare(y.blankId)) };
    return value;
  }
  return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
}

export function GameWorld({ items, activities, current, filled, select }: { items: Item[]; activities?: LearningActivity[]; current: Item; filled: number; select: (item: Item) => void }) {
  const games = items.filter((item, i) => items.findIndex(other => other.question.type === item.question.type) === i);
  const shortcuts = activities?.length ? activities.map(a => ({ id: a.id, label: activityKinds[a.kind], item: items.find(i => i.attemptQuestionId === a.attemptQuestionIds[0])!, active: a.attemptQuestionIds.includes(current.attemptQuestionId) })) : games.map(item => ({ id: item.question.type, label: gameNames[item.question.type], item, active: current.question.type === item.question.type }));
  return <section className="game-world" aria-label="Săn kho báu">
    <div className="game-world-heading"><div><span className="eyebrow">HÀNH TRÌNH TIẾNG ANH</span><h2>Săn kho báu</h2></div><span className="game-progress">{filled}/{items.length} thử thách đã điền</span></div>
    <IslandScene progress={filled / items.length} />
    <div className="game-world-bottom"><p>Hoàn thành các thử thách. Nộp bài để mở những rương trả lời đúng!</p><nav className="game-shortcuts" aria-label="Chọn trò chơi">{shortcuts.map(shortcut => <button type="button" key={shortcut.id} aria-current={shortcut.active ? 'true' : undefined} onClick={() => { select(shortcut.item); requestAnimationFrame(() => document.getElementById('active-game-question')?.scrollIntoView({ block: 'start' })); }}><span aria-hidden="true">{symbols[shortcut.item.question.type]}</span>{shortcut.label}</button>)}</nav></div>
  </section>;
}

export function GameResults({ result, snapshot }: { result: Result; snapshot: Snapshot }) {
  const [selected, setSelected] = useState(0), [replay, setReplay] = useState(0);
  const stars = result.scorePercent >= 80 ? 3 : result.scorePercent >= 50 ? 2 : result.correctCount > 0 ? 1 : 0;
  const fact = result.items[selected], item = snapshot.items.find(i => i.attemptQuestionId === fact?.attemptQuestionId);
  const open = fact?.finalOutcome === 'CORRECT';
  return <section className="game-vault panel" aria-label="Kho báu của lượt chơi">
    <div className="game-world-heading"><div><span className="eyebrow">CHUYẾN PHIÊU LƯU ĐÃ KẾT THÚC</span><h2>Bạn mở được {result.correctCount}/{result.totalQuestions} rương!</h2></div><span className="game-stars" aria-label={stars + '/3 sao'}>{[1, 2, 3].map(n => <span key={n} className={n <= stars ? 'earned' : ''} aria-hidden="true">★</span>)}</span></div>
    <div className="game-vault-scene" key={selected + '-' + replay}>{item?.question.type === 'ORDERING' ? <BridgeScene placed={fact.finalResponse?.type === 'ORDERING' ? fact.finalResponse.tokenIds.length : 0} total={item.question.content.tokens!.length} open={open} /> : <TreasureChest open={open} />}</div>
    <p className="game-vault-caption" role="status">Câu {selected + 1}: {open ? item?.question.type === 'ORDERING' ? 'Cầu đã hoàn thành. Cùng sang bờ bên kia!' : 'Chính xác! Rương kho báu đã mở.' : 'Rương còn khóa. Xem lời giải bên dưới để học lại nhé.'}</p>
    <div className="game-chests" role="group" aria-label="Các rương theo câu hỏi">{result.items.map((row, i) => <button type="button" key={row.attemptQuestionId} aria-label={'Rương câu ' + (i + 1) + (row.finalOutcome === 'CORRECT' ? ', đã mở' : ', còn khóa')} aria-pressed={selected === i} onClick={() => setSelected(i)}><TreasureChest small open={row.finalOutcome === 'CORRECT'} /><span>Câu {i + 1}</span></button>)}</div>
    <div className="game-vault-footer"><p>Sao đánh dấu kết quả lượt chơi. XP nhận được hiển thị ở phần phần thưởng.</p><button type="button" className="button secondary" onClick={() => setReplay(value => value + 1)}>Xem lại hoạt cảnh</button></div>
  </section>;
}
