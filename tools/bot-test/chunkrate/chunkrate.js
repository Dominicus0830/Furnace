// 청크 수신 속도 측정용 가벼운 클라이언트(minecraft-protocol 직접 사용, 청크 본문은 해석하지 않음).
// 사용: node chunkrate.js <이름> <시야> <chunksPerTick 응답값> [조용하면 끝낼 ms]
// chunk_batch_finished 마다 chunk_batch_received(chunksPerTick=인자)를 보낸다 — 바닐라 클라이언트가 빠를 때 보내는 값(최대 64)을 흉내.
const mc = require('minecraft-protocol');   // tools/bot-test 의 node_modules — cd tools/bot-test && node chunkrate/chunkrate.js …
const [name = 'Rate', vdArg = '10', cptArg = '64', quietArg = '4000'] = process.argv.slice(2);
const vd = +vdArg, cpt = +cptArg, quiet = +quietArg;
const t0 = Date.now();
let wire = 0, tLogin = -1, chunks = 0, rawBytes = 0, lastChunk = 0, first = -1;
const times = []; const batches = []; let curBatch = null;
const client = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
client.on('connect', () => client.socket.on('data', d => { wire += d.length; }));
client.on('login', () => {
  tLogin = Date.now() - t0;
  try {
    client.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true });
  } catch (e) { console.log('settings fail', e.message); }
});
client.on('position', p => { if (cx===null){cx=Math.floor(p.x/16);cz=Math.floor(p.z/16);}  try { client.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
client.on('raw.map_chunk', buf => { rawBytes += buf.length; });
let cx=null,cz=null; const got=[];
client.on('map_chunk', (d) => {
  const t = Date.now() - t0; got.push([d.x,d.z,t]); chunks++; lastChunk = t; if (first < 0) first = t; times.push(t);
  if (curBatch) curBatch.n++;
});
client.on('chunk_batch_start', () => { curBatch = { s: Date.now() - t0, n: 0 }; });
client.on('chunk_batch_finished', d => {
  if (curBatch) { curBatch.f = Date.now() - t0; curBatch.size = d.batchSize; batches.push(curBatch); curBatch = null; }
  client.write('chunk_batch_received', { chunksPerTick: cpt });
});
client.on('kick_disconnect', r => console.log('KICK', JSON.stringify(r)));
client.on('error', e => console.log('ERR', e.message));
const iv = setInterval(() => {
  if (tLogin >= 0 && chunks > 0 && Date.now() - t0 - lastChunk > quiet) {
    clearInterval(iv);
    const pct = q => times[Math.min(times.length - 1, Math.floor(times.length * q))];
    const span = (lastChunk - first) / 1000;
    const bs = batches.map(b => b.f - b.s).sort((a, b) => a - b);
    const sizes = batches.map(b => b.size);
    const ring = r => { let m=0,n=0; for (const [x,z,t] of got) if (Math.max(Math.abs(x-cx),Math.abs(z-cz))<=r){n++; m=Math.max(m,t);} return n===(2*r+1)**2? m : 'miss'+n; };
    console.log(JSON.stringify({
      name, r2: ring(2), r4: ring(4), r8: ring(8), vd, cpt, login: tLogin, chunks, first, p50: pct(0.5), p90: pct(0.9), last: lastChunk,
      chunksPerSec: +(chunks / Math.max(span, 0.001)).toFixed(1),
      rawKBperChunk: +(rawBytes / chunks / 1024).toFixed(1), wireMB: +(wire / 1048576).toFixed(2),
      wireKBperChunk: +(wire / chunks / 1024).toFixed(1), wireMBps: +(wire / 1048576 / Math.max(span, 0.001)).toFixed(2),
      batches: batches.length, avgBatch: +(sizes.reduce((a, b) => a + b, 0) / Math.max(1, sizes.length)).toFixed(1),
      maxBatch: Math.max(...sizes), batchMs_p50: bs[Math.floor(bs.length / 2)], batchMs_max: bs[bs.length - 1],
    }));
    client.end(); setTimeout(() => process.exit(0), 300);
  }
}, 100);
setTimeout(() => { console.log('TIMEOUT chunks=' + chunks); process.exit(1); }, 180000);
