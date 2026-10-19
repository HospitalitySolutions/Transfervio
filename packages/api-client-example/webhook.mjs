import {createHmac,timingSafeEqual} from 'node:crypto';
export function verifyEvent({body,eventId,timestamp,signature,secret,seen,now=Date.now()}){
  if(!/^\d+$/.test(timestamp||'')||Math.abs(now/1000-Number(timestamp))>300)throw new Error('Expired event signature');
  if(!/^[0-9a-f-]{36}$/.test(eventId||''))throw new Error('Invalid event ID');
  const expected=Buffer.from(createHmac('sha256',secret).update(`${timestamp}.${eventId}.${body}`).digest('hex'));
  const supplied=Buffer.from((signature||'').replace(/^sha256=/,''));
  if(expected.length!==supplied.length||!timingSafeEqual(expected,supplied))throw new Error('Invalid event signature');
  const event=JSON.parse(body);if(event.event_id!==eventId)throw new Error('Event ID mismatch');
  // Callers persist this event ID only after successfully committing their effect.
  return {event,duplicate:seen.has(eventId)};
}
