import { useId, useLayoutEffect, useRef, useState } from 'react';
import type { DragEvent } from 'react';
import type { Option, Pair } from '../content/authoring';
import './interactive.css';
import { BridgeScene, GameKey, TreasureChest } from './GameArtwork';

const dragType = 'application/x-english-answer';
function dragged(event: DragEvent, scope: string): string | null {
  try { const data = JSON.parse(event.dataTransfer.getData(dragType)); return data.scope === scope && typeof data.id === 'string' ? data.id : null; }
  catch { return null; }
}

export function MatchingCards({ left, right, pairs, onChange, disabled = false, play = false, verified = false }: {
  left: Option[]; right: Option[]; pairs: Pair[]; onChange: (pairs: Pair[]) => void; disabled?: boolean; play?: boolean; verified?: boolean;
}) {
  const scope = useId(), [selected, setSelected] = useState<string | null>(null);
  const board = useRef<HTMLDivElement>(null);
  const [lines, setLines] = useState<{ id: string; path: string }[]>([]);
  useLayoutEffect(() => {
    const element = board.current;
    if (!play || !element) return;
    function measure() {
      const bounds = element!.getBoundingClientRect();
      setLines(pairs.flatMap(pair => {
        const from = element!.querySelector<HTMLButtonElement>('[data-left-id="' + CSS.escape(pair.leftId) + '"]')?.getBoundingClientRect();
        const to = element!.querySelector<HTMLButtonElement>('[data-right-id="' + CSS.escape(pair.rightId) + '"]')?.getBoundingClientRect();
        if (!from || !to) return [];
        const x1 = from.right - bounds.left, y1 = from.top + from.height / 2 - bounds.top;
        const x2 = to.left - bounds.left, y2 = to.top + to.height / 2 - bounds.top, mid = (x1 + x2) / 2;
        return [{ id: pair.leftId, path: `M${x1} ${y1}C${mid} ${y1},${mid} ${y2},${x2} ${y2}` }];
      }));
    }
    measure();
    const observer = new ResizeObserver(measure); observer.observe(element);
    element.querySelectorAll('button').forEach(button => observer.observe(button));
    return () => observer.disconnect();
  }, [play, pairs]);
  function match(leftId: string, rightId: string) {
    if (disabled || !left.some(l => l.id === leftId) || !right.some(r => r.id === rightId)) return;
    onChange([...pairs.filter(p => p.leftId !== leftId && p.rightId !== rightId), { leftId, rightId }]); setSelected(null);
  }
  return <div className={'matching-game' + (play ? ' treasure-matching' : '')}>
    {play && <div className="matching-treasure" data-open={verified}><TreasureChest open={verified} /><div><strong>{verified ? 'Rương đã mở!' : 'Ghép thẻ để tìm chìa khóa'}</strong><div className="key-slots" aria-hidden="true">{left.map((item, i) => <span key={item.id} className={i < pairs.length ? 'filled' : ''}><GameKey /></span>)}</div><p role="status">{verified ? 'Chính xác! Bạn đã mở được kho báu.' : pairs.length === left.length ? 'Đã ghép đủ. Kiểm tra đáp án ở câu hướng dẫn hoặc nộp bài để mở rương.' : 'Mỗi cặp thẻ là một mảnh chìa khóa. Hãy nối các thẻ tương ứng.'}</p></div></div>}
    <p className="muted" id={scope}>Chọn một thẻ bên trái rồi chọn thẻ tương ứng bên phải. Bạn cũng có thể kéo thẻ trái sang phải.</p>
    <div className="matching-board" ref={board} aria-describedby={scope}>
      {play && <svg className="matching-connections" aria-hidden="true">{lines.map(line => <path key={line.id} d={line.path} />)}</svg>}
      <div className="matching-left" role="group" aria-label="Các thẻ bên trái">{left.map((item, i) => {
        const pair = pairs.find(p => p.leftId === item.id);
        return <button type="button" key={item.id} disabled={disabled} data-left-id={item.id} aria-pressed={selected === item.id}
          className={'match-card' + (selected === item.id ? ' selected' : '') + (pair ? ' paired' : '')}
          onClick={() => setSelected(selected === item.id ? null : item.id)} draggable={!disabled}
          onDragStart={event => { event.dataTransfer.setData(dragType, JSON.stringify({ scope, id: item.id })); event.dataTransfer.effectAllowed = 'link'; setSelected(item.id); }}>
          <b>{i + 1}</b><span>{item.text}</span>{pair && <small>Đã ghép</small>}
        </button>;
      })}</div>
      <div className="matching-right" role="group" aria-label="Các thẻ bên phải">{right.map(item => {
        const pair = pairs.find(p => p.rightId === item.id), index = pair ? left.findIndex(l => l.id === pair.leftId) + 1 : null;
        return <button type="button" key={item.id} disabled={disabled} data-right-id={item.id} className={'match-card' + (pair ? ' paired' : '')}
          aria-label={item.text + (index ? ' · Ghép với mục ' + index : '')} onClick={() => { if (selected) match(selected, item.id); }}
          onDragOver={event => { if (!disabled) { event.preventDefault(); event.dataTransfer.dropEffect = 'link'; } }}
          onDrop={event => { event.preventDefault(); const id = dragged(event, scope); if (id) match(id, item.id); }}>
          {index !== null && <b>{index}</b>}<span>{item.text}</span>
        </button>;
      })}</div>
    </div>
    <p aria-live="polite">Đã ghép {pairs.length}/{left.length} cặp{selected ? ' · Chọn thẻ bên phải để ghép.' : '.'}</p>
    {pairs.length > 0 && <ul className="matched-pairs">{left.map((l, i) => {
      const pair = pairs.find(p => p.leftId === l.id); if (!pair) return null;
      return <li key={l.id}><span>{l.text} → {right.find(r => r.id === pair.rightId)?.text}</span><button className="link-button" type="button" disabled={disabled} aria-label={'Bỏ cặp ' + (i + 1)} onClick={() => onChange(pairs.filter(p => p.leftId !== l.id))}>Bỏ ghép</button></li>;
    })}</ul>}
  </div>;
}

