import { requestId } from '../../../api';

export const databaseName = 'english-learning-recovery';
const stores = ['workingDrafts', 'commands', 'leases'] as const;
type Store = typeof stores[number];
let opening: Promise<IDBDatabase> | null = null;
export interface Row { key: string; userId: number; version: 1 }
const blocked = new Set<number>();
const denied = (user: number) => { try { return blocked.has(user) || localStorage.getItem('learning-purged:' + user) === '1'; } catch { return blocked.has(user); } };
let activeUser: number | undefined;
let epoch = 0;
export const userEpoch = () => epoch;
export const isCurrent = (user: number, stamp: number) => user === activeUser && epoch === stamp && !denied(user);
export function activateStorage(user: number | undefined) { if (user !== activeUser) { activeUser = user; epoch++; } }
function database(): Promise<IDBDatabase> {
  if (opening) return opening;
  opening = new Promise((resolve, reject) => {
    const req = indexedDB.open(databaseName, 1);
    req.onupgradeneeded = () => { for (const name of stores) if (!req.result.objectStoreNames.contains(name)) req.result.createObjectStore(name, { keyPath: 'key' }); };
    req.onerror = () => { opening = null; reject(req.error); };
    req.onblocked = () => { opening = null; reject(new Error('Kho tại máy đang được tab khác nâng cấp. Đóng tab cũ rồi thử lại.')); };
    req.onsuccess = () => { const db = req.result; db.onversionchange = () => { db.close(); opening = null; }; resolve(db); };
  });
  return opening;
}
export async function readRows<T extends Row>(store: Store, user: number): Promise<T[]> {
  if (denied(user)) return [];
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, 'readonly'); const req = tx.objectStore(store).getAll();
    tx.oncomplete = () => resolve((req.result as T[]).filter(r => r.userId === user)); tx.onerror = tx.onabort = () => reject(tx.error ?? new Error('Không đọc được kho tại máy.'));
  });
}
export async function writeRows(user: number, stamp: number, records: Partial<Record<Store, Row[]>>, deletes: Partial<Record<Store, string[]>> = {}) {
  const db = await database();
  if (!isCurrent(user, stamp)) throw new Error('Phiên lưu tại máy đã thay đổi.');
  const names = [...new Set([...Object.keys(records), ...Object.keys(deletes)])] as Store[];
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction(names, 'readwrite');
    for (const name of names) {
      for (const row of records[name] ?? []) { if (row.userId !== user || row.version !== 1) { tx.abort(); return; } tx.objectStore(name).put(row); }
      for (const key of deletes[name] ?? []) tx.objectStore(name).delete(key);
    }
    tx.oncomplete = () => resolve(); tx.onerror = tx.onabort = () => reject(tx.error ?? new Error('Chưa lưu được tại máy.'));
  });
}
export async function purgeStorage(user: number) {
  // Synchronous tombstone fences other tabs and late callbacks even if IndexedDB deletion fails.
  blocked.add(user); try { localStorage.setItem('learning-purged:' + user, '1'); } catch { /* RAM fence still applies. */ } epoch++; activeUser = undefined;
  const db = await database();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([...stores], 'readwrite');
    for (const store of stores) {
      const cursor = tx.objectStore(store).openCursor(); cursor.onsuccess = () => { const c = cursor.result; if (c) { if (c.value.userId === user) c.delete(); c.continue(); } };
    }
    tx.oncomplete = () => resolve(); tx.onerror = tx.onabort = () => reject(tx.error ?? new Error('Không xóa được kho tại máy.'));
  });
  blocked.delete(user); try { localStorage.removeItem('learning-purged:' + user); } catch { /* No payload is kept in localStorage. */ }
}
export async function unlockStorage(user: number) { if (denied(user)) await purgeStorage(user); activateStorage(user); }

