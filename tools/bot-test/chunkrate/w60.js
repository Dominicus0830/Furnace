// 60명 새 땅 탐험 장면. 봇이 직접 위치 패킷(20Hz)으로 움직인다(콘솔 tp 걷기 금지 — B1 측정 함정).
// 사용: SRV=<서버 폴더> node w60.js <인원> <시야> <속도 블록/초> <이동 초> <배치 lines|radial> [WARM 초]
//  lines : 사람마다 x = XOFF + i*GAP 줄로 콘솔 tp 한 번 → WARM 초 뒤 +z 로 이동(서로 안 만남).
//  radial: 스폰에서 접속한 자리 그대로, 사람마다 각도 2πi/N 방향으로 퍼져 나간다(처음엔 한 region, 점점 갈라짐).
// 높이: YMODE=ground(기본) 봇이 받은 청크의 MOTION_BLOCKING 높이맵을 읽어 땅 높이를 따라 난다(몹이 붙는 높이에서 잰다).
//       YMODE=fixed 면 Y(기본 200) 고정 — y200 에 떠 있으면 몹이 거의 안 붙어 스폰 비용이 빠지는 함정(T-176·W60 검토 6장)이 있다.
// 결과: JSON 한 줄. 걷는 동안 1초마다 링 채움(체비셰프 0~2, 3~5, 둥근 시야 R=vd-2)을 재서 사람별 평균을 내고
//   ring02Min·ring02Pct(p10·p25·p50·p75·p90)·ring02Zero(발밑 0%대인 사람 수)·ring02P10 으로 분포를 적는다(평균만 보면 불공평이 가려진다 — W-4).
// 이동 검증(콘솔 tp 걷기 금지, 봇이 직접 위치 패킷): movedMedian/movedMin(보낸 거리)·srvMovedMedian/srvMovedMin(서버가 실제 받아들인 거리,
//   멈춘 뒤 제자리 tp 로 서버 위치를 되읽음)·expectedMoved·walkOk(서버 쪽 최소 ≥ 기대의 90%). 거짓이면 그 판의 TPS·청크 수치는 믿지 말 것.
// 서버 쪽 batch.gqstale·gen.queue 등은 이 봇이 아니라 w60-sum.py 가 서버 로그([CHUNKTRACE])에서 읽는다.
const mc = require('minecraft-protocol');
const fs = require('fs');
const [nArg = '60', vdArg = '10', spArg = '5.6', secArg = '90', layout = 'lines', warmArg = '20'] = process.argv.slice(2);
const N = +nArg, vd = +vdArg, speed = +spArg, secs = +secArg, WARM = +warmArg;
const GAP = +(process.env.GAP || 3000), R = Math.max(2, vd - 2);
const SRV = process.env.SRV;
const YMODE = process.env.YMODE || 'ground', YFIX = +(process.env.Y || 200), MINY = +(process.env.MINY || -64), GOFF = +(process.env.GOFF || 0.1);
const HM = new Map(), GR = { hit: 0, miss: 0, last: new Map() };   // 청크별 MOTION_BLOCKING 높이맵(원본 long 배열)
const cmd = c => fs.appendFileSync(SRV + '/cmds', c + '\n');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const now = () => Date.now();
const HIST = {};

