export interface CatalogBase { id: number; ownerId: number; description: string; revision: number }
export interface Topic extends CatalogBase { code: string; name: string }
export interface Objective extends Topic { topicId: number }
export interface Unit extends CatalogBase { topicId: number; title: string; stage: number; sortOrder: number; objectiveIds: number[] }
export type CatalogItem = Topic | Objective | Unit;
export type CatalogKind = 'topics' | 'objectives' | 'units';
export interface CatalogPage<T> { items: T[]; totalElements: number; page: number; size: number; totalPages: number }
export const catalogNames: Record<CatalogKind, string> = { topics: 'Chủ đề', objectives: 'Mục tiêu học', units: 'Unit' };
export function itemName(item: CatalogItem) { return 'title' in item ? item.title : item.name; }
