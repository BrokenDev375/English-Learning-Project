import { useEffect, useState } from 'react';
import { api, ApiError, errorMessage, requestId } from '../../api';
import PaperPicker from './PaperPicker';
import type { PaperSelection } from './PaperPicker';
import ProctorPicker from './ProctorPicker';
import { KeyExplanation } from '../learning/AnswerFields';
import type { AnswerKey } from '../learning/types';
import { date } from './types';
import type { Author, Settings } from './types';

const localDate=(s:string)=>{const d=new Date(s);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,19);};
function defaults():Settings {const start=Date.now()+3600000;return {title:'',description:'',registrationClosesAt:new Date(start-600000).toISOString(),startsAt:new Date(start).toISOString(),endsAt:new Date(start+1800000).toISOString(),penaltyMinutes:10,shuffleQuestions:true,proctorIds:[],paperId:null};}
function settings(a:Author):Settings {const {title,description,registrationClosesAt,startsAt,endsAt,penaltyMinutes,shuffleQuestions,proctorIds,paperId}=a;return {title,description,registrationClosesAt,startsAt,endsAt,penaltyMinutes,shuffleQuestions,proctorIds,paperId};}
export default function ContestEditor({value,creating=false,onSaved}:{value?:Author;creating?:boolean;onSaved:(v:Author)=>void}) {
  const [form,setForm]=useState<Settings>(()=>value?settings(value):defaults()),[saved,setSaved]=useState(()=>JSON.stringify(form));
  const [paper,setPaper]=useState<PaperSelection|null>(()=>value?.paperId?{id:value.paperId,title:value.paperTitle??'Bộ đề đã chọn',questionCount:value.questionCount}:null);
  const [error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [pending,setPending]=useState<{path:string;method:string;body:unknown}|null>(null);
  const editable=creating||value?.canEdit,dirty=JSON.stringify(form)!==saved;
  useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(dirty||pending){e.preventDefault();e.returnValue='';}};window.addEventListener('beforeunload',warn);return()=>window.removeEventListener('beforeunload',warn);},[dirty,pending]);
  const set=(patch:Partial<Settings>)=>setForm(f=>({...f,...patch}));
  async function send(action:'draft'|'publish') {
    setBusy(true);setError('');const command=pending??{path:creating?'/api/author/contests':`/api/author/contests/${value!.id}/${action}`,method:creating||action==='publish'?'POST':'PUT',body:{requestId:requestId(),...(creating?form:action==='publish'?{baseRevision:value!.revision}:{...form,baseRevision:value!.revision})}};setPending(command);
    try{const v=await api<Author>(command.path,command.method,command.body);setPending(null);setForm(settings(v));setSaved(JSON.stringify(settings(v)));onSaved(v);}catch(e){setError(errorMessage(e));if(e instanceof ApiError&&e.status>=400)setPending(null);}finally{setBusy(false);}
  }
  if(!editable)return <section className="panel"><h2>Thông tin cuộc thi</h2><div className="contest-facts"><span><small>Bộ đề</small><strong>{value?.paperTitle??'Đề thi đã chốt'}</strong></span><span><small>Bắt đầu</small>{date(form.startsAt)}</span><span><small>Kết thúc</small>{date(form.endsAt)}</span></div>{value?.questions.length?<details className="contest-paper-preview"><summary>Xem đề đã chốt · {value.questions.length} câu</summary>{value.questions.map((q,i)=><details key={q.id}><summary>Câu {i+1}: {q.prompt}</summary>{q.context&&<p>{q.context}</p>}<KeyExplanation question={q} answerKey={q.answerKey as AnswerKey} explanation={q.explanation}/></details>)}</details>:null}</section>;
  return <section className="panel contest-settings"><h2>{creating?'Tạo cuộc thi':'Thiết lập cuộc thi'}</h2>{error&&<div className="message error" role="alert">{error}</div>}<form onSubmit={e=>{e.preventDefault();void send('draft');}}><fieldset disabled={busy||!!pending}>
    <div className="contest-form-grid"><label>Tên cuộc thi<input required maxLength={160} value={form.title} onChange={e=>set({title:e.target.value})}/></label><PaperPicker value={paper} onChange={p=>{setPaper(p);set({paperId:p?.id??null});}}/><label className="contest-span-all">Mô tả<textarea rows={2} maxLength={4000} value={form.description} onChange={e=>set({description:e.target.value})}/></label></div>
    <div className="contest-date-grid">{(['registrationClosesAt','startsAt','endsAt'] as const).map((field,i)=><label key={field}>{['Hạn đăng ký','Giờ bắt đầu','Giờ kết thúc'][i]}<input required type="datetime-local" step="1" value={localDate(form[field])} onChange={e=>{if(e.target.value)set({[field]:new Date(e.target.value).toISOString()});}}/></label>)}</div>
    <div className="contest-options"><label>Phạt rời màn lần 2 (phút)<input type="number" min={1} max={120} value={form.penaltyMinutes} onChange={e=>set({penaltyMinutes:Number(e.target.value)})}/></label><label className="check-label"><input type="checkbox" checked={form.shuffleQuestions} onChange={e=>set({shuffleQuestions:e.target.checked})}/>Trộn thứ tự câu hỏi</label></div>
    <ProctorPicker value={form.proctorIds} initial={value?.proctors??[]} onChange={proctorIds=>set({proctorIds})}/>
    </fieldset><div className="contest-actions"><button className="button primary" disabled={busy||!!pending} type="submit">{creating?'Tạo cuộc thi':'Lưu thiết lập'}</button>{!creating&&<button type="button" className="button secondary" disabled={busy||dirty||!!pending||!form.paperId} onClick={()=>{if(window.confirm('Công bố cuộc thi với bộ đề và khung giờ đã chọn?'))void send('publish');}}>Công bố cuộc thi</button>}{dirty&&<small className="muted">Có thay đổi chưa lưu</small>}</div>{pending&&!busy&&<button type="button" className="button primary" onClick={()=>void send('draft')}>Gửi lại thao tác chưa xác nhận</button>}</form></section>;
}