// map_chunk(0x27) 본문: [id][x int][z int][heightmaps: 이름 없는 NBT 복합][...]. 높이맵 long 배열(MOTION_BLOCKING, 9비트×7/long)만 떼어 둔다.
function heightmapOf(buf, st) {
  try {
    let p = 9; if (buf[p++] !== 0x0a) return;
    for (;;) {
      const t = buf[p++]; if (t === 0) return;
      const nl = buf.readUInt16BE(p); p += 2; const name = buf.toString('utf8', p, p + nl); p += nl;
      if (t !== 12) return;   // 높이맵 복합엔 long 배열뿐
      const len = buf.readInt32BE(p); p += 4;
      if (name === 'MOTION_BLOCKING') { HM.set(buf.readInt32BE(1) + ',' + buf.readInt32BE(5), Buffer.from(buf.subarray(p, p + len * 8))); return; }
      p += len * 8;
    }
  } catch (e) { /* 해석 실패는 groundMiss 로 센다 */ }
}
// 블록 (x,z) 의 서 있을 높이(첫 비어 있는 y). 청크를 못 받았으면 null.
function groundAt(x, z) {
  const b = HM.get(Math.floor(x / 16) + ',' + Math.floor(z / 16)); if (!b) return null;
  const i = ((Math.floor(z) & 15) << 4) | (Math.floor(x) & 15), li = Math.floor(i / 7), off = (i % 7) * 9;
  const hi = b.readUInt32BE(li * 8), lo = b.readUInt32BE(li * 8 + 4);
  const v = off === 0 ? lo & 511 : off < 32 ? ((lo >>> off) | (hi << (32 - off))) & 511 : (hi >>> (off - 32)) & 511;
  return v + MINY;
}
// 걷는 방향 앞 6블록까지 보아 가장 높은 곳 + 0.1(언덕에 박히지 않게). 한 번(50ms)에 ±1.5 이상 튀지 않게(서버 '너무 빠른 이동' 되돌림 방지) 부드럽게.
function yFor(s, x, z, ux, uz) {
  if (YMODE !== 'ground') return YFIX;
  let h = null;
  for (const k of [0, 2, 4, 6]) { const g = groundAt(x + ux * k, z + uz * k); if (g !== null && (h === null || g > h)) h = g; }
  if (h === null) { GR.miss++; return s.y ?? YFIX; }
  GR.hit++; const y = h + GOFF;
  if (s.y === undefined) s.y = YFIX;   // tp 한 높이에서 시작해 천천히 내려온다(한 번에 크게 내려가면 서버가 되돌림)
  s.y = Math.abs(y - s.y) > 1.5 ? s.y + Math.sign(y - s.y) * 1.5 : y;
  return s.y;
}

function client(i) {
  const name = (process.env.NAMEP || 'W') + i;
  const st = { name, i, t0: now(), have: new Set(), chunks: 0, bytes: 0, px: null, pz: null, ring02: [], ring35: [], cov: [], corr: 0 };
  const c = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
  st.c = c;
  const patch = () => {   // 청크 본문 해석 생략(봇 CPU 가 서버를 덜 막게)
    const d = c.deserializer; if (!d || d.__fast) return; d.__fast = true;
    const orig = d.parsePacketBuffer.bind(d);
    d.parsePacketBuffer = buf => (c.state === 'play' && buf[0] === 0x27)
      ? (YMODE === 'ground' && heightmapOf(buf, st), { data: { name: 'map_chunk', params: { x: buf.readInt32BE(1), z: buf.readInt32BE(5), size: buf.length } }, metadata: { size: buf.length }, buffer: buf })
      : orig(buf);
  };
  c.on('state', s => { if (s === 'play') setImmediate(patch); });
  c.on('login', () => { patch(); c.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true }); });
  c.on('position', p => { if (global.WALKING && process.env.DBG && st.i === 0 && st.corr < 4) console.error('corr', JSON.stringify(p), 'sent y', st.y); if (global.VERIFY) st.srvPos = [p.x, p.z]; if (global.WALKING) st.corr++; st.px = p.x; st.pz = p.z; try { c.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {} });
  c.on('map_chunk', d => {
    st.have.add(d.x + ',' + d.z); st.chunks++; st.bytes += d.size || 0;
    if (global.WALKING && st.px !== null) { const dd = Math.max(Math.abs(d.x - Math.floor(st.px / 16)), Math.abs(d.z - Math.floor(st.pz / 16))); HIST[Math.min(dd, 20)] = (HIST[Math.min(dd, 20)] || 0) + 1; }
  });
  c.on('unload_chunk', d => st.have.delete(d.chunkX + ',' + d.chunkZ));
  c.on('chunk_batch_finished', () => c.write('chunk_batch_received', { chunksPerTick: 64 }));
  c.on('kick_disconnect', r => { st.kicked = JSON.stringify(r); });
  c.on('error', e => { st.err = e.message; });
  return st;
}
function inCircle(dx, dz) {
  const a = Math.max(0, Math.abs(dx) - 1), b = Math.max(0, Math.abs(dz) - 1);
  const major = Math.max(0, Math.max(a, b) - 1), minor = Math.min(a, b);
  return minor * minor + major * major < vd * vd;
}
function ring(st, a, b, circleOnly = true) {
  if (st.px === null) return 0; const cx = Math.floor(st.px / 16), cz = Math.floor(st.pz / 16); let n = 0, tot = 0;
  for (let dx = -b; dx <= b; dx++) for (let dz = -b; dz <= b; dz++) { const c = Math.max(Math.abs(dx), Math.abs(dz)); if (c < a || (circleOnly && !inCircle(dx, dz))) continue; tot++; if (st.have.has((cx + dx) + ',' + (cz + dz))) n++; }
  return tot ? n / tot : 0;
}
const med = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.floor(s.length / 2)] : null; };
const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;