export async function pruneCleanDrafts(user: number) {
  const stamp = userEpoch(), db = await database();
  if (!isCurrent(user, stamp)) return;
  type CachedDraft = Row & { updatedAt?: number; state?: { pending?: unknown; conflict?: unknown; draft?: unknown; snapshot?: { items: { attemptQuestionId: number; response: unknown }[]; playerState: unknown } } };
  await new Promise<void>((resolve, reject) => {
    // Keep the read, eligibility check and delete in one transaction. Another tab
    // must not turn a clean candidate into an unsynced draft between these steps.
    const tx = db.transaction('workingDrafts', 'readwrite'), store = tx.objectStore('workingDrafts');
    const get = store.getAll();
    get.onsuccess = () => {
      if (!isCurrent(user, stamp)) { tx.abort(); return; }
      const clean = (get.result as CachedDraft[]).filter(r => {
        const s = r.state;
        return r.userId === user && r.version === 1 && typeof r.updatedAt === 'number' && !s?.pending && !s?.conflict && Array.isArray(s?.snapshot?.items)
          && JSON.stringify(s.draft) === JSON.stringify({ answers: s.snapshot.items.map(i => ({ attemptQuestionId: i.attemptQuestionId, response: i.response })), playerState: s.snapshot.playerState });
      }).sort((a, b) => b.updatedAt! - a.updatedAt!);
      for (const [n, row] of clean.entries()) {
        if (n >= 100 || Date.now() - row.updatedAt! > 30 * 86400000) store.delete(row.key);
      }
    };
    tx.oncomplete = () => resolve();
    tx.onerror = tx.onabort = () => reject(tx.error ?? new Error('Không dọn được bản đã đồng bộ tại máy.'));
  });
}

let writer = '';
const holder = requestId();
let writerReady: Promise<void> | undefined;
/** Browser-owned lock releases on document destruction; duplicated tabs receive distinct writers. */
export function prepareWriter(): Promise<void> {
  if (writerReady) return writerReady;
  writerReady = new Promise<void>((resolve, reject) => {
    const claim = () => {
      if (!navigator.locks) {
        // Without a browser-owned lock, never trust a cloned tab's sessionStorage ID.
        // A reload offers the previous working copy for explicit recovery instead.
        writer = requestId(); sessionStorage.setItem('learning-writer', writer); resolve(); return;
      }
      void navigator.locks.request('learning-writer:' + writerId(), { ifAvailable: true }, async lock => {
        if (!lock) { writer = requestId(); sessionStorage.setItem('learning-writer', writer); claim(); return; }
        resolve(); await new Promise<void>(done => window.addEventListener('pagehide', () => done(), { once: true }));
      }).catch(reject);
    };
    claim();
  });
  return writerReady;
}
export function writerId() {
  if (!writer) { writer = sessionStorage.getItem('learning-writer') ?? requestId(); sessionStorage.setItem('learning-writer', writer); }
  return writer;
}
/** Atomic same-origin sender lease. Server revision/receipt still arbitrate delayed senders. */
const localLanes = new Map<string, Promise<void>>();
export async function lease(user: number, lane: string, run: () => Promise<void>) {
  const key = `${user}:${lane}`;
  const pending = (localLanes.get(key) ?? Promise.resolve()).catch(() => {}).then(() => claimedLease(user, lane, run));
  localLanes.set(key, pending);
  try { await pending; } finally { if (localLanes.get(key) === pending) localLanes.delete(key); }
}
async function claimedLease(user: number, lane: string, run: () => Promise<void>) {
  if (navigator.locks) return navigator.locks.request(`learning-lane:${user}:${lane}`, () => leaseBody(user, lane, run, true));
  return leaseBody(user, lane, run, false);
}
async function leaseBody(user: number, lane: string, run: () => Promise<void>, exclusive: boolean) {
  const db = await database(); const key = `${user}:${lane}`; const stamp = userEpoch();
  const claim = (release = false) => new Promise<boolean>((resolve, reject) => {
    if (!isCurrent(user, stamp)) { resolve(false); return; }
    const tx = db.transaction('leases', 'readwrite'); const store = tx.objectStore('leases'); const get = store.get(key); let won = false;
    get.onsuccess = () => {
      const value = get.result as { holder: string; until: number; fence: number } | undefined;
      if (release) { if (value?.holder === holder) store.delete(key); }
      else if (exclusive || !value || value.holder === holder || value.until < Date.now()) {
        won = true; store.put({ key, version: 1, userId: user, holder, until: Date.now() + 25000, fence: (value?.fence ?? 0) + 1 });
      }
    };
    tx.oncomplete = () => resolve(won); tx.onerror = tx.onabort = () => reject(tx.error);
  });
  if (!await claim()) throw new Error('Tab khác đang đồng bộ lượt này. Bản nhập của bạn vẫn được giữ.');
  const renew = window.setInterval(() => { void claim().catch(() => {}); }, 5000);
  try { if (!isCurrent(user, stamp)) return; await run(); }
  finally { window.clearInterval(renew); await claim(true); }
}
