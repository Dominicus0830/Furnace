// O00 기준선: N 명이 서로 멀리 떨어져(간격 GAP 블록) 새 땅을 한 방향으로 SPEED 블록/초로 이동. 콘솔 /tp 로 움직인다.
// 사용: SRV=<서버 폴더> node o00walk.js <인원> <시야> <속도 블록/초> <이동 초> [모드=new|old]
// 모드 old: 이동 대신 제자리(이미 만든 땅 접속 시험용) — 접속~시야 전체 수신 ms 만 잰다.
// 결과: JSON 한 줄(인원 합계와 사람별 중앙값).
const mc = require('minecraft-protocol');
const fs = require('fs');
const [nArg = '1', vdArg = '10', spArg = '10', secArg = '60', mode = 'new'] = process.argv.slice(2);
const N = +nArg, vd = +vdArg, speed = +spArg, secs = +secArg;
// R0 노트북(11-14): 콘솔 tp 를 모두 합쳐 초당 CMD_RATE(기본 16)개 넘게 보내지 않는다 — A(07dec3da)는 콘솔 명령을 초당 30~40개만
// 처리해서 32명×4회/초면 실제 이동이 1/3 로 줄었다(F-R0-01 측정 함정). 실제 이동 거리는 결과의 movedZ* 로 적는다.
const GAP = +(process.env.GAP || 3000), R = Math.max(2, vd - 2);
const STEP_MS = +(process.env.STEP_MS || Math.max(250, Math.ceil(N * 1000 / +(process.env.CMD_RATE || 16))));
const SRV = process.env.SRV;
const cmd = c => fs.appendFileSync(SRV + '/cmds', c + '\n');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const now = () => Date.now();

function client(i) {
  const name = (process.env.NAMEP || 'W') + i;
  const st = { name, i, t0: now(), have: new Map(), chunks: 0, bytes: 0, px: null, pz: null, cover: [], kicked: null };
  const c = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
  st.c = c;
  const patch = () => {   // 청크 본문 해석 생략(클라이언트 CPU 가 측정을 막지 않게)
    const d = c.deserializer; if (!d || d.__fast) return; d.__fast = true;
    const orig = d.parsePacketBuffer.bind(d);
    d.parsePacketBuffer = buf => (c.state === 'play' && buf[0] === 0x27)
      ? { data: { name: 'map_chunk', params: { x: buf.readInt32BE(1), z: buf.readInt32BE(5), size: buf.length } }, metadata: { size: buf.length }, buffer: buf }
      : orig(buf);
  };
  c.on('state', s => { if (s === 'play') setImmediate(patch); });
  c.on('login', () => { patch(); st.login = now() - st.t0; c.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true }); });
  c.on('position', p => { st.px = p.x; st.pz = p.z; try { c.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
  c.on('map_chunk', d => { st.have.set(d.x + ',' + d.z, now()); st.chunks++; st.bytes += d.size || 0; });
  c.on('unload_chunk', d => st.have.delete(d.chunkX + ',' + d.chunkZ));
  c.on('chunk_batch_finished', () => c.write('chunk_batch_received', { chunksPerTick: 64 }));
  c.on('kick_disconnect', r => { st.kicked = JSON.stringify(r); });
  c.on('error', e => { st.err = e.message; });
  return st;
}
// R0: 정사각 r 과 바닐라 원형 시야(ChunkTrackingView.isWithinDistance, 둘레 포함)의 교집합만 센다.
// A(정사각 전송)와 B(F1 원형 전송) 둘 다 이 집합을 빠짐없이 보내므로 같은 잣대가 된다.
function inCircle(dx, dz) {
  const a = Math.max(0, Math.abs(dx) - 1), b = Math.max(0, Math.abs(dz) - 1);
  const major = Math.max(0, Math.max(a, b) - 1), minor = Math.min(a, b);
  return minor * minor + major * major < vd * vd;
}
function cover(st, r = R) {
  if (st.px === null) return 0;
  const cx = Math.floor(st.px / 16), cz = Math.floor(st.pz / 16); let n = 0, tot = 0;
  for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) { if (!inCircle(dx, dz)) continue; tot++; if (st.have.has((cx + dx) + ',' + (cz + dz))) n++; }
  return n / tot;
}
const med = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.floor(s.length / 2)] : null; };

