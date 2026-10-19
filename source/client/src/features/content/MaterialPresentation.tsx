import { useEffect, useRef, useState } from 'react';
import type { MaterialContent } from './materials';

export default function MaterialPresentation({ material }: { material: MaterialContent }) {
  const [failed, setFailed] = useState(false);
  const audio = useRef<HTMLAudioElement>(null);
  const assetId = material.assetId;
  useEffect(() => { setFailed(false); }, [assetId]);
  const start = material.kind === 'AUDIO' ? material.startSeconds : 0;
  const end = material.kind === 'AUDIO' ? material.endSeconds : null;
  function clamp() {
    const player = audio.current; if (!player || !Number.isFinite(player.duration)) return;
    const limit = end ?? player.duration;
    if (player.currentTime < start) player.currentTime = start;
    if (player.currentTime > limit) player.currentTime = limit;
  }
  return <article className="material-presentation">
    <h2>{material.title || 'Tài liệu chưa đặt tên'}</h2>
    {material.kind === 'READING' && <p className="reading-text">{material.readingText || 'Chưa có nội dung đoạn đọc.'}</p>}
    {material.kind !== 'AUDIO' && assetId && <figure><img src={'/api/assets/' + assetId} alt={material.altText ?? ''} onError={() => setFailed(true)} />
      {material.kind === 'IMAGE' && material.caption && <figcaption>{material.caption}</figcaption>}</figure>}
    {material.kind === 'AUDIO' && <><audio ref={audio} controls preload="metadata" src={'/api/assets/' + assetId} onError={() => setFailed(true)}
      onLoadedMetadata={clamp} onSeeking={clamp} onPlay={() => { if (audio.current && (audio.current.currentTime < start || (end !== null && audio.current.currentTime >= end))) audio.current.currentTime = start; }}
      onTimeUpdate={() => { if (audio.current && end !== null && audio.current.currentTime >= end) { audio.current.pause(); if (Math.abs(audio.current.currentTime - end) > 0.01) audio.current.currentTime = end; } }} />
      {(start > 0 || end !== null) && <p className="muted">Đoạn nghe từ {start} giây{end !== null ? ' đến ' + end + ' giây' : ' đến hết audio'}.</p>}</>}
    {failed && <div className="message error" role="alert">Chưa tải được file. Kiểm tra kết nối hoặc mở lại tài liệu.</div>}
    <div className="material-source">Nguồn: {material.source.label}
      {material.source.url && <> · <a href={material.source.url} target="_blank" rel="noreferrer">Xem nguồn</a></>}
      {material.source.license && <small>{material.source.license}</small>}</div>
  </article>;
}
