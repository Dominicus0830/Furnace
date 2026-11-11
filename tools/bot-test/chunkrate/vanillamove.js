// 빠른 이동 중 청크 구멍 측정: 바닐라 클라이언트 속도 계산(vanillaclient.js 와 같음)으로 접속한 뒤, 콘솔(SRV/cmds)로 /tp 를 주기적으로 걸어
// 한 방향으로 움직이고, 매 이동 직전에 지금 중심 둘레 반경 R 안에 받은 청크 비율을 잰다.
// 사용: SRV=<서버 폴더> node chunkrate/vanillamove.js <이름> <시야> <걸음 블록> <주기 ms> <걸음 수> [R=4]
const mc = require('minecraft-protocol');
const fs = require('fs');
const [name = 'Mov', vdArg = '10', stepArg = '32', periodArg = '1000', nArg = '20', rArg = '4'] = process.argv.slice(2);
const vd = +vdArg, step = +stepArg, period = +periodArg, steps = +nArg, R = +rArg;
const now = () => Number(process.hrtime.bigint()) / 1e6;
let agg = 2000000.0, oldW = 1, startT = 0;
const desired = () => 7000000.0 / agg;
const have = new Set();
let px = null, pz = null, chunks = 0;
const client = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
client.on('login', () => client.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true }));
client.on('position', p => { px = p.x; pz = p.z; try { client.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
client.on('map_chunk', d => { have.add(d.x + ',' + d.z); chunks++; });
client.on('unload_chunk', d => have.delete(d.chunkX + ',' + d.chunkZ));
client.on('chunk_batch_start', () => { startT = now(); });
client.on('chunk_batch_finished', d => {
  if (d.batchSize > 0) { const e = (now() - startT) * 1e6 / d.batchSize; const f = Math.min(Math.max(e, agg / 3), agg * 3); agg = (agg * oldW + f) / (oldW + 1); oldW = Math.min(49, oldW + 1); }
  client.write('chunk_batch_received', { chunksPerTick: desired() });
});
client.on('error', e => console.log('ERR', e.message));
const cover = () => { const cx = Math.floor(px / 16), cz = Math.floor(pz / 16); let n = 0; for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) if (have.has((cx + dx) + ',' + (cz + dz))) n++; return n / (2 * R + 1) ** 2; };
const tp = (x, z) => fs.appendFileSync(process.env.SRV + '/cmds', `tp ${name} ${x} 200 ${z}\n`);
setTimeout(() => {   // 처음 시야를 받을 시간
  const x0 = px, z0 = pz; const samples = []; let i = 0;
  tp(x0, z0);   // 하늘에 띄워 떨어지지 않게(관전 흉내 대신 높이 200)
  const iv = setInterval(() => {
    if (i > 0) samples.push(+cover().toFixed(2));
    if (i >= steps) {
      clearInterval(iv);
      const avg = samples.reduce((a, b) => a + b, 0) / samples.length;
      setTimeout(() => {   // 멈춘 뒤 다 채워지기까지
        const t1 = Date.now(); const w = setInterval(() => {
          if (cover() >= 1 || Date.now() - t1 > 30000) {
            clearInterval(w);
            console.log(JSON.stringify({ name, vd, step, period, speedBlocksPerSec: step * 1000 / period, R, coverAvg: +avg.toFixed(3), coverMin: Math.min(...samples), fillAfterStopMs: Date.now() - t1, chunks, want: +desired().toFixed(2), samples }));
            client.end(); setTimeout(() => process.exit(0), 300);
          }
        }, 50);
      }, 0);
      return;
    }
    i++; tp(x0 + step * i, z0);
  }, period);
}, +(process.env.WARM || 8000));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 240000);
