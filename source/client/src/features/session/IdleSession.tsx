import { useEffect, useRef, useState } from 'react';
import { ActivityTracker } from './tracker';
import type { IdleView } from './tracker';
import './session.css';

export default function IdleSession() {
  const [view, setView] = useState<IdleView>({ remaining: 0, warning: false, uncertain: false, busy: false, error: '' });
  const tracker = useRef<ActivityTracker | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  useEffect(() => { const next = new ActivityTracker(setView); tracker.current = next; next.start(); return () => { next.stop(); tracker.current = null; }; }, []);
  useEffect(() => { if (view.warning) button.current?.focus(); }, [view.warning]);
  if (!view.warning && !view.uncertain) return null;
  return <section className="session-warning" aria-label="Trạng thái phiên làm việc">
    <div><strong>{view.uncertain ? 'Chưa xác minh được phiên' : 'Phiên làm việc sắp hết hạn'}</strong>
      <p role="status">{view.uncertain ? view.error || 'Kết nối đang gián đoạn. Bản nháp đã lưu tại máy được giữ lại.' : `Phiên sẽ hết hạn sau ${view.remaining} giây nếu không tương tác.`}</p></div>
    <button ref={button} className="button primary" disabled={view.busy} onClick={() => void tracker.current?.continueWorking()}>{view.busy ? 'Đang xác minh…' : 'Tiếp tục làm việc'}</button>
  </section>;
}
