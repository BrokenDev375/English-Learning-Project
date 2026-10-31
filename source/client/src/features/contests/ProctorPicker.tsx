import { useEffect, useState } from 'react';
import { usePagedSearch } from './usePagedSearch';
import type { Proctor, TeacherPermission } from './types';

export default function ProctorPicker({value,initial,onChange}:{value:number[];initial:Proctor[];onChange:(ids:number[])=>void}) {
  const [query,setQuery]=useState(''),[page,setPage]=useState(0);
  const [known,setKnown]=useState<Record<number,Proctor>>(()=>Object.fromEntries(initial.map(p=>[p.userId,p])));
  const {data,error,loading,retry}=usePagedSearch<TeacherPermission>('/api/author/contest-permissions?proctorOnly=true',query,page,6);
  useEffect(()=>{if(data)setKnown(old=>({...old,...Object.fromEntries(data.items.map(p=>[p.userId,{userId:p.userId,fullName:p.fullName,email:p.email,eligible:true}]))}));},[data]);
  return <section className="contest-proctors" aria-labelledby="contest-proctors-title"><div className="contest-proctors-heading"><h3 id="contest-proctors-title">Giám thị cuộc thi</h3><small>Đã chọn {value.length}/20</small></div>
    {!!value.length&&<div className="contest-selected-proctors" role="list" aria-label="Giám thị đã chọn">{value.map(id=>{const p=known[id];return <span role="listitem" key={id} className={p?.eligible===false?'unavailable':''}>{p?.fullName??'Giáo viên #'+id}{p?.eligible===false&&<small> · không còn quyền giám sát</small>}<button type="button" aria-label={'Bỏ giám thị '+(p?.fullName??'#'+id)} onClick={()=>onChange(value.filter(x=>x!==id))}>×</button></span>;})}</div>}
    <label htmlFor="proctor-search">Tìm giám thị</label><input id="proctor-search" type="search" maxLength={160} placeholder="Họ tên hoặc email giáo viên" value={query} onChange={e=>{setQuery(e.target.value);setPage(0);}} onKeyDown={e=>{if(e.key==='Enter')e.preventDefault();}}/>
    <p className="muted contest-proctor-help">Tick giáo viên đã có quyền giám sát để phân công vào cuộc thi này.</p>
    <div className="contest-proctor-results" aria-busy={loading}>{loading?<p role="status">Đang tìm giáo viên…</p>:error?<div role="alert">{error}<button type="button" className="link-button" onClick={retry}>Thử lại</button></div>:data?.items.length?<div className="contest-teacher-list">{data.items.map(t=><label className="check-label" key={t.userId}><input type="checkbox" checked={value.includes(t.userId)} disabled={!value.includes(t.userId)&&value.length>=20} onChange={e=>onChange(e.target.checked?[...value,t.userId]:value.filter(id=>id!==t.userId))}/><span><strong>{t.fullName}</strong><small>{t.email}</small></span></label>)}</div>:<p className="muted">{query.trim()?'Không tìm thấy giám thị phù hợp.':'Chưa có giáo viên được cấp quyền giám sát.'}</p>}</div>
    <div className="contest-pagination"><small>{data?`${data.totalElements} giáo viên phù hợp`:''}</small><button type="button" className="button secondary compact" disabled={loading||page===0} onClick={()=>setPage(page-1)}>Trước</button><small>Trang {page+1}/{Math.max(1,data?.totalPages??0)}</small><button type="button" className="button secondary compact" disabled={loading||!data||page+1>=data.totalPages} onClick={()=>setPage(page+1)}>Sau</button></div>
  </section>;
}
