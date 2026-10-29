import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import type { Material } from './types';
import RestrictedAudio from './RestrictedAudio';
export default function LearningMaterial({ material: m, savedSeconds, restoreVersion = 0, onPosition, attemptId, userId }: { material: Material; savedSeconds?: number; restoreVersion?: number; onPosition?: (seconds: number) => void; attemptId?: number; userId?: number }) {
  const audio = useRef<HTMLAudioElement>(null);
  const restore = useRef(savedSeconds);
  const latestSaved = useRef(savedSeconds);
  const [failed, setFailed] = useState(false), [retry, setRetry] = useState(0);
  const bounds = useCallback((player: HTMLAudioElement) => {
    const duration = Number.isFinite(player.duration) ? player.duration : 86400;
    const end = Math.max(0, Math.min(m.endSeconds ?? duration, duration, 86400));
    return { start: Math.min(Math.max(0, m.startSeconds ?? 0), end), end };
  }, [m.startSeconds, m.endSeconds]);
  const clamp = useCallback((restoring = false) => {
    const player = audio.current; if (!player || !Number.isFinite(player.duration)) return;
    const { start, end } = bounds(player);
    const current = Math.min(end, Math.max(start, restoring ? restore.current ?? start : player.currentTime));
    if (Math.abs(player.currentTime - current) > 0.005) player.currentTime = current;
  }, [bounds]);
  useLayoutEffect(() => { latestSaved.current = savedSeconds; }, [savedSeconds]);
  // Only adopting a server draft restores playback. Local ticks and save ACKs keep the current player position.
  useLayoutEffect(() => { restore.current = latestSaved.current; clamp(true); }, [restoreVersion, clamp]);
  function record() {
    const player = audio.current; if (!player || !Number.isFinite(player.duration)) return;
    const { start, end } = bounds(player);
    onPosition?.(Math.max(start, Math.min(end, player.currentTime)));
  }
  return <article className="material-presentation learning-material"><h2>{m.title}</h2>
    {m.kind === 'READING' && <p className="reading-text">{m.readingText}</p>}
    {m.kind !== 'AUDIO' && m.asset && <figure><img key={retry} src={m.asset.url + (retry ? '?retry=' + retry : '')} alt={m.altText ?? ''} onError={() => setFailed(true)} />{m.caption && <figcaption>{m.caption}</figcaption>}</figure>}
    {m.kind === 'AUDIO' && m.asset && attemptId !== undefined && userId !== undefined ? <RestrictedAudio material={m} attemptId={attemptId} userId={userId} onPosition={onPosition} /> : m.kind === 'AUDIO' && m.asset && <><audio ref={audio} controls preload="metadata" aria-label={m.title} src={m.asset.url} onError={() => setFailed(true)}
      onLoadedMetadata={() => clamp(true)} onSeeking={() => { clamp(); record(); }} onPause={record}
      onPlay={() => { const p = audio.current; if (p && p.currentTime >= bounds(p).end) p.currentTime = bounds(p).start; clamp(); }}
      onTimeUpdate={() => { const p = audio.current; if (!p) return; if (p.currentTime >= bounds(p).end) { p.pause(); clamp(); } record(); }} />
      <p className="muted">Đoạn nghe từ {m.startSeconds ?? 0} giây{m.endSeconds !== null ? ' đến ' + m.endSeconds + ' giây' : ' đến hết audio'}. Vị trí nghe được gửi cùng lần lưu bài.</p></>}
    {failed && <div className="message error" role="alert">Chưa tải được file. Kiểm tra kết nối rồi thử lại.<button className="link-button" onClick={() => { setFailed(false); setRetry(v => v + 1); restore.current = audio.current?.currentTime ?? savedSeconds; audio.current?.load(); }}>Tải lại học liệu</button></div>}
    <div className="material-source">Nguồn: {m.source.label}{m.source.url && <> · <a href={m.source.url} target="_blank" rel="noreferrer">Xem nguồn</a></>}{m.source.license && <small>{m.source.license}</small>}</div>
  </article>;
}
