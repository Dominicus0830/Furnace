// 마을 포위가 차원에 하나인지: 멀리 떨어진 두 봇(서로 다른 region)이 둘 다 마을(종 격자) 안에 있을 때 밤마다 굴림은 한 번,
// 포위는 먼저 들어온 봇(바닐라 level.players() 순서의 첫 사람) 쪽에만 난다. 두 곳 모두 deep_dark 로 바꿔 자연 스폰 좀비를 없앤다.
const U = require('../round2/u.js');
const fs = require('fs');
const { cmd, sleep } = U;
const NIGHTS = +(process.argv[2] || 60);
const A = [8, 8], B = [3000, 3000];
function logCount() {
  const lines = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').split('\n');
  for (let i = lines.length - 1; i >= 0; i--) {
    const m = lines[i].match(/Test passed, count: (\d+)|Test failed/);
    if (m) return { n: m[1] ? +m[1] : 0, idx: i, len: lines.length };
  }
  return { n: -1 };
}
async function count(c) {
  const before = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').split('\n').length;
  cmd(`execute if entity @e[type=zombie,x=${c[0]},y=-60,z=${c[1]},distance=..56]`);
  for (let i = 0; i < 30; i++) {
    await sleep(100);
    const r = logCount();
    if (r.idx >= before - 1) return r.n;
  }
  return -1;
}
async function village(c) {
  cmd(`fillbiome ${c[0] - 64} -61 ${c[1] - 64} ${c[0] + 63} -60 ${c[1] + 63} minecraft:deep_dark`);
  for (let dx = -48; dx <= 48; dx += 16) for (let dz = -48; dz <= 48; dz += 16) cmd(`setblock ${c[0] + dx + 3} -60 ${c[1] + dz + 3} bell`);
  await sleep(1500);
}
(async () => {
  const a = await U.setup('Alpha');
  const b = await U.setup('Bravo');
  cmd('difficulty easy'); cmd('gamemode survival Alpha'); cmd('gamemode survival Bravo');
  cmd('gamerule doMobSpawning true'); cmd('gamerule doDaylightCycle true');
  cmd(`tp Alpha ${A[0]}.5 -60 ${A[1]}.5`); cmd(`tp Bravo ${B[0]}.5 -60 ${B[1]}.5`);
  cmd('effect give Alpha resistance 100000 255 true'); cmd('effect give Bravo resistance 100000 255 true');
  await sleep(5000);
  await village(A); await village(B);
  const nights = [];
  for (let n = 0; n < NIGHTS; n++) {
    cmd('time set 6000'); await sleep(300);
    cmd('kill @e[type=zombie]'); await sleep(200);
    cmd('time set 17990'); await sleep(5500);   // 자정 10틱 전 → 포위면 60틱 동안 20마리
    const ca = await count(A), cb = await count(B);
    nights.push([ca, cb]);
    console.log('night', n, ca, cb);
  }
  const sieges = nights.filter(([x, y]) => x > 0 || y > 0);
  const res = {
    nights: NIGHTS,
    siegeNights: sieges.length,
    nearAlphaOnly: sieges.filter(([x, y]) => x > 0 && y == 0).length,
    nearBravo: sieges.filter(([x, y]) => y > 0).length,
    both: sieges.filter(([x, y]) => x > 0 && y > 0).length,
    maxPerNight: Math.max(0, ...nights.map(([x, y]) => x + y)),
    counts: sieges,
  };
  console.log('RESULT', JSON.stringify(res));
  a.quit(); b.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
