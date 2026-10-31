import { useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import type { Material } from './types';
import { audioSessionFor } from './audioSession';
import './RestrictedAudio.css';

function time(seconds: number) { const n = Math.max(0, Math.floor(seconds)); return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, '0')}`; }
export default function RestrictedAudio({ material: m, userId, attemptId, onPosition }: { material: Material; userId: number; attemptId: number; onPosition?: (seconds: number) => void }) {
  const session = useMemo(() => audioSessionFor(userId, attemptId, m.materialVersionId, m.startSeconds ?? 0), [userId, attemptId, m.materialVersionId, m.startSeconds]);
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot);
  const audio = useRef<HTMLAudioElement>(null), mounted = useRef(false), trusted = useRef(state.seconds), callback = useRef(onPosition);
  const [playing, setPlaying] = useState(false), [muted, setMuted] = useState(false), [duration, setDuration] = useState(0), [mediaFailed, setMediaFailed] = useState(false);
  const start = m.startSeconds ?? 0, end = Math.min(m.endSeconds ?? duration, duration), length = Math.max(0, end - start);
  useLayoutEffect(() => { callback.current = onPosition; }, [onPosition]);
  useEffect(() => {
    mounted.current = true; void session.load();
    const player = audio.current;
    return () => { mounted.current = false; player?.pause(); };
  }, [session]);
  function restore() {
    const player = audio.current; if (!player || !Number.isFinite(player.duration)) return;
    setDuration(player.duration); setMediaFailed(false);
    const last = Math.max(start, Math.min(m.endSeconds ?? player.duration, session.getSnapshot().seconds));
    trusted.current = last; player.currentTime = last;
  }
  function record() {
    const player = audio.current; if (!player || player.seeking || !Number.isFinite(player.currentTime) || !Number.isFinite(player.duration)) return;
    const position = Math.max(start, Math.min(end || player.duration, player.currentTime));
    trusted.current = position; session.position(position); callback.current?.(position);
  }
  function finish() {
    const player = audio.current; if (!player || !session.getSnapshot().cycle) return;
    const last = Math.min(m.endSeconds ?? player.duration, player.duration);
    player.pause(); trusted.current = last; session.finish(last); callback.current?.(last); setPlaying(false);
  }
  async function toggle() {
    const player = audio.current; if (!player) return;
    if (!player.paused) { player.pause(); return; }
    if (!await session.reserve() || !mounted.current) return;
    try {
      trusted.current = session.getSnapshot().seconds;
      player.currentTime = trusted.current; callback.current?.(trusted.current);
      await player.play(); if (mounted.current) session.clearError();
    } catch { if (mounted.current) session.playbackError(); }
  }
  const exhausted = state.used === 2 && !state.cycle && !state.pending;
  const label = playing ? 'Tạm dừng' : state.pending ? 'Xác nhận lại lượt nghe' : state.cycle ? 'Nghe tiếp' : exhausted ? 'Đã hết lượt nghe' : `Nghe lần ${(state.used ?? 0) + 1}/2`;
  return <div className="restricted-audio">
    <audio ref={audio} preload="metadata" src={m.asset!.url} aria-label={m.title} onLoadedMetadata={restore}
      onPlay={() => { if (!session.getSnapshot().cycle) audio.current?.pause(); else setPlaying(true); }}
      onPause={() => { setPlaying(false); record(); }} onEnded={finish} onError={() => { setDuration(0); setMediaFailed(true); session.playbackError(); }}
      onRateChange={() => { if (audio.current && audio.current.playbackRate !== 1) audio.current.playbackRate = 1; }}
      onSeeking={() => { const player = audio.current; if (player && Math.abs(player.currentTime - trusted.current) > .05) player.currentTime = trusted.current; }}
      onTimeUpdate={() => { const player = audio.current; if (!player || player.seeking) return; if (length > 0 && player.currentTime >= end - .02) finish(); else record(); }} />
    <div className="restricted-audio-controls">
      <button className="button primary" onClick={() => void toggle()} disabled={state.busy || state.loading || exhausted || !duration || length <= 0}>{state.busy ? 'Đang xác nhận…' : label}</button>
      <div className="restricted-audio-progress"><progress aria-label="Tiến độ nghe" value={Math.min(length, Math.max(0, state.seconds - start))} max={length || 1} /><span>{time(state.seconds - start)} / {time(length)}</span></div>
      <button className="button secondary compact" aria-label={muted ? 'Bật âm thanh' : 'Tắt âm thanh'} aria-pressed={muted} onClick={() => { const player = audio.current; if (player) { player.muted = !player.muted; setMuted(player.muted); } }}>{muted ? 'Bật tiếng' : 'Tắt tiếng'}</button>
    </div>
    <p className="restricted-audio-note" role="status">{state.used === null ? 'Đang tải số lượt nghe…' : `Đã dùng ${state.used}/2 lượt nghe.`} Không tua. Tạm dừng rồi nghe tiếp không trừ thêm lượt.</p>
    <p className="restricted-audio-note">Lượt được tính khi bắt đầu nghe. Tải lại trang kết thúc lượt đang mở và không hoàn lại lượt đã dùng.</p>
    {!!duration && length <= 0 && <p className="message error" role="alert">Đoạn audio không có thời lượng hợp lệ để nghe.</p>}
    {(state.error || mediaFailed) && <div className="message error" role="alert">{state.error || 'Chưa tải được audio. Kiểm tra kết nối và tải lại.'}<button className="link-button" onClick={() => { setMediaFailed(false); audio.current?.load(); void session.load(); }}>Thử tải lại</button></div>}
  </div>;
}
