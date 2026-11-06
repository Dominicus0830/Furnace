// 청크 저장·로드 빠른 경로 접속 시험(노이즈 월드).
// 1단계(node persist.js 1): 지형 표본을 떠서 저장하고, 표시 블록·상자를 놓고, 멀리 갔다 돌아와(퇴출 저장 → 디스크 로드) 다시 본다.
// 2단계(재시작 뒤 node persist.js 2): 디스크에서 읽은 청크의 지형 표본·표시 블록·상자 내용이 1단계와 같은지 본다.
const fs = require('fs');
const { makeBot, spawned, cmd, sleep } = require('../lib');
const SRV = process.env.SRV;
const SAMPLE = SRV + '/chunkio-sample.json';
const stage = process.argv[2];
const results = [];
function check(name, ok, detail = '') { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${detail}`); }

async function waitChunks(bot, ms = 20000) {
  let last = -1, stable = 0;
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const n = Object.keys(bot.world.async.columns ?? {}).length;
    if (n === last) { if (++stable >= 6) return n; } else { stable = 0; last = n; }
    await sleep(500);
  }
  return last;
}

// 표본: 스폰 주변 64x64 칸을 4칸 간격, 각 기둥의 위에서부터 첫 비공기 블록 이름과 y + 지하 몇 칸
function sample(bot, cx, cz) {
  const out = [];
  for (let x = cx - 32; x < cx + 32; x += 4) for (let z = cz - 32; z < cz + 32; z += 4) {
    let top = null;
    for (let y = 200; y > -64; y--) {
      const b = bot.blockAt(new (require('vec3').Vec3)(x, y, z));
      if (b && b.name !== 'air' && b.name !== 'cave_air' && b.name !== 'void_air') { top = { y, name: b.name }; break; }
    }
    const deep = [];
    for (const y of [-60, -30, 0, 30]) { const b = bot.blockAt(new (require('vec3').Vec3)(x, y, z)); deep.push(b ? b.name : null); }
    out.push({ x, z, top, deep });
  }
  return out;
}

const MARKS = [
  { x: 5, z: 5, block: 'diamond_block' },
  { x: 40, z: -37, block: 'emerald_block' },      // 다른 청크
  { x: -100, z: 70, block: 'gold_block' },
];

async function main() {
  const V = require('vec3').Vec3;
  const bot = makeBot('ChunkIo');
  await spawned(bot);
  await sleep(1000);
  cmd('op ChunkIo');
  cmd('difficulty peaceful');
  cmd('gamerule doDaylightCycle false');
  await sleep(500);
  bot.chat('/gamemode creative');
  bot.chat('/tp @s 0 200 0');
  await sleep(1500);
  const n0 = await waitChunks(bot);
  // 표본 범위(-32..31) 네 귀퉁이 청크가 다 왔는지
  for (let i = 0; i < 60; i++) {
    const ok = [[-32, -32], [31, -32], [-32, 31], [31, 31]].every(([x, z]) => bot.blockAt(new (require('vec3').Vec3)(x, 0, z)));
    if (ok) break;
    await sleep(500);
  }
  console.log('pos', bot.entity.position.toString());
  console.log('chunks', n0);
  if (stage === '1') {
    const s = sample(bot, 0, 0);
    // 표시 블록(지면 위 y=150 — 지형과 겹치지 않게), 상자 하나
    for (const m of MARKS) {
      bot.chat(`/tp @s ${m.x} 160 ${m.z}`);
      await sleep(1500);
      bot.chat(`/setblock ${m.x} 150 ${m.z} minecraft:${m.block}`);
      await sleep(400);
    }
    bot.chat('/tp @s 0 160 0');
    await sleep(1500);
    bot.chat('/setblock 2 150 2 minecraft:chest');
    await sleep(300);
    bot.chat('/item replace block 2 150 2 container.0 with minecraft:diamond 17');
    bot.chat('/item replace block 2 150 2 container.5 with minecraft:golden_apple 3');
    await sleep(800);
    fs.writeFileSync(SAMPLE, JSON.stringify(s));
    check('표본 기둥 수', s.length === 256 && s.every(c => c.top), `${s.length}`);
    // 멀리 갔다가(퇴출 → 저장) 돌아와서(디스크 로드) 같은지
    bot.chat('/tp @s 3000 200 3000');
    await sleep(2000);
    await waitChunks(bot);
    await sleep(15000);   // 퇴출 유예 지나 저장되게
    bot.chat('/tp @s 0 200 0');
    await sleep(2000);
    await waitChunks(bot);
    const s2 = sample(bot, 0, 0);
    let diff = 0;
    for (let i = 0; i < s.length; i++) if (JSON.stringify(s[i]) !== JSON.stringify(s2[i])) diff++;
    check('갔다 온 뒤 지형 표본 같음', diff === 0, `다른 기둥 ${diff}`);
    for (const m of MARKS) {
      bot.chat(`/tp @s ${m.x} 160 ${m.z}`);
      await sleep(1500);
      const b = bot.blockAt(new V(m.x, 150, m.z));
      check(`갔다 온 뒤 표시 블록 ${m.block}`, b && b.name === m.block, b ? b.name : 'null');
    }
  } else {
    const s = JSON.parse(fs.readFileSync(SAMPLE));
    const s2 = sample(bot, 0, 0);
    let diff = 0;
    for (let i = 0; i < s.length; i++) if (JSON.stringify(s[i]) !== JSON.stringify(s2[i])) { if (diff < 3) console.log('diff', JSON.stringify(s[i]), JSON.stringify(s2[i])); diff++; }
    check('재시작 뒤 지형 표본 같음', diff === 0, `다른 기둥 ${diff}/${s.length}`);
    for (const m of MARKS) {
      bot.chat(`/tp @s ${m.x} 160 ${m.z}`);
      await sleep(1500);
      await waitChunks(bot, 8000);
      const b = bot.blockAt(new V(m.x, 150, m.z));
      check(`재시작 뒤 표시 블록 ${m.block}`, b && b.name === m.block, b ? b.name : 'null');
    }
    bot.chat('/tp @s 0 160 0');
    await sleep(1500);
    const since = Date.now();
    cmd('data get block 2 150 2 Items');
    await sleep(1500);
    const log = fs.readFileSync(SRV + '/server.log', 'utf8');
    const line = log.split('\n').filter(l => l.includes('has the following block data')).pop() || '';
    check('재시작 뒤 상자 내용', /diamond/.test(line) && /17/.test(line) && /golden_apple/.test(line), line.slice(-200));
  }
  bot.quit();
  await sleep(500);
  const fails = results.filter(r => !r.ok).length;
  console.log(`RESULT ${results.length - fails}/${results.length}`);
  process.exit(fails ? 1 : 0);
}
main().catch(e => { console.error(e); process.exit(2); });
