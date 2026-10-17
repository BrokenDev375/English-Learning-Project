export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'DELETED';
export interface User {
  id: number; email: string; fullName: string; role: Role;
  status: AccountStatus; createdAt: string;
}
export interface UserPage {
  items: User[]; totalElements: number; page: number; size: number; totalPages: number;
}
export const roleNames: Record<Role, string> = {
  STUDENT: 'Người học', TEACHER: 'Người biên soạn', ADMIN: 'Quản trị viên',
};
export const statusNames: Record<AccountStatus, string> = {
  ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa', DELETED: 'Đã xóa',
};
export interface Classroom {
  id: number; teacherId: number; teacherName: string; name: string; description: string;
  joinCode: string | null; memberCount: number; createdAt: string; joinedAt: string | null;
}
export interface Member { id: number; fullName: string; email: string; joinedAt: string }
export interface JoinResult { classroom: Classroom; alreadyJoined: boolean }
