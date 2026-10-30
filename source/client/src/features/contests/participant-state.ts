import type { Contest, Participant } from './types';

export function participantStage(p:Participant,c:Contest,serverNow:number) {
  if(p.status==='REQUESTED')return {label:'Chờ duyệt',tone:'pending'};
  if(p.status==='REJECTED')return {label:'Không được duyệt',tone:'rejected'};
  if(p.status==='REVOKED')return {label:'Đã thu hồi quyền thi',tone:'revoked'};
  if(p.status!=='APPROVED')return {label:'Chưa xác định',tone:'neutral'};
  if(p.submitted)return {label:'Đã nộp / chốt bài',tone:'submitted'};
  if(c.state==='CANCELLED')return {label:'Cuộc thi đã hủy',tone:'neutral'};
  if(c.state==='ARCHIVED')return {label:'Cuộc thi đã lưu trữ',tone:'neutral'};
  const started=serverNow>=Date.parse(c.startsAt)||['RUNNING','CLOSING','FINALIZED','ARCHIVED'].includes(c.state);
  if(!p.checkedInAt)return started?{label:'Vắng thi',tone:'absent'}:{label:'Chưa vào phòng',tone:'not-entered'};
  if(serverNow>=Date.parse(p.deadline)||['CLOSING','FINALIZED','ARCHIVED'].includes(c.state))return {label:'Chờ chốt bài',tone:'finalizing'};
  return started?{label:'Đang làm bài',tone:'in-progress'}:{label:'Đang chờ thi',tone:'waiting'};
}

export function participantConnection(p:Participant,c:Contest,serverNow:number) {
  if(p.status==='REVOKED')return {label:'Không còn quyền vào phòng thi',tone:'ended'};
  if(p.submitted)return {label:'Lượt thi đã kết thúc',tone:'ended'};
  if(['CANCELLED','CLOSING','FINALIZED','ARCHIVED'].includes(c.state))return {label:'Phòng thi đã đóng',tone:'ended'};
  if(!p.checkedInAt)return {label:'Chưa vào phòng thi',tone:'idle'};
  const live=p.online&&p.lastSeenAt!==null&&Date.parse(p.lastSeenAt)>serverNow-45000;
  return live?{label:'Đang kết nối phòng thi',tone:'online'}:{label:'Không có kết nối thi',tone:'offline'};
}

export function participantTab(p:Participant,c:Contest,serverNow:number) {
  if(p.status!=='APPROVED'||p.submitted||!p.checkedInAt||c.state!=='RUNNING'||serverNow>=Date.parse(p.deadline))return null;
  if(participantConnection(p,c,serverNow).tone!=='online'||typeof p.tabHidden!=='boolean')return {label:'Chưa rõ trạng thái tab',tone:'unknown'};
  return p.tabHidden?{label:'Ngoài tab thi',tone:'outside'}:{label:'Trong tab thi',tone:'inside'};
}
