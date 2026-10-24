// Isolated, reproducible acceptance. Never accepts a demo URL/project/container.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, appendFileSync, mkdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';

const root=fileURLToPath(new URL('../',import.meta.url));
const stateDir=new URL('../.test-state/m05-final/',import.meta.url);mkdirSync(stateDir,{recursive:true});
const project='english-learning-m05-acceptance',base='http://127.0.0.1:15177';
const env={...process.env,APP_PORT:'15177',API_PORT:'18084',WS_ALLOWED_ORIGINS:'http://localhost:15177,http://127.0.0.1:15177',DEMO_ENABLED:'true'};
const mode=process.argv[2]??'all';assert.ok(['all','prepare','pending','verify','measure','audit'].includes(mode));
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function docker(args,options={}){
  if(!options.input)appendFileSync(new URL('docker-acceptance.log',stateDir),'RUN '+args.join(' ')+'\n');
  const r=spawnSync('docker',args,{cwd:root,env,encoding:'utf8',timeout:240000,maxBuffer:20*1024*1024,...options});
  if(!options.input)appendFileSync(new URL('docker-acceptance.log',stateDir),(r.stdout??'')+(r.stderr??''));
  if(r.error||r.status!==0)throw new Error('Docker acceptance command failed; see ignored .test-state/m05-final/docker-acceptance.log.');
  return r.stdout.trim();
}
const compose=['compose','-p',project,'-f','compose.yml'];
const control=fileURLToPath(new URL('worker-disabled.json',stateDir));
writeFileSync(control,JSON.stringify({services:{server:{environment:{JAVA_TOOL_OPTIONS:'-Dapp.gamification.worker-enabled=false'}}}}));
function sql(query){return docker(['exec','-i',project+'-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --raw --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE"'],{input:query+'\n'});}
function rows(query){const text=sql(query);return text?text.split('\n').map(line=>JSON.parse(line)):[];}
const quote=s=>"'"+s.replaceAll("'","''")+"'";
const timestamp=date=>date.toISOString().replace('T',' ').replace('Z','');
const integer=value=>{assert.ok(Number.isSafeInteger(value)&&value>0);return value;};
const seed=JSON.parse(readFileSync(new URL('../source/server/src/main/resources/demo/curriculum-v1.json',import.meta.url),'utf8'));
const questions=seed.lessons.flatMap(l=>l.questions);
function answer(item){const key=questions.find(q=>q.data.prompt===item.question.prompt)?.data.answerKey;assert.ok(key,'Seed question is pinned');
  if(item.question.type==='SINGLE_CHOICE')return {type:'SINGLE_CHOICE',optionId:key.correctOptionId};
  if(item.question.type==='FILL_BLANK')return {type:'FILL_BLANK',values:key.blanks.map(b=>({blankId:b.blankId,value:b.acceptedAnswers[0]}))};
  return {type:'MATCHING',pairs:key.pairs};}
