import { requestId } from '../../api';

let current: string | undefined;
export const activityTabId = () => current ??= requestId();
export function renewActivityTab() {
  current = requestId();
  window.dispatchEvent(new Event('session-tab-changed'));
  return current;
}