(async () => {
  const sts = [];
  for (let i = 0; i < N; i++) { sts.push(client(i)); await sleep(150); }
  await sleep(4000);
  for (const s of sts) { cmd(`gamemode creative ${s.name}`); }
  if (mode === 'old') {
    // 이미 만든 땅: 각자 자기 자리로 보낸 뒤 시야 전체를 받을 때까지
    const t = now();
    for (const s of sts) { s.have.clear(); cmd(`tp ${s.name} ${(+(process.env.XOFF||0)) + s.i * GAP} 120 0`); }
    const done = new Map();
    while (now() - t < 60000 && done.size < N) {
      for (const s of sts) if (!done.has(s.name) && s.px !== null && Math.abs(s.px - ((+(process.env.XOFF||0)) + s.i * GAP)) < 2 && cover(s, vd) >= 1) done.set(s.name, now() - t);
      await sleep(50);
    }
    console.log(JSON.stringify({ mode, N, vd, fullViewMsMedian: med([...done.values()]), fullViewMsMax: Math.max(...done.values()), done: done.size, miss: sts.filter(s => !done.has(s.name)).slice(0,3).map(s => ({ px: s.px, pz: s.pz, cov: +cover(s, vd).toFixed(4), cov2: +cover(s, vd - 1).toFixed(4) })), kicked: sts.filter(s => s.kicked).length, chunksRecv: sts.reduce((a, s) => a + s.chunks, 0), bytesRecv: sts.reduce((a, s) => a + s.bytes, 0) }));
    // R0: HOLD(ms) 동안 접속을 붙잡아 둔다(⑦ 힙 히스토그램을 청크가 올라 있는 동안 뜨려고).
    if (process.env.HOLD) { try { fs.writeFileSync(SRV + '/hold.flag', '1'); } catch (e) {} await sleep(+process.env.HOLD); }
    for (const s of sts) s.c.end(); await sleep(500); process.exit(0);
  }
  // 새 땅: 각자 x=i*GAP 줄에서 +z 로 이동
  const XOFF = +(process.env.XOFF || 0); const x = i => XOFF + i * GAP;
  for (const s of sts) { s.have.clear(); cmd(`tp ${s.name} ${x(s.i)} 200 0`); }
  // 새 땅 첫 시야: 모두 시야 전체(vd)를 받을 때까지(최대 150초) — 이것이 "새 땅 접속" 시간
  const tj = now(); const joined = new Map();
  while (now() - tj < 150000 && joined.size < N) { for (const s of sts) if (!joined.has(s.name) && s.px !== null && Math.abs(s.px - x(s.i)) < 2 && cover(s, vd) >= 1) joined.set(s.name, now() - tj); await sleep(50); }
  const joinMs = [...joined.values()];
  const walkStart = Date.now() / 1000;
  const c0 = sts.map(s => s.chunks), b0 = sts.map(s => s.bytes);
  const t0 = now(); let z = 0, k = 0;
  while (now() - t0 < secs * 1000) {
    await sleep(STEP_MS);
    z = speed * (now() - t0) / 1000;
    for (const s of sts) cmd(`tp ${s.name} ${x(s.i)} 200 ${z.toFixed(1)}`);
    if (++k % Math.max(1, Math.round(1000 / STEP_MS)) === 0) for (const s of sts) s.cover.push(cover(s));
  }
  const el = (now() - t0) / 1000;
  await sleep(1500);  // 마지막 tp 가 처리될 틈
  const moved = sts.map(s => s.pz === null ? 0 : s.pz);
  const recv = sts.map((s, j) => s.chunks - c0[j]);
  // 멈춘 뒤 시야(R) 다 채워지기까지
  const ts = now(); const fill = new Map();
  while (now() - ts < 30000 && fill.size < N) { for (const s of sts) if (!fill.has(s.name) && cover(s) >= 1) fill.set(s.name, now() - ts); await sleep(50); }
  const covAvg = sts.map(s => s.cover.reduce((a, b) => a + b, 0) / Math.max(1, s.cover.length));
  console.log(JSON.stringify({
    mode, N, vd, speed, secs: +el.toFixed(1), R, stepMs: STEP_MS, intendedZ: +z.toFixed(1), movedZMedian: +med(moved).toFixed(1), movedZMin: +Math.min(...moved).toFixed(1), movedZMax: +Math.max(...moved).toFixed(1), realSpeedMedian: +(med(moved) / el).toFixed(2), newViewMsMedian: med(joinMs), newViewMsMax: joinMs.length ? Math.max(...joinMs) : null, newViewDone: joinMs.length, walkStart: +walkStart.toFixed(1), walkEnd: +(walkStart + el).toFixed(1),
    chunksPerSecTotal: +(recv.reduce((a, b) => a + b, 0) / el).toFixed(1),
    chunksPerSecPerPlayerMedian: +(med(recv) / el).toFixed(1),
    needPerPlayerPerSec: +((2 * vd + 1) * speed / 16).toFixed(1),
    mbPerSecTotal: +((sts.reduce((a, s, j) => a + s.bytes - b0[j], 0)) / el / 1048576).toFixed(2),
    coverAvgMedian: +med(covAvg).toFixed(3), coverAvgMin: +Math.min(...covAvg).toFixed(3),
    fillAfterStopMsMedian: med([...fill.values()]), fillAfterStopMsMax: fill.size ? Math.max(...fill.values()) : null, filled: fill.size,
    kicked: sts.filter(s => s.kicked).map(s => s.kicked).slice(0, 2), errs: sts.filter(s => s.err).length,
  }));
  for (const s of sts) s.c.end(); await sleep(500); process.exit(0);
})();
