import { useSyncExternalStore } from 'react';

// Keep only screen names and IDs in the URL/history. Content is always read
// through authenticated APIs rather than cached in browser history entries.
type Changes = Record<string, string | null>;
interface Options { replace?: boolean; reset?: boolean }
const eventName = 'app-navigation';
function read(hash = window.location.hash) {
  const [path, query = ''] = hash.replace(/^#\/?/, '').split('?');
  return { view: path || 'home', params: new URLSearchParams(query) };
}
function target(changes: Changes, reset = false) {
  const current = read();
  const view = changes.view ?? current.view;
  const params = reset ? new URLSearchParams() : current.params;
  for (const [key, value] of Object.entries(changes)) {
    if (key === 'view') continue;
    if (value === null || value === '') params.delete(key); else params.set(key, value);
  }
  params.sort();
  return '#/' + (view === 'home' ? '' : encodeURIComponent(view)) + (params.size ? '?' + params.toString() : '');
}
function canonical(hash: string) {
  const { view, params } = read(hash); params.sort();
  return '#/' + (view === 'home' ? '' : view) + (params.size ? '?' + params.toString() : '');
}
export function navigate(changes: Changes, options: Options = {}) {
  const hash = target(changes, options.reset);
  if (canonical(window.location.hash) === hash) return;
  if (options.replace) window.history.replaceState(window.history.state, '', hash);
  else window.history.pushState({ previousAppHash: window.location.hash }, '', hash);
  window.dispatchEvent(new Event(eventName));
}
export function returnTo(changes: Changes) {
  const hash = target(changes);
  const previous: unknown = window.history.state?.previousAppHash;
  if (typeof previous === 'string' && canonical(previous) === hash) window.history.back();
  else navigate(changes);
}
function subscribe(listener: () => void) {
  window.addEventListener('popstate', listener);
  window.addEventListener('hashchange', listener);
  window.addEventListener(eventName, listener);
  return () => {
    window.removeEventListener('popstate', listener);
    window.removeEventListener('hashchange', listener);
    window.removeEventListener(eventName, listener);
  };
}
export function useRouteParam(key: string, fallback = '') {
  const hash = useSyncExternalStore(subscribe, () => window.location.hash);
  const route = read(hash);
  const value = key === 'view' ? route.view : route.params.get(key) ?? fallback;
  const set = (next: string) => { if (next !== value) navigate({ [key]: next === fallback ? null : next }); };
  return [value, set] as const;
}
