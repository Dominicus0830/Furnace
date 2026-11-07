// 여러 명 동시 청크 수신 속도 측정(M12). minecraft-protocol 직접, 청크 본문은 해석하지 않는다.
// 사용(tools/bot-test 에서): SRV=<서버 폴더> node chunkrate/multi.js <모드> <인원> [시야=10] [cpt=64]
//  모드 together : 인원 모두 동시에 접속 → 각자 시야 전체(정사각형)를 다 받을 때까지 ms
//       join     : (인원-1)명이 먼저 접속해 다 받은 뒤 마지막 1명이 접속 → 마지막 사람 ms(앞사람은 가만히 있음)
//       apart    : 인원 모두 접속 후, 콘솔 /tp 로 서로 멀리 떨어진 (이미 생성된) 곳으로 동시에 보냄 → 각자 새 시야 ms
//       same     : apart 와 같지만 모두 같은 곳(0번 자리)으로 — 같은 청크를 여러 명이 동시에 받음
// 결과는 JSON 한 줄씩. APART_BASE(기본 2000) 블록 간격으로 x 축에 늘어선다(APART_Z 로 z 를 옮긴다, ROUNDS 로 tp 횟수).
// 측정·결과: docs/feature-parity/perf-multiplayer-chunks-2026-11-07.md
const mc = require('minecraft-protocol');
const fs = require('fs');
const [mode = 'together', nArg = '1', vdArg = '10', cptArg = '64'] = process.argv.slice(2);
const N = +nArg, vd = +vdArg, cpt = +cptArg;
const SRV = process.env.SRV;
const APART = +(process.env.APART_BASE || 2000);
const APART_Z = +(process.env.APART_Z || 0);
const cmd = c => fs.appendFileSync(SRV + '/cmds', c + '\n');
const sleep = ms => new Promise(r => setTimeout(r, ms));

