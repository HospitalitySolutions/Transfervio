// Server-side reference client. Keep the API key out of browser code.
export class TransfervioClient {
  constructor(baseUrl,apiKey){const url=new URL(baseUrl);if(!['localhost','127.0.0.1'].includes(url.hostname)||url.protocol!=='http:')throw new Error('This reference client is local-only');this.base=baseUrl.replace(/\/$/,'');this.key=apiKey;}
  async request(path,method='GET',body){const r=await fetch(this.base+path,{method,redirect:'error',headers:{Authorization:`Bearer ${this.key}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});if(!r.ok)throw new Error(`Transfervio returned ${r.status}`);const text=await r.text();return text?JSON.parse(text):undefined;}
  async export(migration,entity){const records=[];let cursor=0;do{const page=await this.request(`/v1/migrations/${encodeURIComponent(migration)}/export?entity=${entity}&cursor=${cursor}&limit=100`);records.push(...page.records);cursor=page.next_cursor;}while(cursor!==null);return records;}
  async preview(migration){return this.request(`/v1/migrations/${encodeURIComponent(migration)}/dry-run`);}
  async poll(){return this.request('/v1/events');}
  async acknowledge(migration,results){return this.request(`/v1/migrations/${encodeURIComponent(migration)}/delivery-results`,'POST',{results});}
}
