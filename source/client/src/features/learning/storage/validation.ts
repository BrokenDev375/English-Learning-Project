// Storage is fallible. These structural guards protect hydration/rendering;
// server validators remain authoritative for membership and grading rules.
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const strings = (v: unknown) => Array.isArray(v) && v.every(x => typeof x === 'string');
function answer(v: unknown): boolean {
  if (v === null) return true;
  if (!object(v)) return false;
  if (v.type === 'SINGLE_CHOICE') return v.optionId === null || typeof v.optionId === 'string';
  if (v.type === 'FILL_BLANK') return Array.isArray(v.values) && v.values.every(x => object(x) && typeof x.blankId === 'string' && typeof x.value === 'string');
  return v.type === 'MATCHING' && Array.isArray(v.pairs) && v.pairs.every(x => object(x) && typeof x.leftId === 'string' && typeof x.rightId === 'string');
}
function player(v: unknown): boolean {
  return object(v) && (v.currentAttemptQuestionId === null || Number.isSafeInteger(v.currentAttemptQuestionId)) && Array.isArray(v.audioPositions)
    && v.audioPositions.every(x => object(x) && Number.isSafeInteger(x.materialVersionId) && typeof x.seconds === 'number' && Number.isFinite(x.seconds));
}
function question(v: unknown): boolean {
  if (!object(v) || typeof v.prompt !== 'string' || !object(v.content)) return false;
  const c = v.content;
  const options = (value: unknown) => Array.isArray(value) && value.length > 0 && value.every(x => object(x) && typeof x.id === 'string' && typeof x.text === 'string');
  if (v.type === 'SINGLE_CHOICE') return options(c.options);
  if (v.type === 'MATCHING') return options(c.leftItems) && options(c.rightItems);
  return v.type === 'FILL_BLANK' && Array.isArray(c.parts) && c.parts.every(p => object(p) && (p.kind === 'TEXT' ? typeof p.text === 'string' : p.kind === 'BLANK' && typeof p.blankId === 'string'));
}
export function validRecoveryState(v: unknown): boolean {
  if (!object(v) || !object(v.snapshot) || !object(v.draft)) return false;
  const s = v.snapshot, d = v.draft;
  if (!Number.isSafeInteger(s.revision) || !['IN_PROGRESS', 'SUBMITTED'].includes(String(s.state)) || !Array.isArray(s.items) || s.items.length === 0 || !player(s.playerState) || !player(d.playerState)) return false;
  if (!s.items.every(i => object(i) && Number.isSafeInteger(i.attemptQuestionId) && question(i.question) && answer(i.response))) return false;
  if (!Array.isArray(d.answers) || d.answers.length !== s.items.length || !d.answers.every(a => object(a) && Number.isSafeInteger(a.attemptQuestionId) && answer(a.response))) return false;
  const ids = s.items.map(i => i.attemptQuestionId);
  if (new Set(d.answers.map(a => a.attemptQuestionId)).size !== ids.length || d.answers.some(a => !ids.includes(a.attemptQuestionId))) return false;
  if (!Array.isArray(s.materials) || !s.materials.every(m => object(m) && object(m.source) && typeof m.source.label === 'string' && (m.asset === null || object(m.asset) && typeof m.asset.url === 'string'))) return false;
  if (!s.materials.every(m => typeof m.title === 'string' && (m.readingText === null || typeof m.readingText === 'string') && (m.caption === null || typeof m.caption === 'string') && (m.source.license === null || typeof m.source.license === 'string'))) return false;
  if (s.lesson !== null && (!object(s.lesson) || !object(s.lesson.lesson) || typeof s.lesson.lesson.title !== 'string' || !Array.isArray(s.lesson.instructions) || !s.lesson.instructions.every(b => object(b) && typeof b.text === 'string') || !Array.isArray(s.lesson.objectives) || !s.lesson.objectives.every(o => object(o) && typeof o.name === 'string'))) return false;
  if (v.conflict !== null && (!object(v.conflict) || !Number.isSafeInteger(v.conflict.revision) || !Array.isArray(v.conflict.items) || !v.conflict.items.every(i => object(i) && question(i.question) && answer(i.response)))) return false;
  if (!object(v.hints) || !Object.values(v.hints).every(x => typeof x === 'string') || !object(v.feedback)) return false;
  return Object.values(v.feedback).every(f => {
    if (!object(f) || !object(f.data) || typeof f.data.explanation !== 'string' || !object(f.data.answerKey) || !answer(f.response)) return false;
    const key = f.data.answerKey;
    return (!('blanks' in key) || Array.isArray(key.blanks) && key.blanks.every(b => object(b) && strings(b.acceptedAnswers)))
      && (!('pairs' in key) || Array.isArray(key.pairs) && key.pairs.every(p => object(p) && typeof p.leftId === 'string' && typeof p.rightId === 'string'));
  });
}
