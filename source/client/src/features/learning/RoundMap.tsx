import type { RoundProgress } from './journey';
import { RoundMeter } from './journey';

export function RoundRules() {
  return <div className="round-rules" aria-label="Cách mở vòng chơi">
    <p><b>1</b><span>Chọn vòng đang mở</span></p><p><b>2</b><span>Làm đủ bài học và nộp bài viết nếu có</span></p><p><b>3</b><span>Mở khóa vòng tiếp theo, không phải chờ chấm</span></p>
  </div>;
}
export default function RoundMap({ rounds, open, disabled }: { rounds: RoundProgress[]; open: (round: RoundProgress) => void; disabled: boolean }) {
  return <><div className="round-legend" aria-label="Trạng thái vòng"><span>▶ Đang mở</span><span>✓ Đã hoàn thành</span><span>🔒 Chưa mở khóa</span></div>
    <ol className="round-map" aria-label="Bản đồ vòng chơi">{rounds.map(round => {
      const done = round.state === 'COMPLETED', status = round.locked ? 'locked' : done ? 'done' : 'open';
      return <li className={'round-stop ' + status} key={round.unit.id}>
        <div className="round-node" aria-hidden="true"><span>{round.locked ? '🔒' : done ? '✓' : '▶'}</span><small>VÒNG {round.number}</small></div>
        <article className="panel round-map-card" aria-label={'Vòng ' + round.number + ': ' + round.unit.title}>
          <div className="round-map-heading"><span className="eyebrow">VÒNG {round.number}</span><span className={'round-state ' + status}>{round.locked ? 'Chưa mở khóa' : done ? 'Đã hoàn thành' : round.startedLessons ? 'Đang chơi' : 'Bắt đầu ở đây'}</span></div>
          <h2>{round.unit.title}</h2><p className="muted">{round.unit.description}</p><RoundMeter {...round} />
          {round.locked && <p className="round-lock-reason">Hoàn thành Vòng {round.prerequisiteRoundNumber} — {round.prerequisiteTitle} để mở vòng này.</p>}
          <button className={'button ' + (status === 'open' ? 'primary' : 'secondary')} disabled={disabled || round.locked} onClick={() => open(round)}>
            {round.locked ? 'Vòng ' + round.number + ' đang khóa' : done ? 'Chơi lại vòng ' + round.number : round.startedLessons ? 'Chơi tiếp vòng ' + round.number + ' →' : 'Chơi vòng ' + round.number + ' →'}
          </button>
        </article>
      </li>;
    })}</ol></>;
}
