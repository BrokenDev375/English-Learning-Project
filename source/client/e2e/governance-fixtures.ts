import { request as contexts } from '@playwright/test';
import type { APIRequestContext, APIResponse } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Curriculum setup and trusted publication for regressions outside the review feature.
// Tests of actual teacher/admin permissions call their own request contexts directly.
let manager: Promise<APIRequestContext> | undefined;
function admin() {
  return manager ??= (async () => {
    const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5174';
    if (new URL(baseURL).port === '15183') throw new Error('Author fixtures require an isolated test environment.');
    const path = resolve(process.cwd(), '../../.env');
    const env = Object.fromEntries((existsSync(path) ? readFileSync(path, 'utf8') : '').split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
    const api = await contexts.newContext({ baseURL });
    const csrf = await (await api.get('/api/auth/csrf')).json();
    const login = await api.post('/api/auth/login', { headers: { [csrf.headerName]: csrf.token }, data: { email: process.env.E2E_ADMIN_EMAIL ?? env.BOOTSTRAP_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD ?? env.BOOTSTRAP_ADMIN_PASSWORD } });
    if (!login.ok()) throw new Error('Cannot authenticate the administrator fixture.');
    return api;
  })();
}
export async function fixtureWrite(api: APIRequestContext, path: string, data: unknown, method = 'POST'): Promise<APIResponse> {
  let target = api, payload = data;
  const curriculum = /^\/api\/author\/(topics|objectives|units)(\/\d+)?$/.test(path);
  const publication = /^\/api\/author\/(questions|lessons)\/\d+\/(publish|retire)$/.test(path);
  if (curriculum || publication) {
    const self = await (await api.get('/api/auth/me')).json();
    target = await admin();
    if (path === '/api/author/units' && method === 'POST' && self.role === 'TEACHER') payload = { ...data as object, assignedTeacherId: self.id };
  }
  const csrf = await (await target.get('/api/auth/csrf')).json();
  return target.fetch(path, { method, data: payload, headers: { [csrf.headerName]: csrf.token } });
}
