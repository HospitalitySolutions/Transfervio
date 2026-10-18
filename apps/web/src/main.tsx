import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

function App() {
  const [runtime, setRuntime] = React.useState<Record<string, unknown>>();
  React.useEffect(() => { fetch('/api/runtime').then(r => r.json()).then(setRuntime); }, []);
  return <main className="foundation"><div className="wordmark">↗ transfervio<span>LOCAL WORKSPACE</span></div><p className="eyebrow">A BETTER WAY TO MOVE FORWARD</p><h1>A fresh start.<br/>With your data intact.</h1><p>Your private migration workspace is ready. Bring clients and appointments together, review every detail, and move with confidence.</p><section className="card"><span className="badge">● Local development</span><h2>Foundation status</h2>{runtime ? <dl>{Object.entries(runtime).map(([k,v]) => <React.Fragment key={k}><dt>{k.replaceAll('_', ' ')}</dt><dd>{String(v)}</dd></React.Fragment>)}</dl> : <p>Connecting to the local API…</p>}</section><small>Stored on this computer · Mock AI · Destination writes disabled</small></main>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
