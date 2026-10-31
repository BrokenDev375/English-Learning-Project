export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fields: Record<string, string> = {}) {
    super(message);
  }
}
interface Csrf { token: string; headerName: string }
let csrf: Csrf | null = null;
let csrfPending: Promise<Csrf> | null = null;
let csrfEpoch = 0;
let authEpoch = 0;
export const authenticationEpoch = () => authEpoch;
export function advanceAuthentication() { authEpoch++; resetCsrf(); }
export function resetCsrf() { csrfEpoch++; csrf = null; csrfPending = null; }
async function fetchTimed(path: string, init: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 15000);
  try {
    return await fetch(path, { ...init, credentials: 'same-origin', signal: controller.signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError(0, 'TIMEOUT', 'Chưa nhận được xác nhận từ máy chủ. Nội dung biểu mẫu vẫn được giữ; bạn có thể thử lại.');
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Chưa kết nối được máy chủ. Kiểm tra kết nối rồi thử lại.');
  } finally { window.clearTimeout(timer); }
}
async function csrfToken(): Promise<Csrf> {
  if (csrf) return csrf;
  if (!csrfPending) {
    const generation = csrfEpoch;
    const pending = fetchTimed('/api/auth/csrf', { method: 'GET' }).then(async (response) => {
      if (!response.ok) throw new ApiError(response.status, 'CSRF_UNAVAILABLE', 'Chưa chuẩn bị được phiên đăng nhập. Vui lòng thử lại.');
      const next = await response.json() as Csrf;
      if (generation !== csrfEpoch) throw new ApiError(0, 'SESSION_CHANGED', 'Phiên đăng nhập đã thay đổi.');
      csrf = next;
      return csrf;
    }).finally(() => { if (csrfPending === pending) csrfPending = null; });
    csrfPending = pending;
  }
  return csrfPending;
}
export async function api<T>(path: string, method = 'GET', body?: unknown, allowUnauthorized = false, retried = false): Promise<T> {
  const epoch = authEpoch;
  const headers: Record<string, string> = { Accept: 'application/json' };
  const multipart = body instanceof FormData;
  if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
  if (!['GET', 'HEAD'].includes(method)) {
    const token = await csrfToken();
    headers[token.headerName] = token.token;
  }
  if (epoch !== authEpoch) throw new ApiError(0, 'SESSION_CHANGED', 'Phiên đăng nhập đã thay đổi.');
  const response = await fetchTimed(path, { method, headers, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body) });
  if (epoch !== authEpoch) throw new ApiError(0, 'SESSION_CHANGED', 'Phiên đăng nhập đã thay đổi.');
  if (!response.ok) {
    const failure = await response.json().catch(() => ({})) as { code?: string; message?: string; fieldErrors?: Record<string, string> };
    if (epoch !== authEpoch) throw new ApiError(0, 'SESSION_CHANGED', 'Phiên đăng nhập đã thay đổi.');
    if (response.status === 403 && failure.code === 'CSRF_INVALID' && !retried) {
      resetCsrf();
      return api<T>(path, method, body, allowUnauthorized, true);
    }
    if (response.status === 401 && !allowUnauthorized) window.dispatchEvent(new CustomEvent('session-expired', { detail: { code: failure.code, epoch } }));
    throw new ApiError(response.status, failure.code ?? 'REQUEST_FAILED', failure.message ?? 'Yêu cầu chưa thành công. Vui lòng thử lại.', failure.fieldErrors);
  }
  if (response.status === 204) return undefined as T;
  const result = await response.json() as T;
  if (epoch !== authEpoch) throw new ApiError(0, 'SESSION_CHANGED', 'Phiên đăng nhập đã thay đổi.');
  return result;
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : 'Có lỗi xảy ra. Vui lòng thử lại.'; }
export function requestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map(v => v.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}
