import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const root=new URL('../',import.meta.url);
const env=Object.fromEntries(readFileSync(new URL('.env.local',root),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
const base=`http://localhost:${env.API_PORT||8091}`;let checks=0;
async function api(path,{token,body,form,method='GET',status=200}={}) {
  const r=await fetch(base+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{})},body:form|| (body?JSON.stringify(body):undefined)});
  const text=await r.text();assert.equal(r.status,status,`${method} ${path.replace(/public\/invitations\/[^/]+/,'public/invitations/[redacted]')}: ${text.slice(0,300)}`);checks++;return text?JSON.parse(text):null;
}
const token=(await api('/auth/login',{method:'POST',body:{email:'admin@calendra.test',password:env.BOOTSTRAP_ADMIN_PASSWORD}})).token;
const tenant=await api('/v1/tenants',{token,method:'POST',body:{external_tenant_id:'synthetic-acceptance',name:'Synthetic Studio'}});
const property=await api(`/v1/tenants/${tenant.id}/properties`,{token,method:'POST',body:{external_property_id:'ljubljana-demo',name:'Ljubljana · Demo',timezone:'Europe/Ljubljana'}});
await api(`/v1/tenants/${tenant.id}/catalog`,{token,method:'PUT',body:{property_id:property.id,entries:[{kind:'SERVICE',external_id:'svc-consult',name:'Consultation'},{kind:'EMPLOYEE',external_id:'emp-demo',name:'Demo Specialist'}]}});
async function invite(){const i=await api('/v1/invitations',{token,method:'POST',body:{tenant_id:tenant.id,property_id:property.id,allowed_entities:['CLIENT','APPOINTMENT'],expires_in_hours:168}});return {...i,raw:new URL(i.upload_url).pathname.split('/').at(-1)};}
async function upload(i,files,status=200){const form=new FormData();for(const [name,bytes] of files)form.append('files',new Blob([bytes]),name);return api(`/public/invitations/${i.raw}/uploads`,{method:'POST',form,status});}
async function preview(i){return api(`/public/invitations/${i.raw}/preview`);}
async function process(i){await api(`/public/invitations/${i.raw}/process`,{method:'POST'});for(let n=0;n<40;n++){await new Promise(r=>setTimeout(r,750));const p=await preview(i);if(p.migration.status==='REVIEW')return p;if(p.migration.status==='FAILED')throw new Error('Extraction job failed');}throw new Error('Worker timeout');}
const small=await invite();
await upload(small,[['malformed.png','this is not an image']],415);
const csv=readFileSync(new URL('fixtures/synthetic/clients-sl.csv',root));
await upload(small,[['clients-sl.csv',csv],['clients-en.xlsx',readFileSync(new URL('fixtures/synthetic/clients-en.xlsx',root))],['complete.json',readFileSync(new URL('fixtures/synthetic/complete-migration.json',root))],['screenshot-01.png',readFileSync(new URL('fixtures/synthetic/screenshot-01.png',root))],['screenshot-09.png',readFileSync(new URL('fixtures/synthetic/screenshot-09.png',root))]]);
const duplicate=await upload(small,[['clients-again.csv',csv]]);assert.equal(duplicate[0].duplicate,true);
let p=await process(small);assert.equal(p.records.length,8);assert.ok(p.records.every(r=>r.issues.length===0),JSON.stringify(p.records.filter(r=>r.issues.length).map(r=>r.issues)));assert.equal(p.records.filter(r=>r.entity==='CLIENT').length,5);assert.equal(p.records.filter(r=>r.entity==='APPOINTMENT').length,3);assert.ok(p.records.some(r=>r.provenance.length>1));
await api(`/public/invitations/${small.raw}/mappings`,{method:'PATCH',body:{revision:p.migration.revision,records:[{record_id:p.records[0].id,fields:{tenant_id:'forged'}}]},status:400});
const approved=await api(`/public/invitations/${small.raw}/approve`,{method:'POST',body:{revision:p.migration.revision}});assert.equal(approved.status,'READY');
await api(`/public/invitations/${small.raw}/mappings`,{method:'PATCH',body:{revision:p.migration.revision,records:[]},status:409});
await upload(small,[['later.csv','id,name\nx,Example']],409);
const dry=await api(`/v1/migrations/${small.migration_id}/dry-run`,{token});assert.equal(dry.dry_run,true);assert.equal(dry.proposed_changes.length,8);assert.equal((await preview(small)).migration.status,'READY');
const failed=p.records.find(r=>r.entity==='CLIENT').id;
await api(`/v1/migrations/${small.migration_id}/mock-delivery`,{token,method:'POST',body:{fail_record_ids:[failed]}});assert.equal((await preview(small)).migration.status,'PARTIAL');
const delivered=await api(`/v1/migrations/${small.migration_id}/mock-delivery`,{token,method:'POST',body:{}});assert.equal((await preview(small)).migration.status,'IMPORTED');assert.equal(delivered.notifications,0);assert.equal(delivered.billing_operations,0);
const repeated=await api(`/v1/migrations/${small.migration_id}/mock-delivery`,{token,method:'POST',body:{}});assert.ok(repeated.receipts.every(r=>r.outcome==='ALREADY_EXISTS'));assert.equal(repeated.receipts.length,8);
const ambiguous=await invite();await upload(ambiguous,[['clients.csv',csv],['screenshot-17.png',readFileSync(new URL('fixtures/synthetic/screenshot-17.png',root))]]);p=await process(ambiguous);
const appointment=p.records.find(r=>r.entity==='APPOINTMENT');assert.ok(appointment.issues.some(i=>i.code==='AMBIGUOUS_DATE'));assert.ok(appointment.issues.some(i=>i.code==='INVALID_DURATION'));
await api(`/public/invitations/${ambiguous.raw}/approve`,{method:'POST',body:{revision:p.migration.revision},status:409});
await api(`/public/invitations/${ambiguous.raw}/mappings`,{method:'PATCH',body:{revision:p.migration.revision,records:[{record_id:appointment.id,fields:{start:'2026-04-03T10:00',duration_minutes:'60'}}]}});p=await preview(ambiguous);assert.ok(p.records.every(r=>r.issues.length===0));
await api(`/public/invitations/${ambiguous.raw}/approve`,{method:'POST',body:{revision:p.migration.revision}});
const large=await invite();const clients=Array.from({length:500},(_,n)=>({id:`bulk-c${n}`,name:`Synthetic Person ${n}`,email:`synthetic-${n}@example.test`}));
const appointments=Array.from({length:200},(_,n)=>({id:`bulk-a${n}`,client_id:`bulk-c${n}`,start:new Date(Date.UTC(2026,10,2,9+n*2)).toISOString().slice(0,16),timezone:'Europe/Ljubljana',duration:'45',status:'BOOKED',service:'Consultation',staff:'Demo Specialist'}));
await upload(large,[['bulk.json',JSON.stringify({clients,appointments})]]);p=await process(large);assert.equal(p.records.length,700);assert.ok(p.records.every(r=>r.issues.length===0),JSON.stringify(p.records.filter(r=>r.issues.length).slice(0,2)));const ids=p.records.map(r=>r.id).sort();
p=await process(large);assert.deepEqual(p.records.map(r=>r.id).sort(),ids);
await api(`/public/invitations/${large.raw}/approve`,{method:'POST',body:{revision:p.migration.revision}});
const exported={};for(const entity of ['clients','appointments']){let cursor=0;const rows=[];do{const page=await api(`/v1/migrations/${large.migration_id}/export?entity=${entity}&limit=73&cursor=${cursor}`,{token});rows.push(...page.records);cursor=page.next_cursor;}while(cursor!==null);exported[entity]=rows;}
assert.equal(exported.clients.length,500);assert.equal(exported.appointments.length,200);const clientIds=new Set(exported.clients.map(c=>c.stable_record_id));assert.ok(exported.appointments.every(a=>clientIds.has(a.data.client_record_id)));
const runtime=await api('/api/runtime');assert.equal(runtime.runtime,'local');assert.equal(runtime.ai_provider,'mock');assert.equal(runtime.destination_mode,'mock');assert.equal(runtime.import_write_enabled,false);
const report={passed:true,httpAssertions:checks,small:{clients:5,appointments:3,duplicateRetry:'zero new records',partialFailureRecovery:true},bulk:{clients:500,appointments:200,stableIds:true,paginated:true,linked:true},ambiguousDateCorrection:true,runtimePolicy:{runtime:runtime.runtime,aiProvider:runtime.ai_provider,destination:runtime.destination_mode,remoteWrites:runtime.import_write_enabled},networkEvidence:'Configuration and implemented local paths checked; this script is not a packet capture.'};
writeFileSync(new URL('.local-data/migration-results.json',root),JSON.stringify(report,null,2));
writeFileSync(new URL('.local-data/demo.json',root),JSON.stringify({migration_id:small.migration_id,tenant_id:tenant.id,property_id:property.id,review_url:ambiguous.upload_url,bulk_migration_id:large.migration_id},null,2));
await api('/v1/logout',{token,method:'POST'});console.log(JSON.stringify(report,null,2));
