export type MaterialKind = 'READING' | 'AUDIO' | 'IMAGE';
export interface Source { label: string; url: string | null; license: string | null }
export interface Asset { id: number; ownerId: number; name: string; mimeType: string; sizeBytes: number; sha256: string; url: string }
interface Stamp { id: number; materialId: number; versionNo: number; revision: number; state: 'DRAFT' | 'PUBLISHED'; schemaVersion: 1 }
interface Base { title: string; source: Source }
export type MaterialContent = Base & (
  { kind: 'READING'; readingText: string; assetId: number | null; altText: string | null } |
  { kind: 'AUDIO'; assetId: number; transcript: string; startSeconds: number; endSeconds: number | null } |
  { kind: 'IMAGE'; assetId: number; altText: string; caption: string }
);
export type MaterialVersion = MaterialContent & Stamp;
export interface MaterialRoot { id: number; ownerId: number; rootRevision: number; status: string; draftVersionId: number | null; publishedVersionId: number | null; draft: MaterialVersion | null; published: MaterialVersion | null }
export interface MaterialSummary { id: number; ownerId: number; rootRevision: number; status: string; draftVersionId: number | null; publishedVersionId: number | null; title: string; kind: MaterialKind }
export interface MaterialPreview { resourceId: number; draftVersionId: number; baseRevision: number; previewToken: string; errors: { path: string; code: string; message: string }[]; warnings: string[]; data: MaterialVersion }
export const materialKinds: Record<MaterialKind, string> = { READING: 'Đoạn đọc', AUDIO: 'Audio', IMAGE: 'Ảnh' };
export const materialStatuses: Record<string, string> = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã xuất bản', RETIRED: 'Ngừng cấp mới' };
