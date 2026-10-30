import { navigate, useRouteParam } from '../../navigation';
import type { User } from '../../types';
import ContentCatalog from './ContentCatalog';
import MaterialLibrary from './MaterialLibrary';
import AuthorLibrary from './AuthorLibrary';
import TeacherWriting from '../writing/TeacherWriting';
import './materials.css';
import './authoring.css';

export default function ContentWorkspace({ user }: { user: User }) {
  const [routeArea] = useRouteParam('area', 'catalog');
  const area = routeArea === 'assignments' && user.role === 'ADMIN' ? 'assignments' : routeArea === 'materials' || routeArea === 'questions' || routeArea === 'lessons' || routeArea === 'writing' ? routeArea : 'catalog';
  const setArea = (next: string) => { if (next !== area) navigate({ view: 'content', area: next === 'catalog' ? null : next }, { reset: true }); };
  return <><div className="content-areas" aria-label="Khu vực học liệu">
    <button aria-pressed={area === 'catalog'} className={area === 'catalog' ? 'active' : ''} onClick={() => setArea('catalog')}>{user.role === 'TEACHER' ? 'Vòng được giao' : 'Danh mục'}</button>
    {user.role === 'ADMIN' && <button aria-pressed={area === 'assignments'} className={area === 'assignments' ? 'active' : ''} onClick={() => setArea('assignments')}>Phân công</button>}
    <button aria-pressed={area === 'materials'} className={area === 'materials' ? 'active' : ''} onClick={() => setArea('materials')}>Tài liệu</button>
    <button aria-pressed={area === 'questions'} className={area === 'questions' ? 'active' : ''} onClick={() => setArea('questions')}>Câu hỏi</button>
    <button aria-pressed={area === 'lessons'} className={area === 'lessons' ? 'active' : ''} onClick={() => setArea('lessons')}>Bài học</button>
    <button aria-pressed={area === 'writing'} className={area === 'writing' ? 'active' : ''} onClick={() => setArea('writing')}>Bài viết cuối vòng</button>
  </div>{area === 'catalog' || area === 'assignments' ? <ContentCatalog key={area} user={user} assignmentMode={area === 'assignments'} /> : area === 'writing' ? <TeacherWriting user={user} /> : area === 'materials' ? <MaterialLibrary user={user} /> : <AuthorLibrary key={area} kind={area} user={user} />}</>;
}
