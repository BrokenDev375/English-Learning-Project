// Run prepare, restart only the isolated test server, then run verify.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.env.M05_TEST_BASE_URL??'http://127.0.0.1:15176';
const target=new URL(base);
if(!['127.0.0.1','localhost'].includes(target.hostname)||target.port!=='15176'||target.protocol!=='http:')
  throw new Error('Use the isolated M05 test environment on localhost:15176; never the demo database.');
if(!['prepare','verify'].includes(process.argv[2])) throw new Error('Usage: node scripts/verify-m05-persistence.mjs prepare|verify');
mkdirSync(new URL('../.test-state/m05-xp/',import.meta.url),{recursive:true});
const statePath=new URL('../.test-state/m05-xp/persistence-state.json',import.meta.url);
const cookies=new Map();
async function raw(path,method='GET',body,headers={}) {
  const response=await fetch(base+path,{method,headers:{Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join('; '),...headers,
    ...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  for(const line of response.headers.getSetCookie()){const pair=line.split(';')[0],cut=pair.indexOf('=');cookies.set(pair.slice(0,cut),pair.slice(cut+1));}
  if(!response.ok) throw new Error(`${method} ${path}: ${response.status}`);
  return {data:await response.json(),replayed:response.headers.get('Idempotent-Replayed')};
}
async function post(path,body){const {data:csrf}=await raw('/api/auth/csrf');return raw(path,'POST',body,{[csrf.headerName]:csrf.token});}
async function ready(){for(let i=0;i<60;i++){try{await raw('/api/health');return;}catch{await new Promise(r=>setTimeout(r,1000));}}throw new Error('Server did not recover');}
await ready();
if(process.argv[2]==='prepare'){
  const email=`m05-persistence-${Date.now()}@test.local`,password='Persistence@Test123';
  await post('/api/auth/register',{email,password,fullName:'M05 persistence test'});await post('/api/auth/login',{email,password});
  const joinBody={requestId:randomUUID()};const {data:join}=await post('/api/me/league/join',joinBody);
  const {data:units}=await raw('/api/units?size=50');
  const unit=units.items.find(u=>u.title.includes('Sinh hoạt hằng ngày'));assert.ok(unit,'Seed unit exists');
  const {data:snapshot}=await post('/api/attempts',{requestId:randomUUID(),mode:'PRACTICE',topicId:unit.topicId,difficulty:'EASY',count:3});
  const curriculum=JSON.parse(readFileSync(new URL('../source/server/src/main/resources/demo/curriculum-v1.json',import.meta.url),'utf8'));
  const questions=curriculum.lessons.flatMap(l=>l.questions);
  function answer(item){const key=questions.find(q=>q.data.prompt===item.question.prompt).data.answerKey;
    if(item.question.type==='SINGLE_CHOICE')return{type:'SINGLE_CHOICE',optionId:key.correctOptionId};
    if(item.question.type==='FILL_BLANK')return{type:'FILL_BLANK',values:key.blanks.map(b=>({blankId:b.blankId,value:b.acceptedAnswers[0]}))};
    return{type:'MATCHING',pairs:key.pairs};}
  const submitBody={requestId:randomUUID(),baseRevision:snapshot.revision,playerState:snapshot.playerState,
    answers:snapshot.items.map(i=>({attemptQuestionId:i.attemptQuestionId,response:answer(i)}))};
  const {data:result}=await post(`/api/attempts/${snapshot.attemptId}/submit`,submitBody);const xp=snapshot.items.length*5;
  for(let i=0;i<20;i++){const {data:summary}=await raw('/api/me/gamification');if(summary.totalXp===xp)break;await new Promise(r=>setTimeout(r,500));}
  const syncBody={requestId:randomUUID()};const {data:sync}=await post('/api/me/gamification/sync',syncBody);
  const {data:summary}=await raw('/api/me/gamification');assert.equal(summary.totalXp,xp);
  const {data:history}=await raw(`/api/me/gamification/activities?submissionId=${result.submissionId}`);
  const {data:league}=await raw('/api/me/league');assert.equal(league.membership.weeklyXp,xp);
  writeFileSync(statePath,JSON.stringify({email,password,xp,attemptId:snapshot.attemptId,submissionId:result.submissionId,submitBody,syncBody,sync,summary,history,joinBody,join,league}));
  console.log(`Prepared real submitted activity: ${xp} lifetime and weekly XP, streak ${summary.streak.current}, persisted join/sync receipts.`);
}else{
  const state=JSON.parse(readFileSync(statePath,'utf8'));await post('/api/auth/login',{email:state.email,password:state.password});
  const replay=await post('/api/me/gamification/sync',state.syncBody);assert.equal(replay.replayed,'true');assert.deepEqual(replay.data,state.sync);
  const submitReplay=await post(`/api/attempts/${state.attemptId}/submit`,state.submitBody);assert.equal(submitReplay.replayed,'true');assert.equal(submitReplay.data.submissionId,state.submissionId);
  await post('/api/me/gamification/sync',{requestId:randomUUID()});
  const {data:summary}=await raw('/api/me/gamification');assert.equal(summary.totalXp,state.xp);assert.deepEqual(summary.streak,state.summary.streak);assert.equal(summary.revision,state.summary.revision);
  const {data:history}=await raw(`/api/me/gamification/activities?submissionId=${state.submissionId}`);assert.deepEqual(history,state.history);
  if(state.joinBody){
    const joinReplay=await post('/api/me/league/join',state.joinBody);assert.equal(joinReplay.replayed,'true');assert.deepEqual(joinReplay.data,state.join);
    const {data:league}=await raw('/api/me/league');
    for(const field of ['memberKey','groupId','tier','joinedAt','weeklyXp'])assert.deepEqual(league.membership[field],state.league.membership[field]);
    const {data:board}=await raw('/api/me/league/leaderboard');assert.equal(board.self.weeklyXp,state.xp);
    assert.equal(board.items.filter(m=>m.memberKey===state.league.membership.memberKey).length,1);
  }
  console.log('PASS: restart kept XP, streak, history, league membership/weekly XP and original receipts; replay created no extra XP or membership.');
}
