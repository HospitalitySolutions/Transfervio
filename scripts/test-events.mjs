import {createServer} from 'node:http';
import {readFileSync,writeFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';
import assert from 'node:assert/strict';
import {verifyEvent} from '../packages/api-client-example/webhook.mjs';
const root=new URL('../',import.meta.url),env=Object.fromEntries(readFileSync(new URL('.env.local',root),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
const base=`http://localhost:${env.API_PORT||8091}`,port=Number(env.WEBHOOK_RECEIVER_PORT||8099);let key,secret,subscription,seen=new Set(),attempts=0,lastEvent;
async function api(path,method='GET',body,status=200){const r=await fetch(base+path,{method,headers:{...(key?{Authorization:`Bearer ${key}`} : {}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const text=await r.text();assert.equal(r.status,status,text);return text?JSON.parse(text):undefined;}
const server=createServer(async(req,res)=>{if(req.url!=='/events'||req.method!=='POST'){res.writeHead(404).end();return}let body='';for await(const chunk of req){body+=chunk;if(body.length>10000){res.writeHead(413).end();return}}try{const args={body,eventId:req.headers['x-transfervio-event'],timestamp:req.headers['x-transfervio-timestamp'],signature:req.headers['x-transfervio-signature'],secret,seen};const checked=verifyEvent(args);attempts++;if(attempts===1){res.writeHead(503).end();return}assert.equal(checked.duplicate,false);lastEvent=args;seen.add(checked.event.event_id);writeFileSync(new URL('.local-data/receiver-events.json',root),JSON.stringify([...seen]));res.writeHead(204).end()}catch{res.writeHead(401).end()}});
await new Promise(resolve=>server.listen(port,'127.0.0.1',resolve));
try{
 key=(await api('/auth/login','POST',{email:'admin@calendra.test',password:env.BOOTSTRAP_ADMIN_PASSWORD})).token;
 for(const target_url of ['https://app.calendra.si/events','http://169.254.169.254/events',`http://127.0.0.1:${port}/events?redirect=evil`,`http://localhost:${port}/events`])await api('/v1/webhook-subscriptions','POST',{target_url},400);
 subscription=await api('/v1/webhook-subscriptions','POST',{target_url:`http://127.0.0.1:${port}/events`});secret=subscription.signing_secret;
 const tenant=await api('/v1/tenants','POST',{external_tenant_id:'webhook-synthetic',name:'Webhook Synthetic'});const invitation=await api('/v1/invitations','POST',{tenant_id:tenant.id,allowed_entities:['CLIENT'],expires_in_hours:24});const raw=new URL(invitation.upload_url).pathname.split('/').at(-1),publicBase=`/public/invitations/${raw}`;
 const form=new FormData();form.append('files',new Blob(['id,name,email\nevent-c1,Synthetic Event,event@example.test']),'events.csv');const r=await fetch(base+publicBase+'/uploads',{method:'POST',body:form});assert.equal(r.status,200);await api(publicBase+'/process','POST');let preview;
 for(let n=0;n<30;n++){await new Promise(r=>setTimeout(r,500));preview=await api(publicBase+'/preview');if(preview.migration.status==='REVIEW')break}assert.equal(preview.migration.status,'REVIEW');await api(publicBase+'/approve','POST',{revision:preview.migration.revision});
 for(let n=0;n<40&&!lastEvent;n++)await new Promise(r=>setTimeout(r,500));assert.ok(lastEvent,'Webhook delivery timeout');assert.equal(attempts,2);assert.ok(!lastEvent.body.includes('event@example.test'));
 assert.equal(verifyEvent({...lastEvent,seen:new Set(JSON.parse(readFileSync(new URL('.local-data/receiver-events.json',root),'utf8')))}).duplicate,true);
 assert.throws(()=>verifyEvent({...lastEvent,body:lastEvent.body+' '}));assert.throws(()=>verifyEvent({...lastEvent,now:Date.now()+600000}));
 writeFileSync(new URL('.local-data/webhook-results.json',root),JSON.stringify({passed:true,ssrfTargetsRejected:4,retryAfter503:true,hmacVerified:true,replayDetectedAcrossRestart:true,staleTimestampRejected:true,noPiiInPayload:true},null,2));
 console.log('Signed webhook, retry, persisted replay detection, timestamp and SSRF checks PASS');
}finally{if(subscription)await api(`/v1/webhook-subscriptions/${subscription.id}`,'DELETE');if(key)await api('/v1/logout','POST');await new Promise(resolve=>server.close(resolve));}