(async () => {
  const sts = [];
  for (let i = 0; i < N; i++) { sts.push(client(i)); await sleep(120); }
  await sleep(4000);
  for (const s of sts) cmd(`gamemode creative ${s.name}`);
  const XOFF = +(process.env.XOFF || 0);
  let start;
  if (layout === 'lines') {
    for (const s of sts) cmd(`tp ${s.name} ${XOFF + s.i * GAP} ${YFIX} 0`);
    await sleep(3000);
    start = sts.map(s => [XOFF + s.i * GAP + 0.5, 0.5]);
  } else {
    for (const s of sts) cmd(`execute as ${s.name} at @s run tp @s ~ ${YFIX} ~`);
    await sleep(3000);
    start = sts.map(s => [s.px ?? 0, s.pz ?? 0]);
  }
  // 따뜻하게: WARM 초 동안 제자리(첫 시야를 받는 시간). 이때 링 채움도 적는다.
  for (const s of sts) { try { s.c.write('abilities', { flags: 2 }); } catch (e) {} }   // 날기 켜고 시작(내려오는 동안 되돌림 방지)
  const tw = now();
  // ground 모드는 제자리에서도 땅 높이로 내려와 서 있는다(첫 시야를 받는 동안 몹 스폰 조건이 걷는 때와 같게).
  while (now() - tw < WARM * 1000) {
    await sleep(50);
    if (YMODE === 'ground') for (const s of sts) if (s.px !== null) try { s.c.write('position', { x: s.px, y: yFor(s, s.px, s.pz, 0, 0), z: s.pz, onGround: false }); } catch (e) {}
  }
  const warmRing02 = +avg(sts.map(s => ring(s, 0, 2))).toFixed(3), warmCov = +avg(sts.map(s => ring(s, 0, R))).toFixed(3);
  for (const s of sts) { try { s.c.write('abilities', { flags: 2 }); } catch (e) {} s.corr = 0; }
  const dir = sts.map(s => layout === 'lines' ? [0, 1] : [Math.cos(2 * Math.PI * s.i / N), Math.sin(2 * Math.PI * s.i / N)]);
  const c0 = sts.map(s => s.chunks), b0 = sts.map(s => s.bytes);
  const walkStart = now() / 1000;
  global.WALKING = true; const t0 = now(); let lastCov = now(); let holes = 0, samples = 0;
  while (now() - t0 < secs * 1000) {
    await sleep(50);
    const d = speed * (now() - t0) / 1000;
    for (const s of sts) {
      const [x0, z0] = start[s.i], [ux, uz] = dir[s.i];
      const x = x0 + ux * d, z = z0 + uz * d;
      try { s.c.write('position', { x, y: yFor(s, x, z, ux, uz), z, onGround: false }); } catch (e) {}
      s.px = x; s.pz = z; s.moved = d;
    }
    if (now() - lastCov >= 1000) {
      lastCov = now();
      for (const s of sts) { const r02 = ring(s, 0, 2); s.ring02.push(r02); s.ring35.push(ring(s, 3, 5)); s.cov.push(ring(s, 0, R)); samples++; if (r02 < 1) holes++; }
    }
  }
  const el = (now() - t0) / 1000; global.WALKING = false;
  const walkEnd = now() / 1000;
  global.VERIFY = true; for (const s of sts) cmd(`execute as ${s.name} at @s run tp @s ~ ~ ~`);
  { const tv = now(); while (now() - tv < 8000 && sts.some(s => s.srvPos === undefined)) await sleep(50); }
  global.VERIFY = false;
  const srvMoved = sts.filter(s => s.srvPos).map(s => Math.hypot(s.srvPos[0] - start[s.i][0], s.srvPos[1] - start[s.i][1]));
  const recv = sts.map((s, j) => s.chunks - c0[j]);
  const ts = now(); const fill = new Map();
  while (now() - ts < 30000 && fill.size < N) { for (const s of sts) if (!fill.has(s.name) && ring(s, 0, R) >= 1) fill.set(s.name, now() - ts); await sleep(100); }
  const out = {
    layout, N, vd, speed, secs: +el.toFixed(1), R, warm: WARM, warmRing02, warmCov, walkStart: +walkStart.toFixed(1), walkEnd: +walkEnd.toFixed(1),
    chunksPerSecTotal: +(recv.reduce((a, b) => a + b, 0) / el).toFixed(1),
    needPerSecTotal: +(N * (2 * vd + 1) * speed / 16).toFixed(0),
    mbPerSecTotal: +((sts.reduce((a, s, j) => a + s.bytes - b0[j], 0)) / el / 1048576).toFixed(2),
    ring02Avg: +avg(sts.map(s => avg(s.ring02))).toFixed(3), ring35Avg: +avg(sts.map(s => avg(s.ring35))).toFixed(3), coverAvg: +avg(sts.map(s => avg(s.cov))).toFixed(3),
    ring02Min: +Math.min(...sts.map(s => avg(s.ring02))).toFixed(3), ring02Pct: (a => [0.1,0.25,0.5,0.75,0.9].map(q => +a[Math.floor(q * (a.length - 1))].toFixed(3)))(sts.map(s => avg(s.ring02)).sort((x, y) => x - y)), holeFrac: +(holes / Math.max(1, samples)).toFixed(3),
    fillAfterStopMsMedian: med([...fill.values()]), filled: fill.size,
    ring02P10: +sts.map(s => avg(s.ring02)).sort((x, y) => x - y)[Math.floor(0.1 * (N - 1))].toFixed(3), ring02Zero: sts.filter(s => avg(s.ring02) < 0.05).length,
    ring02Person: process.env.PERSON ? sts.map(s => +avg(s.ring02).toFixed(3)) : undefined,
    ymode: YMODE, groundHit: GR.hit, groundMiss: GR.miss, yMedian: +med(sts.map(s => s.y ?? YFIX)).toFixed(1),
    expectedMoved: +(speed * el).toFixed(1), walkOk: srvMoved.length === N && Math.min(...srvMoved) >= 0.9 * speed * el,
    distHist: HIST, movedMedian: +med(sts.map(s => s.moved || 0)).toFixed(1), movedMin: +Math.min(...sts.map(s => s.moved || 0)).toFixed(1),
    srvMovedMedian: srvMoved.length ? +med(srvMoved).toFixed(1) : null, srvMovedMin: srvMoved.length ? +Math.min(...srvMoved).toFixed(1) : null, srvN: srvMoved.length,
    serverCorrections: sts.reduce((a, s) => a + s.corr, 0), kicked: sts.filter(s => s.kicked).map(s => s.kicked).slice(0, 2), errs: sts.filter(s => s.err).length,
  };
  out.movedZMedian = out.movedMedian; out.movedZMin = out.movedMin;   // o00walk2.js 와 같은 이름(lines 에선 z 이동 = 이동 거리)
  console.log(JSON.stringify(out));
  if (!out.walkOk) console.error('경고: 서버가 받아들인 이동이 기대(' + out.expectedMoved + ')의 90% 미만 — 위치 되돌림(serverCorrections)·접속 끊김을 확인. 이 판 수치는 믿지 말 것.');
  for (const s of sts) s.c.end(); await sleep(500); process.exit(0);
})();
