import { useEffect, useRef, useState } from 'react';
import type { Paper } from './types';
import { usePagedSearch } from './usePagedSearch';

export interface PaperSelection {id:number;title:string;questionCount:number;ownerName?:string}
function PaperDialog({selected,onSelect,onDismiss}:{selected:number|null;onSelect:(p:PaperSelection)=>void;onDismiss:()=>void}) {
  const ref=useRef<HTMLDialogElement>(null);
  const [query,setQuery]=useState(''),[page,setPage]=useState(0);
  const {data,error,loading,retry}=usePagedSearch<Paper>('/api/author/contest-papers?state=READY',query,page,8);
  useEffect(()=>{const dialog=ref.current!;dialog.showModal();return()=>{dialog.close();};},[]);
  return <dialog ref={ref} className="contest-selection-dialog" aria-labelledby="paper-dialog-title" onCancel={e=>{e.preventDefault();onDismiss();}} onKeyDown={e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();onDismiss();}else if(e.key==='Enter'&&(e.target as HTMLElement).tagName==='INPUT')e.preventDefault();}}>
    <header><div><h2 id="paper-dialog-title">Chọn bộ đề dự thi</h2><p>Chọn một bộ đề đã được duyệt.</p></div><button type="button" className="contest-dialog-close" aria-label="Đóng chọn bộ đề" onClick={onDismiss}>×</button></header>
    <div className="contest-dialog-search"><label htmlFor="paper-search">Tìm bộ đề</label><input id="paper-search" type="search" autoFocus maxLength={160} placeholder="Tên bộ đề hoặc người soạn" value={query} onChange={e=>{setQuery(e.target.value);setPage(0);}}/><small>{loading?'Đang tìm…':data?`${data.totalElements} bộ đề phù hợp`:''}</small></div>
    <div className="table-scroll contest-picker-results" aria-busy={loading}><table><thead><tr><th>Bộ đề / Người soạn</th><th>Số câu</th><th/></tr></thead><tbody>
      {loading?<tr><td colSpan={3} role="status">Đang tìm bộ đề…</td></tr>:error?<tr><td colSpan={3}><p role="alert">{error}</p><button type="button" className="button secondary compact" onClick={retry}>Thử lại</button></td></tr>:data?.items.length?data.items.map(p=><tr key={p.id}><td><strong>{p.title}</strong><small>{p.ownerName} · Đã dùng trong {p.usageCount} cuộc thi</small></td><td>{p.questionCount} câu</td><td><button type="button" className={'button compact '+(selected===p.id?'secondary':'primary')} disabled={selected===p.id} onClick={()=>onSelect(p)}>{selected===p.id?'Đã chọn':'Chọn bộ đề'}</button></td></tr>):<tr><td colSpan={3}>Không tìm thấy bộ đề phù hợp.</td></tr>}
    </tbody></table></div>
    <footer className="contest-pagination"><button type="button" className="button secondary compact" disabled={loading||page===0} onClick={()=>setPage(page-1)}>Trước</button><small>Trang {page+1}/{Math.max(1,data?.totalPages??0)}</small><button type="button" className="button secondary compact" disabled={loading||!data||page+1>=data.totalPages} onClick={()=>setPage(page+1)}>Sau</button></footer>
  </dialog>;
}
export default function PaperPicker({value,onChange}:{value:PaperSelection|null;onChange:(p:PaperSelection|null)=>void}) {
  const [open,setOpen]=useState(false);
  const trigger=useRef<HTMLButtonElement>(null);
  function dismiss(){setOpen(false);window.requestAnimationFrame(()=>trigger.current?.focus());}
  return <div className="contest-paper-picker"><label htmlFor="contest-paper-trigger">Bộ đề dự thi</label><button ref={trigger} id="contest-paper-trigger" type="button" className="contest-picker-trigger" aria-haspopup="dialog" aria-label="Bộ đề dự thi" onClick={()=>setOpen(true)}><span>{value?.title??'Chọn bộ đề đã duyệt'}</span><span aria-hidden="true">⌕</span></button>
    {value&&<div className="contest-picked-paper"><small>{value.questionCount} câu{value.ownerName?' · '+value.ownerName:''}</small><button type="button" className="link-button" onClick={()=>onChange(null)}>Bỏ chọn bộ đề</button></div>}
    {open&&<PaperDialog selected={value?.id??null} onDismiss={dismiss} onSelect={p=>{onChange(p);dismiss();}}/>}
  </div>;
}
