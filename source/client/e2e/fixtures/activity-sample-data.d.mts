import type { QuestionData } from '../../src/features/content/authoring';
export const sampleVersion: string;
export const source: QuestionData['source'];
export const sampleQuestions: (Pick<QuestionData, 'type' | 'prompt' | 'content' | 'answerKey' | 'explanation'> & { key: string; materialKey: string | null })[];
export interface Sample { unitId: number; lessonId: number; lessonVersionId: number; questionVersions: Record<string, number>; materialVersions: Record<string, number>; version: string }
export function provisionActivitySample(client: {
  get(path: string): Promise<any>;
  action(label: string, path: string, body: object): Promise<any>;
  publish(kind: string, id: number, label: string): Promise<any>;
}, namespace: string): Promise<Sample>;
