import { requestId } from '../../api';
import { OrderingWords } from '../learning/InteractiveAnswers';
import type { QuestionData, Option } from './authoring';

export default function OrderingEditor({ value: q, onChange }: { value: QuestionData; onChange: (q: QuestionData) => void }) {
  const tokens = q.content.tokens ?? [], orders = q.answerKey.acceptedOrders ?? [];
  const setOrders = (acceptedOrders: string[][]) => onChange({ ...q, answerKey: { acceptedOrders } });
  function setTokens(rows: Option[]) {
    const ids = rows.map(t => t.id), added = ids.filter(id => !tokens.some(t => t.id === id));
    onChange({ ...q, content: { tokens: rows }, answerKey: { acceptedOrders: orders.map(order => [...order.filter(id => ids.includes(id)), ...added]) } });
  }
  return <div className="catalog-wide question-block ordering-author">
    <h3>Từ và cụm từ</h3><p className="muted">Nhập 2–20 thẻ. Các thẻ được đảo khi học viên bắt đầu; thứ tự đó được giữ khi tiếp tục bài.</p>
    {tokens.map((token, i) => <div className="question-row" key={token.id}><span>{i + 1}</span><input aria-label={'Từ hoặc cụm từ ' + (i + 1)} maxLength={100} value={token.text} onChange={event => setTokens(tokens.map(t => t.id === token.id ? { ...t, text: event.target.value } : t))} /><button className="link-button" type="button" aria-label={'Bỏ thẻ từ ' + (i + 1)} onClick={() => setTokens(tokens.filter(t => t.id !== token.id))}>Bỏ</button></div>)}
    <button className="button secondary" type="button" disabled={tokens.length >= 20} onClick={() => setTokens([...tokens, { id: 'word-' + requestId().slice(0, 8), text: '' }])}>Thêm thẻ từ</button>
    <h3>Các thứ tự đúng được chấp nhận</h3><p className="muted">Mỗi cách phải dùng đủ các thẻ một lần. Có thể khai báo nhiều cách đúng; các thẻ có chữ giống nhau được chấm theo câu tạo thành.</p>
    {orders.map((order, i) => <section key={i} aria-label={'Thứ tự đúng ' + (i + 1)}><h4>Cách đúng {i + 1}</h4><OrderingWords tokens={tokens} tokenIds={order} onChange={ids => setOrders(orders.map((row, n) => n === i ? ids : row))} /><button className="link-button" type="button" disabled={orders.length <= 1} onClick={() => setOrders(orders.filter((_, n) => n !== i))}>Bỏ cách đúng {i + 1}</button></section>)}
    <button className="button secondary" type="button" disabled={orders.length >= 10} onClick={() => setOrders([...orders, [...(orders[0] ?? tokens.map(t => t.id))]])}>Thêm cách sắp xếp đúng</button>
  </div>;
}
