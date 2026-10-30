import { participantConnection, participantStage, participantTab } from './participant-state';
import type { Contest, Participant } from './types';

export default function ParticipantStatus({participant:p,contest,serverNow}:{participant:Participant;contest:Contest;serverNow:number}) {
  const stage=participantStage(p,contest,serverNow),connection=participantConnection(p,contest,serverNow),tab=participantTab(p,contest,serverNow);
  return <div className="contest-participant-status"><span className={'contest-stage '+stage.tone}>{stage.label}</span>{p.status==='APPROVED'&&<small className="contest-permission-note">Đã được duyệt dự thi</small>}{tab&&<span className={'contest-tab-state '+tab.tone}>{tab.label}</span>}<small className={'contest-room-connection '+connection.tone}><i aria-hidden="true"/>{connection.label}</small></div>;
}