const freshXp=result=>new Set(result.items.filter(i=>!i.wasSeenBefore&&i.practiceRole!=='GUIDED'&&i.firstResponseComplete&&!i.firstSupportUsed&&i.firstOutcome==='CORRECT').map(i=>i.originKey)).size*5;
class Client{
  cookies=new Map();
  async call(path,method='GET',body,headers={}){
    const r=await fetch(base+path,{method,headers:{Cookie:this.cookie(),...headers,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
    for(const line of r.headers.getSetCookie()){const pair=line.split(';')[0],i=pair.indexOf('=');this.cookies.set(pair.slice(0,i),pair.slice(i+1));}
    assert.ok(r.ok,`${method} ${path} returned ${r.status}`);return {data:await r.json(),replayed:r.headers.get('Idempotent-Replayed')};
  }
  cookie(){return [...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ');}
  async post(path,body){const {data:csrf}=await this.call('/api/auth/csrf');return this.call(path,'POST',body,{[csrf.headerName]:csrf.token});}
  async register(name){this.email=`m05-acceptance-${randomUUID()}@test.local`;this.password='Acceptance@Test123';
    await this.post('/api/auth/register',{email:this.email,password:this.password,fullName:name});const {data:user}=await this.login(this.email,this.password);this.id=integer(user.id);return this;}
  async login(email,password){return this.post('/api/auth/login',{email,password});}
}
async function poll(action,label,timeout=90000){const end=performance.now()+timeout;while(performance.now()<end){if(await action())return;await delay(300);}throw new Error('Timeout: '+label);}
async function ready(){await poll(async()=>{try{return (await fetch(base+'/api/health',{signal:AbortSignal.timeout(1000)})).ok;}catch{return false;}},'HTTP ready');}
async function idle(c){await poll(async()=>{const {data:s}=await c.call('/api/me/gamification');return s.backlog.pendingSubmissions===0&&s.backlog.deferredSubmissions===0;},'reward worker drain');}
async function practice(c,topic,count=3){return (await c.post('/api/attempts',{requestId:randomUUID(),mode:'PRACTICE',topicId:topic,difficulty:'EASY',count})).data;}
async function submit(c,snapshot){const body={requestId:randomUUID(),baseRevision:snapshot.revision,playerState:snapshot.playerState,answers:snapshot.items.map(i=>({attemptQuestionId:i.attemptQuestionId,response:answer(i)}))};
  return {body,...await c.post(`/api/attempts/${snapshot.attemptId}/submit`,body)};}
async function units(c){return (await c.call('/api/units?size=50')).data.items;}
const stateFile=new URL('durability-state.json',stateDir);
function contextRows(user){return rows(`SELECT JSON_OBJECT('attempt',attempt_id,'objective',objective_id,'cycle',cycle_key,'selected',selected_at,'due',due_at,'stage',stage,'eligible',eligible) FROM review_reward_context WHERE user_id=${integer(user)} ORDER BY attempt_id,objective_id`);}
async function snapshot(c){return {summary:(await c.call('/api/me/gamification')).data,badges:(await c.call('/api/me/badges')).data.items,
  history:(await c.call('/api/me/league/history')).data,league:(await c.call('/api/me/league')).data,contexts:contextRows(c.id)};}
function historicalMembership(user){
  // Imported zero-point membership fixture. The real worker freezes it; no fake XP/result/badge is inserted.
  const local=new Date(Date.now()+7*3600000);const monday=new Date(Date.UTC(local.getUTCFullYear(),local.getUTCMonth(),local.getUTCDate()-((local.getUTCDay()+6)%7))-7*3600000);
  let start;
  for(let weeks=1;weeks<=104;weeks++){const candidate=new Date(monday.getTime()-weeks*7*86400000);if(Number(sql(`SELECT COUNT(*) FROM league_seasons WHERE start_at=${quote(timestamp(candidate))}`))===0){start=candidate;break;}}
  assert.ok(start,'Unused historical fixture week');const end=new Date(start.getTime()+7*86400000),cutoff=new Date(end.getTime()+120000);
  const rules="JSON_OBJECT('capacity',25,'minimumMembers',10,'minimumActive',5,'promotionPercent',20,'demotionPercent',20)";
  sql(`START TRANSACTION;
    INSERT INTO league_seasons(start_at,end_at,cutoff_at,status,policy_version,rules_json) VALUES(${quote(timestamp(start))},${quote(timestamp(end))},${quote(timestamp(cutoff))},'OPEN','m05-p0-v1',${rules});
    SET @acceptance_season=LAST_INSERT_ID();
    INSERT INTO league_group_allocators(season_id,tier) VALUES(@acceptance_season,'BRONZE'),(@acceptance_season,'SILVER'),(@acceptance_season,'GOLD'),(@acceptance_season,'PLATINUM'),(@acceptance_season,'DIAMOND');
    INSERT INTO league_groups(season_id,tier,group_no,member_count) VALUES(@acceptance_season,'BRONZE',1,1);
    INSERT INTO league_members(member_key,user_id,season_id,group_id,tier,joined_at) VALUES(${quote(randomUUID())},${integer(user)},@acceptance_season,LAST_INSERT_ID(),'BRONZE',${quote(timestamp(start))});COMMIT;`);
}
async function prepare(){
  const c=await new Client().register('M05 acceptance durable');historicalMembership(c.id);
  await poll(async()=>((await c.call('/api/me/league/history')).data.totalElements===1),'real historical finalization');
  const joinBody={requestId:randomUUID()};const {data:join}=await c.post('/api/me/league/join',joinBody);
  const list=await units(c);const daily=list.find(u=>u.title.includes('Sinh hoạt hằng ngày'));assert.ok(daily);
  const detail=(await c.call('/api/units/'+daily.id)).data;let learn=(await c.post('/api/attempts',{requestId:randomUUID(),mode:'LEARN',lessonId:detail.lessons[0].id})).data;
  for(const item of learn.items.filter(i=>i.practiceRole==='GUIDED')){
    const check=(await c.post(`/api/attempts/${learn.attemptId}/items/${item.attemptQuestionId}/check`,{requestId:randomUUID(),baseRevision:learn.revision,response:answer(item)})).data;learn.revision=check.revision;}
  const lesson=await submit(c,learn);assert.equal(lesson.data.lessonCompleted,true);await idle(c);
  await c.post('/api/me/review/sync',{requestId:randomUUID()});
  sql(`UPDATE review_objective_progress SET stage=1,needs_practice=0,has_mistakes=0,next_review_at=UTC_TIMESTAMP(6)-INTERVAL 1 DAY WHERE user_id=${c.id}`);
  const review=(await c.post('/api/attempts',{requestId:randomUUID(),mode:'REVIEW',reviewKind:'DUE',topicId:daily.topicId,count:3})).data;
  const reviewed=await submit(c,review);await idle(c);
  const syncBody={requestId:randomUUID()},sync=(await c.post('/api/me/gamification/sync',syncBody)).data;
  const expectedXp=freshXp(lesson.data)+freshXp(reviewed.data)+5;assert.equal(freshXp(lesson.data),25);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM gamification_reward_claims WHERE user_id=${c.id} AND reward_type='DUE_REVIEW'`)),1);
  const before=await snapshot(c);assert.equal(before.summary.totalXp,expectedXp);assert.equal(before.league.membership.weeklyXp,expectedXp);
  assert.equal(before.badges.filter(b=>b.earnedAt).length,2);assert.ok(before.contexts.length>0);assert.equal(before.history.items.length,1);
  writeFileSync(stateFile,JSON.stringify({email:c.email,password:c.password,user:c.id,expectedXp,joinBody,join,syncBody,sync,learn,lesson,review,reviewed,before,otherTopic:list.find(u=>u.topicId!==daily.topicId).topicId}));
  console.log(`PASS prepare: real LEARN + REVIEW, ${expectedXp} XP, two badges/context and a zero-point week finalized by the real job.`);
}
async function fromState(){const state=JSON.parse(readFileSync(stateFile,'utf8'));const c=new Client();c.id=state.user;await c.login(state.email,state.password);return {state,c};}
async function pending(){const {state,c}=await fromState();const attempt=await practice(c,state.otherTopic);const pending=await submit(c,attempt);
  const s=(await c.call('/api/me/gamification')).data;assert.equal(s.backlog.pendingSubmissions,1);assert.equal(s.totalXp,state.expectedXp);
  state.pending={attempt,...pending};writeFileSync(stateFile,JSON.stringify(state));console.log('PASS pending: source committed with worker disabled, no reward page/sync required.');}
async function verify(){const {state,c}=await fromState();await idle(c);const now=await snapshot(c);const expected=state.expectedXp+(state.pending?freshXp(state.pending.data):0);
  assert.equal(now.summary.totalXp,expected);assert.equal(now.league.membership.weeklyXp,expected);assert.deepEqual(now.badges,state.before.badges);assert.deepEqual(now.contexts,state.before.contexts);assert.deepEqual(now.history,state.before.history);
  for(const field of ['memberKey','groupId','tier','joinedAt'])assert.deepEqual(now.league.membership[field],state.before.league.membership[field]);
  const replay=await c.post('/api/me/league/join',state.joinBody);assert.equal(replay.replayed,'true');assert.deepEqual(replay.data,state.join);
  const sync=await c.post('/api/me/gamification/sync',state.syncBody);assert.equal(sync.replayed,'true');assert.deepEqual(sync.data,state.sync);
  for(const [attempt,result] of [[state.learn,state.lesson],[state.review,state.reviewed],...(state.pending?[[state.pending.attempt,state.pending]]:[])]){
    const response=await c.post(`/api/attempts/${attempt.attemptId}/submit`,result.body);assert.equal(response.replayed,'true');assert.deepEqual(response.data,result.data);}
  const repeated=await snapshot(c);assert.equal(repeated.summary.totalXp,expected);assert.equal(repeated.league.membership.weeklyXp,expected);
  audit();console.log(`PASS durability: ${expected} XP/weekly XP, badges, REVIEW context, frozen week and receipts retained; replay adds nothing.`);
}
function audit(){
  const checks={profile:`SELECT COUNT(*) FROM gamification_profiles p LEFT JOIN (SELECT user_id,SUM(awarded_xp) total FROM xp_ledger GROUP BY user_id) x ON x.user_id=p.user_id WHERE p.total_xp<>COALESCE(x.total,0)`,
    day:`SELECT COUNT(*) FROM gamification_daily_stats d LEFT JOIN (SELECT user_id,activity_date,SUM(awarded_xp) total FROM xp_ledger GROUP BY user_id,activity_date) x ON x.user_id=d.user_id AND x.activity_date=d.activity_date WHERE d.awarded_xp<>COALESCE(x.total,0) OR d.awarded_xp>d.cap_xp`,
    member:`SELECT COUNT(*) FROM league_members m LEFT JOIN (SELECT user_id,season_id,SUM(league_counted_xp) total FROM xp_ledger GROUP BY user_id,season_id) x ON x.user_id=m.user_id AND x.season_id=m.season_id WHERE m.weekly_xp<>COALESCE(x.total,0)`,
    frozen:`SELECT COUNT(*) FROM league_members WHERE result_json IS NOT NULL AND weekly_xp<>CAST(JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.weeklyXp')) AS UNSIGNED)`,
    claims:`SELECT COUNT(*) FROM gamification_reward_claims c LEFT JOIN xp_ledger x ON x.claim_id=c.id WHERE x.claim_id IS NULL`,
    groupCount:`SELECT COUNT(*) FROM league_groups g LEFT JOIN (SELECT group_id,COUNT(*) n FROM league_members GROUP BY group_id) m ON m.group_id=g.id WHERE g.member_count<>COALESCE(m.n,0) OR g.member_count>25`};
  for(const [name,query] of Object.entries(checks))assert.equal(Number(sql(query)),0,'Ledger audit '+name);
  writeFileSync(new URL('ledger-audit.json',stateDir),JSON.stringify({checkedAt:new Date().toISOString(),project,checks:Object.keys(checks),mismatches:0},null,2));
}
async function measure(){
  const require=createRequire(new URL('../source/client/package.json',import.meta.url));const {ws:Socket}=require('./node_modules/playwright-core/lib/utilsBundle.js');
  const clients=[],sockets=[],events=[];const duration=[];let reconnects=0;
  const hardware=JSON.parse(docker(['info','--format','{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}']));
  try{
    for(let i=0;i<20;i++){
      const c=await new Client().register('M05 measured learner '+i);await c.post('/api/me/league/join',{requestId:randomUUID()});clients.push(c);
      for(let j=0;j<2;j++){
        const socket=new Socket(base.replace('http:','ws:')+'/ws/events',{headers:{Cookie:c.cookie(),Origin:base}});sockets.push(socket);
        await new Promise((resolve,reject)=>{socket.once('open',resolve);socket.once('error',reject);});
        socket.on('message',data=>{const event=JSON.parse(data.toString());if(['GAMIFICATION_CHANGED','LEADERBOARD_CHANGED'].includes(event.type))events.push({received:performance.now(),user:i,event});});
        for(const resource of ['GAMIFICATION','LEAGUE'])socket.send(JSON.stringify({version:1,type:'SUBSCRIBE',requestId:randomUUID(),resource}));
      }
    }
    const topic=(await units(clients[0])).find(u=>u.title.includes('Sinh hoạt hằng ngày')).topicId;
    const started=performance.now();const expected=new Map();
    await Promise.all(clients.map(async(c,index)=>{const origins=new Set();for(let i=0;i<6;i++){
      const attempt=await practice(c,topic);const at=performance.now();const {data:result}=await submit(c,attempt);duration.push(performance.now()-at);
      for(const item of result.items)if(!item.wasSeenBefore&&item.practiceRole!=='GUIDED'&&item.firstResponseComplete&&!item.firstSupportUsed&&item.firstOutcome==='CORRECT')origins.add(item.originKey);
    }expected.set(index,Math.min(200,origins.size*5));}));
    const allSubmitted=performance.now();
    await Promise.all(clients.map(async(c,index)=>{await idle(c);const s=(await c.call('/api/me/gamification')).data,l=(await c.call('/api/me/league')).data;
      assert.equal(s.totalXp,expected.get(index));assert.equal(l.membership.weeklyXp,expected.get(index));}));
    const drained=performance.now();await poll(()=>Promise.resolve(events.some(e=>e.event.type==='GAMIFICATION_CHANGED')&&events.some(e=>e.event.type==='LEADERBOARD_CHANGED')),'socket invalidations');
    audit();duration.sort((a,b)=>a-b);
    const report={at:new Date().toISOString(),project,hardware,students:20,sockets:40,submissions:120,submitP50Ms:Math.round(duration[Math.floor(duration.length*.5)]),submitP95Ms:Math.round(duration[Math.floor(duration.length*.95)]),
      totalSubmitMs:Math.round(allSubmitted-started),remainingDrainMs:Math.round(drained-allSubmitted),eventsReceived:events.length,ledgerMismatches:0,scope:'Bounded local single-backend measurement; no capacity or production SLA claim.'};
    writeFileSync(new URL('backlog-measurement.json',stateDir),JSON.stringify(report,null,2));console.log('PASS measured workload: '+JSON.stringify(report));
  }finally{for(const socket of sockets)socket.close();}
}

if(mode==='all'){
  try{
    docker([...compose,'up','-d','--build','--wait']);await ready();
    await poll(()=>Promise.resolve(docker([...compose,'logs','--no-color','server']).includes('M02 curriculum seed completed:')),'curriculum seed');
    await prepare();
    docker([...compose,'-f',control,'up','-d','--wait','server']);await ready();await pending();
    docker([...compose,'up','-d','--wait','server']);await ready();await verify();
    docker([...compose,'restart','server']);await ready();await verify();
    docker([...compose,'up','-d','--force-recreate','--wait']);await ready();await verify();await measure();
    console.log('PASS M05 acceptance: startup/backlog/restart/recreate/seed/replay/audit/measurement.');
  }finally{docker([...compose,'stop']);}
}else{await ready();await ({prepare,pending,verify,measure,audit}[mode])();}
