import { expect, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import type { Snapshot } from '../src/features/learning/types';
import type { RoundProgress } from '../src/features/learning/journey';

export async function write(request: APIRequestContext, path: string, data: unknown) {
  const csrf = await (await request.get('/api/auth/csrf')).json();
  const response = await request.post(path, { headers: { [csrf.headerName]: csrf.token }, data });
  expect(response.ok(), await response.text()).toBeTruthy(); return response.json();
}
export function completeAnswers(s: Snapshot) {
  return s.items.map(item => {
    const q = item.question, c = q.content;
    const response = q.type === 'SINGLE_CHOICE' ? { type: q.type, optionId: c.options![0].id }
      : q.type === 'FILL_BLANK' ? { type: q.type, values: c.parts!.filter(p => p.kind === 'BLANK').map(p => ({ blankId: p.blankId, value: 'fixture' })) }
      : q.type === 'MATCHING' ? { type: q.type, pairs: c.leftItems!.map((p, i) => ({ leftId: p.id, rightId: c.rightItems![i].id })) }
      : { type: q.type, tokenIds: c.tokens!.map(t => t.id) };
    return { attemptQuestionId: item.attemptQuestionId, response };
  });
}
// Real submissions through HTTP, only on the isolated E2E database. No lock bypass or SQL progress writes.
export async function completeEarlierRounds(request: APIRequestContext, targetUnitId: number) {
  const rounds: RoundProgress[] = [];
  for (let page = 0; ; page++) {
    const result = await (await request.get('/api/me/rounds?size=50&page=' + page)).json(); rounds.push(...result.items);
    if (page + 1 >= result.totalPages) break;
  }
  for (const round of rounds) {
    if (round.unit.id === targetUnitId) break;
    if (round.state === 'COMPLETED') continue;
    const detail = await (await request.get('/api/units/' + round.unit.id)).json();
    for (const lesson of detail.lessons) {
      const s: Snapshot = await write(request, '/api/attempts', { requestId: randomUUID(), mode: 'LEARN', lessonId: lesson.id });
      const result = await write(request, '/api/attempts/' + s.attemptId + '/submit', { requestId: randomUUID(), baseRevision: s.revision, answers: completeAnswers(s), playerState: s.playerState });
      expect(result.lessonCompleted).toBe(true);
    }
  }
  const target = await (await request.get('/api/me/rounds/' + targetUnitId)).json(); expect(target.locked).toBe(false);
}
