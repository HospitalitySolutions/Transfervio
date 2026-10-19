import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const root=new URL('../',import.meta.url);
const env=Object.fromEntries(readFileSync(new URL('.env.local',root),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]));
const demo=JSON.parse(readFileSync(new URL('.local-data/demo.json',root),'utf8'));
const base=`http://localhost:${env.API_PORT||8091}`;
const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'admin@calendra.test',password:env.BOOTSTRAP_ADMIN_PASSWORD})});
assert.equal(login.status,200);const {token}=await login.json();
const headers={Authorization:`Bearer ${token}`};
async function json(path){const r=await fetch(base+path,{headers});assert.equal(r.status,200);return r.json();}
const hash=value=>createHash('sha256').update(value).digest('hex');
try {
  const clients=await json(`/v1/migrations/${demo.migration_id}/export?entity=clients`);
  const appointments=await json(`/v1/migrations/${demo.migration_id}/export?entity=appointments`);
  const detail=await json(`/v1/migrations/${demo.migration_id}`);
  const invite=new URL(demo.review_url).pathname.split('/').at(-1);
  const preview=await json(`/public/invitations/${invite}/preview`);
  const file=preview.files.find(f=>!f.deleted_at);assert.ok(file);
  const original=await fetch(`${base}/public/invitations/${invite}/uploads/${file.id}`);assert.equal(original.status,200);
  const bytes=Buffer.from(await original.arrayBuffer());assert.equal(hash(bytes),file.sha256);
  const state={migrationId:demo.migration_id,status:detail.migration.status,clients:clients.total,appointments:appointments.total,
    snapshotDigest:hash(JSON.stringify({clients,appointments})),receiptsDigest:hash(JSON.stringify(detail.delivery_results)),originalDigest:hash(bytes)};
  const checkpoint=new URL('.local-data/persistence-checkpoint.json',root);
  if(process.argv.includes('--capture')){writeFileSync(checkpoint,JSON.stringify(state,null,2));console.log('Captured approved exports, receipts and original-file digest before restart.');}
  else{assert.deepEqual(state,JSON.parse(readFileSync(checkpoint,'utf8')));console.log('PASS: restart preserved approved exports, destination receipts and byte-identical MinIO original.');}
} finally {await fetch(base+'/v1/logout',{method:'POST',headers});}