function client(name) {
  const st = { name, t0: Date.now(), got: new Map(), cx: null, cz: null, waiters: [], batches: 0, chunks: 0 };
  const c = mc.createClient({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.1', auth: 'offline' });
  st.c = c;
  // 청크 본문은 해석하지 않는다: play 상태 역직렬화기에서 map_chunk(0x27)는 x,z 만 읽고 넘긴다 — 클라이언트 CPU 가 측정을 막지 않게.
  const patch = () => {
    const d = c.deserializer; if (!d || d.__fast) return; d.__fast = true;
    const orig = d.parsePacketBuffer.bind(d);
    d.parsePacketBuffer = buf => (c.state === 'play' && buf[0] === 0x27)
      ? { data: { name: 'map_chunk', params: { x: buf.readInt32BE(1), z: buf.readInt32BE(5) } }, metadata: { size: buf.length }, buffer: buf }
      : orig(buf);
  };
  c.on('state', s => { if (s === 'play') setImmediate(patch); });
  c.on('login', patch);
  c.on('login', () => {
    st.login = Date.now() - st.t0;
    c.write('settings', { locale: 'en_us', viewDistance: vd, chatFlags: 0, chatColors: true, skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true });
  });
  c.on('position', p => {
    try { c.write('teleport_confirm', { teleportId: p.teleportId }); } catch (e) {}
    st.cx = Math.floor(p.x / 16); st.cz = Math.floor(p.z / 16);
    st.pos = p;
    check(st);
  });
  c.on('map_chunk', d => { st.got.set(d.x + ',' + d.z, Date.now()); st.chunks++; check(st); });
  c.on('unload_chunk', d => { st.got.delete(d.chunkX + ',' + d.chunkZ); });
  c.on('chunk_batch_finished', () => { st.batches++; c.write('chunk_batch_received', { chunksPerTick: cpt }); });
  c.on('kick_disconnect', r => console.log('KICK', name, JSON.stringify(r)));
  c.on('error', e => console.log('ERR', name, e.message));
  return st;
}
function full(st) {
  if (st.cx === null) return false;
  for (let dx = -vd; dx <= vd; dx++) for (let dz = -vd; dz <= vd; dz++)
    if (!st.got.has((st.cx + dx) + ',' + (st.cz + dz))) return false;
  return true;
}
function doneAt(st) {
  let m = 0;
  for (let dx = -vd; dx <= vd; dx++) for (let dz = -vd; dz <= vd; dz++) m = Math.max(m, st.got.get((st.cx + dx) + ',' + (st.cz + dz)));
  return m;
}
function gaps(st, t) {
  const ts = [...st.got.values()].sort((a, b) => a - b);
  let g = 0, at = 0, n = 0;
  for (let i = 1; i < ts.length; i++) if (ts[i] - ts[i - 1] > g) { g = ts[i] - ts[i - 1]; at = ts[i - 1] - t; n = i; }
  return { gap: g, gapAt: at, gapAfter: n, first: ts[0] - t };
}
function check(st) {
  if (!full(st)) return;
  const w = st.waiters; st.waiters = [];
  for (const f of w) f();
}
function waitFull(st, timeout = 120000) {
  return new Promise(res => {
    if (full(st)) return res(true);
    st.waiters.push(() => res(true));
    setTimeout(() => res(false), timeout);
  });
}
function waitCenter(st, cx, cz, timeout = 30000) {
  return new Promise(res => {
    const t = Date.now();
    const iv = setInterval(() => {
      if (st.cx === cx && st.cz === cz) { clearInterval(iv); res(true); }
      else if (Date.now() - t > timeout) { clearInterval(iv); res(false); }
    }, 5);
  });
}
const out = o => { const u = process.cpuUsage(); console.log(JSON.stringify(Object.assign({ mode, N, vd, cpt }, o, { nodeCpuMs: Math.round((u.user + u.system) / 1000) }))); };

(async () => {
  if (mode === 'together') {
    const t = Date.now();
    const cs = []; for (let i = 0; i < N; i++) cs.push(client('T' + i));
    const r = await Promise.all(cs.map(s => waitFull(s)));
    out({ ms: cs.map((s, i) => r[i] ? doneAt(s) - t : 'timeout'), login: cs.map(s => s.login), chunks: cs.map(s => s.chunks), gaps: cs.map(s => gaps(s, t)) });
    cs.forEach(s => s.c.end());
  } else if (mode === 'join') {
    const cs = []; for (let i = 0; i < N - 1; i++) cs.push(client('J' + i));
    await Promise.all(cs.map(s => waitFull(s)));
    await sleep(1500);
    const t = Date.now();
    const last = client('Jlast');
    const ok = await waitFull(last);
    out({ ms: ok ? doneAt(last) - t : 'timeout', login: last.login, chunks: last.chunks, gaps: gaps(last, t) });
    [...cs, last].forEach(s => s.c.end());
  } else if (mode === 'apart' || mode === 'same') {
    const cs = []; for (let i = 0; i < N; i++) cs.push(client('A' + i));
    await Promise.all(cs.map(s => waitFull(s)));
    const rounds = +(process.env.ROUNDS || 3);
    for (let r = 0; r < rounds; r++) {
      await sleep(1500);
      const t = Date.now();
      const dest = cs.map((s, i) => [((mode === 'same' ? 0 : i) + 1) * APART + r * 16 * (2 * vd + 3), 100, APART_Z]);
      for (let i = 0; i < N; i++) cmd(`tp ${cs[i].name} ${dest[i][0]} ${dest[i][1]} ${dest[i][2]}`);
      const centered = await Promise.all(cs.map((s, i) => waitCenter(s, Math.floor(dest[i][0] / 16), Math.floor(dest[i][2] / 16))));
      const ok = await Promise.all(cs.map(s => waitFull(s)));
      out({ round: r, ms: cs.map((s, i) => centered[i] && ok[i] ? doneAt(s) - t : 'timeout') });
    }
    cs.forEach(s => s.c.end());
  }
  setTimeout(() => process.exit(0), 300);
})();
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 600000);
