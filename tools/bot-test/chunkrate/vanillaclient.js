// 바닐라 클라이언트의 청크 속도 계산(ChunkBatchSizeCalculator)을 그대로 흉내 내는 가벼운 접속기.
// chunkrate.js 는 늘 64 를 답해 "빠른 클라이언트"만 본다. 실제 클라이언트는 배치 시작(ChunkBatchStart)부터
// 끝(ChunkBatchFinished)까지 걸린 시간을 배치 크기로 나눠 청크당 시간을 재고, 7ms(틱 안에서 청크에 쓸 시간) ÷ 그 값을
// 원하는 틱당 청크 수로 서버에 알린다. 서버가 Start 와 Finished 사이를 벌리면 클라이언트는 자기가 느리다고 보고 속도를 낮춘다.
// 사용: node chunkrate/vanillaclient.js <이름> <시야> [청크당 클라이언트 처리 ms 를 더해 흉내, 기본 0] [조용하면 끝낼 ms]  (PORT·TIMEOUT 환경 변수)
const mc = require('minecraft-protocol');
const [name = 'Van', vdArg = '10', costArg = '0', quietArg = '5000'] = process.argv.slice(2);
const vd = +vdArg, costMs = +costArg, quiet = +quietArg;
const t0 = Date.now();
const now = () => Number(process.hrtime.bigint()) / 1e6;
// 바닐라 net.minecraft.client.multiplayer.ChunkBatchSizeCalculator
let agg = 2000000.0, oldW = 1, startT = 0;
const desired = () => 7000000.0 / agg;
function onFinished(batchSize, elapsedNs) {
  if (batchSize > 0) {
    const e = elapsedNs / batchSize;
    const f = Math.min(Math.max(e, agg / 3.0), agg * 3.0);
    agg = (agg * oldW + f) / (oldW + 1);
    oldW = Math.min(49, oldW + 1);
  }
}
let tLogin = -1, chunks = 0, lastChunk = 0, first = -1, cx = null, cz = null, wire = 0;
const got = [], batches = [], times = [];
let cur = null;
const client = mc.createClient({ host: '127.0.0.1', port: +(process.env.PORT || 25565), username: name, version: '1.21.1', auth: 'offline' });
client.on('connect', () => client.socket.on('data', d => { wire += d.length; }));
client.on('login', () => {
  tLogin = Date.now() - t0;
  client.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true });
});
client.on('position', p => { if (cx === null) { cx = Math.floor(p.x / 16); cz = Math.floor(p.z / 16); } try { client.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
client.on('chunk_batch_start', () => { startT = now(); cur = { s: Date.now() - t0, n: 0 }; });
client.on('map_chunk', d => {
  const t = Date.now() - t0; got.push([d.x, d.z, t]); chunks++; lastChunk = t; if (first < 0) first = t; times.push(t);
  if (cur) cur.n++;
});
client.on('chunk_batch_finished', d => {
  const el = (now() - startT) + costMs * d.batchSize;   // 클라이언트 처리 시간 흉내(배치 크기 × 청크당 비용)
  onFinished(d.batchSize, el * 1e6);
  const want = desired();
  if (cur) { cur.f = Date.now() - t0; cur.size = d.batchSize; cur.gap = +(now() - startT).toFixed(1); cur.want = +want.toFixed(2); batches.push(cur); cur = null; }
  client.write('chunk_batch_received', { chunksPerTick: want });
});
client.on('kick_disconnect', r => console.log('KICK', JSON.stringify(r)));
client.on('error', e => console.log('ERR', e.message));
const ring = r => { let m = 0, n = 0; for (const [x, z, t] of got) if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= r) { n++; m = Math.max(m, t); } return n === (2 * r + 1) ** 2 ? m : 'miss' + n; };
const iv = setInterval(() => {
  if (tLogin >= 0 && chunks > 0 && Date.now() - t0 - lastChunk > quiet) {
    clearInterval(iv);
    const nonEmpty = batches.filter(b => b.size > 0);
    const gaps = nonEmpty.map(b => b.gap).sort((a, b) => a - b);
    const q = (arr, p) => arr.length ? arr[Math.min(arr.length - 1, Math.floor(arr.length * p))] : null;
    const sizes = nonEmpty.map(b => b.size);
    const span = (lastChunk - first) / 1000;
    console.log(JSON.stringify({
      name, vd, costMs, login: tLogin, chunks, first, r2: ring(2), r4: ring(4), r8: ring(8), last: lastChunk,
      chunksPerSec: +(chunks / Math.max(span, 0.001)).toFixed(1), wireKBperChunk: +(wire / chunks / 1024).toFixed(1),
      batches: batches.length, avgBatch: +(sizes.reduce((a, b) => a + b, 0) / Math.max(1, sizes.length)).toFixed(1),
      gapMs_p50: q(gaps, 0.5), gapMs_p90: q(gaps, 0.9), gapMs_max: q(gaps, 0.999),
      finalWant: +desired().toFixed(2),
      wantTrace: batches.filter((b, i) => i % Math.max(1, Math.floor(batches.length / 12)) === 0).map(b => [b.f, b.size, b.gap, b.want]),
    }));
    client.end(); setTimeout(() => process.exit(0), 300);
  }
}, 100);
setTimeout(() => { console.log('TIMEOUT chunks=' + chunks + ' want=' + desired().toFixed(2)); process.exit(1); }, +(process.env.TIMEOUT || 180000));
