import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api';
import type { Page } from './types';

/** Discard obsolete search responses; never let an old page remain selectable. */
export function usePagedSearch<T>(path:string,query:string,page:number,size:number,enabled=true) {
  const params=new URLSearchParams({q:query.trim(),page:String(page),size:String(size)});
  const url=path+(path.includes('?')?'&':'?')+params;
  const [reply,setReply]=useState<{url:string;data:Page<T>|null;error:string}|null>(null);
  const [revision,setRevision]=useState(0);
  useEffect(()=>{
    if(!enabled){setReply(null);return;}
    let alive=true;
    const timer=window.setTimeout(()=>{
      void api<Page<T>>(url).then(data=>{if(alive)setReply({url,data,error:''});}).catch(e=>{if(alive)setReply({url,data:null,error:errorMessage(e)});});
    },200);
    return()=>{alive=false;window.clearTimeout(timer);};
  },[url,revision,enabled]);
  const current=reply?.url===url?reply:null;
  return {data:enabled?current?.data??null:null,error:enabled?current?.error??'':'',loading:enabled&&!current,retry:()=>{setReply(null);setRevision(n=>n+1);}};
}