export function OrderingWords({ tokens, tokenIds, onChange, disabled = false, play = false, verified = false }: {
  tokens: Option[]; tokenIds: string[]; onChange: (ids: string[]) => void; disabled?: boolean; play?: boolean; verified?: boolean;
}) {
  const scope = useId();
  function insert(id: string, at: number) {
    if (disabled || !tokens.some(t => t.id === id)) return;
    const rows = tokenIds.filter(t => t !== id); rows.splice(Math.min(at, rows.length), 0, id); onChange(rows);
  }
  function drop(event: DragEvent, at: number) { event.preventDefault(); event.stopPropagation(); const id = dragged(event, scope); if (id) insert(id, at); }
  function drag(event: DragEvent, id: string) { event.dataTransfer.setData(dragType, JSON.stringify({ scope, id })); event.dataTransfer.effectAllowed = 'move'; }
  return <div className={'ordering-game' + (play ? ' bridge-ordering' : '')}>
    {play && <div className="word-bridge"><BridgeScene placed={tokenIds.length} total={tokens.length} open={verified} /><p role="status">{verified ? 'Câu chính xác. Cầu đã nối hai bờ!' : tokenIds.length === tokens.length ? 'Đã đặt đủ ván. Kiểm tra đáp án ở câu hướng dẫn hoặc nộp bài để qua cầu.' : 'Mỗi từ là một tấm ván. Xếp thành câu để giúp nhà thám hiểm qua sông.'}</p></div>}
    <p className="muted">Bấm hoặc kéo các từ để tạo câu. Dùng nút mũi tên để đổi vị trí; bấm từ trong câu để bỏ chọn.</p>
    <div className="word-bank" role="group" aria-label="Các từ chưa chọn" onDragOver={event => { if (!disabled) event.preventDefault(); }} onDrop={event => {
      event.preventDefault(); const id = dragged(event, scope); if (!disabled && id) onChange(tokenIds.filter(t => t !== id));
    }}>{tokens.filter(t => !tokenIds.includes(t.id)).map(t => <button className="word-token" type="button" key={t.id} disabled={disabled} draggable={!disabled} onDragStart={event => drag(event, t.id)} onClick={() => insert(t.id, tokenIds.length)}>{t.text}</button>)}
      {tokenIds.length === tokens.length && <span className="muted">Đã chọn đủ từ.</span>}
    </div>
    <ol className="ordered-words" aria-label="Câu trả lời đã sắp xếp" onDragOver={event => { if (!disabled) event.preventDefault(); }} onDrop={event => drop(event, tokenIds.length)}>
      {tokenIds.map((id, i) => <li key={id} onDragOver={event => { if (!disabled) event.preventDefault(); }} onDrop={event => drop(event, i)}>
        <button className="word-token" type="button" disabled={disabled} draggable={!disabled} onDragStart={event => drag(event, id)} aria-label={'Bỏ từ ở vị trí ' + (i + 1) + ': ' + tokens.find(t => t.id === id)?.text} onClick={() => onChange(tokenIds.filter(t => t !== id))}>{tokens.find(t => t.id === id)?.text}</button>
        <div className="word-movers"><button type="button" disabled={disabled || i === 0} aria-label={'Chuyển từ ' + (i + 1) + ' sang trái'} onClick={() => insert(id, i - 1)}>←</button><button type="button" disabled={disabled || i === tokenIds.length - 1} aria-label={'Chuyển từ ' + (i + 1) + ' sang phải'} onClick={() => insert(id, i + 1)}>→</button></div>
      </li>)}
      {tokenIds.length === 0 && <li className="word-placeholder">Chọn từ ở phía trên để bắt đầu.</li>}
    </ol>
    <p aria-live="polite">Đã chọn {tokenIds.length}/{tokens.length} từ.</p>
  </div>;
}
