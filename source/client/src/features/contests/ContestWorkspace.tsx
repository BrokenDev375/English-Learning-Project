import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import type { User } from '../../types';
import { navigate, useRouteParam } from '../../navigation';
import ContestPlayer from './ContestPlayer';
import ContestEditor from './ContestEditor';
import ContestMonitor from './ContestMonitor';
import ContestPermissions from './ContestPermissions';
import PaperLibrary from './PaperLibrary';
import { date, stateNames, statusNames } from './types';
import type { Author, Capabilities, Contest, Page } from './types';
import './contest.css';
import './contest-search.css';

export default function ContestWorkspace({user}:{user:User}) {
  const [selected]=useRouteParam('contest'),[tab,setTab]=useRouteParam('section','monitor'),[area]=useRouteParam('area','contests');
  const id=Number(selected),creating=selected==='new',valid=Number.isSafeInteger(id)&&id>0,student=user.role==='STUDENT';
  const [page,setPage]=useState(0),[list,setList]=useState<Page<Contest>|null>(null),[author,setAuthor]=useState<Author|null>(null),[caps,setCaps]=useState<Capabilities|null>(null),[error,setError]=useState(''),[refresh,setRefresh]=useState(0);
  useEffect(()=>{if(student)return;let alive=true;const load=()=>{void api<Capabilities>('/api/author/contest-permissions/mine').then(c=>{if(alive){setCaps(c);setRefresh(n=>n+1);}}).catch(e=>{if(alive)setError(errorMessage(e));});};load();window.addEventListener('contest-permissions:changed',load);return()=>{alive=false;window.removeEventListener('contest-permissions:changed',load);};},[student]);
  useEffect(()=>{if(!student&&!caps||area!=='contests')return;let alive=true;setError('');if(valid&&!student){setAuthor(null);void api<Author>(`/api/author/contests/${id}`).then(v=>{if(alive)setAuthor(v);}).catch(e=>{if(alive)setError(errorMessage(e));});}else if(!valid&&!creating)void api<Page<Contest>>(`/api/contests?page=${page}&size=12`).then(v=>{if(alive)setList(v);}).catch(e=>{if(alive)setError(errorMessage(e));});return()=>{alive=false;};},[id,selected,student,area,caps,page,refresh]);
  const open=(contest:string|null)=>navigate({view:'contests',contest},{reset:true});
  const changeArea=(next:string)=>navigate({view:'contests',area:next==='contests'?null:next},{reset:true});
  function saved(v:Author){setAuthor(v);if(creating)navigate({view:'contests',contest:String(v.id),section:'settings'},{reset:true});else setRefresh(n=>n+1);}
  if(!student&&!caps)return <section><p>{error||'Đang mở không gian cuộc thi…'}</p></section>;
  return <section className="contest-stack contest-compact">
    {!student&&<div className="contest-top-tabs" role="navigation" aria-label="Quản lý cuộc thi"><button className={area==='contests'?'active':''} onClick={()=>changeArea('contests')}>Cuộc thi</button>{caps?.canAuthor&&<button className={area==='papers'?'active':''} onClick={()=>changeArea('papers')}>Kho bộ đề</button>}{caps?.canManage&&<button className={area==='permissions'?'active':''} onClick={()=>changeArea('permissions')}>Phân quyền</button>}</div>}
    {error&&<div className="message error" role="alert">{error}</div>}
    {area==='papers'&&!student?caps?.canAuthor?<PaperLibrary/>:<p>Bạn chưa được cấp quyền ra đề cuộc thi.</p>:area==='permissions'&&!student?caps?.canManage?<ContestPermissions/>:<p>Chỉ admin được quản lý phân quyền.</p>:<>
      {(valid||creating)&&<button className="link-button contest-back" onClick={()=>open(null)}>← Danh sách cuộc thi</button>}
      {creating&&caps?.canManage?<ContestEditor creating onSaved={saved}/>:valid?student?<ContestPlayer key={id} user={user} id={id}/>:author?<><div className="contest-detail-heading"><div><span className="eyebrow">{stateNames[author.state]}</span><h1>{author.title}</h1></div>{author.canManage&&<div className="contest-top-tabs"><button className={tab==='monitor'?'active':''} onClick={()=>setTab('monitor')}>Thí sinh và giám sát</button><button className={tab==='settings'?'active':''} onClick={()=>setTab('settings')}>Thiết lập</button></div>}</div>{tab==='settings'&&author.canManage?<ContestEditor key={author.id+':'+author.revision} value={author} onSaved={saved}/>:<ContestMonitor contest={author} onChanged={()=>{void api<Author>(`/api/author/contests/${id}`).then(setAuthor).catch(e=>setError(errorMessage(e)));}}/>}</>:!error&&<p>Đang mở cuộc thi…</p>:<>
        <div className="contest-heading"><div><span className="eyebrow">CUỘC THI TIẾNG ANH</span><h1>Cuộc thi</h1><p className="muted">{student?'Đăng ký và vào phòng trước giờ thi.':caps?.canManage?'Chọn bộ đề đã duyệt, đặt lịch và phân công giám thị.':'Các cuộc thi bạn được phân công giám sát.'}</p></div>{caps?.canManage&&<button className="button primary compact" onClick={()=>open('new')}>+ Tạo cuộc thi</button>}</div>
        <div className="panel contest-list">{list?.items.map(c=><article className="contest-list-row" key={c.id}><div><h3>{c.title}</h3><small>{c.questionCount} câu{c.participationStatus?' · '+statusNames[c.participationStatus]:''}</small></div><div className="contest-list-time"><small>Bắt đầu</small><span>{date(c.startsAt)}</span></div><span className={'contest-status '+c.state.toLowerCase()}>{stateNames[c.state]}</span><button className="button secondary compact" onClick={()=>open(String(c.id))}>{student?'Xem cuộc thi':caps?.canManage?'Quản lý':'Giám sát'}</button></article>)}{list&&!list.items.length&&<div className="contest-empty"><h3>Chưa có cuộc thi</h3><p className="muted">{student?'Cuộc thi mới sẽ xuất hiện sau khi công bố.':caps?.canManage?'Tạo cuộc thi từ một bộ đề đã duyệt trong kho.':'Admin sẽ phân công bạn vào từng cuộc thi sau khi cấp quyền giám sát.'}</p></div>}{!list&&!error&&<p>Đang tải danh sách…</p>}</div>
        {list&&<div className="contest-pagination"><button className="button secondary compact" disabled={!page} onClick={()=>setPage(page-1)}>Trước</button><small>Trang {page+1}/{Math.max(1,list.totalPages)}</small><button className="button secondary compact" disabled={page+1>=list.totalPages} onClick={()=>setPage(page+1)}>Sau</button></div>}
      </>}
    </>}
  </section>;
}
