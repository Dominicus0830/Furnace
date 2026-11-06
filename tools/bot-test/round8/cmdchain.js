// 명령 블록 체인 한 틱: 임펄스 + 체인 5칸. 첫 칸과 마지막 칸이 게임 시각을 점수에 적어 차이를 본다(바닐라는 0).
// 중간 칸은 @e 로 멀리 있는 다른 봇(다른 region) 옆 돼지를 찾는다 — 모든 region 을 도는 명령도 그 틱 안에 끝나야 한다.
const U = require('../round2/u.js');
const fs = require('fs');
const { cmd, sleep } = U;
const TRIALS = +(process.argv[2] || 10);
async function score(name) {
  const before = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').length;
  cmd(`scoreboard players get ${name} t`);
  for (let i = 0; i < 30; i++) {
    await sleep(100);
    const tail = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').slice(before);
    const m = tail.match(new RegExp(name + ' has (-?\\d+)'));
    if (m) return +m[1];
    if (/Can't get value|none is set|No score/.test(tail)) return null;
  }
  return null;
}
(async () => {
  const a = await U.setup('Alpha');
  const b = await U.setup('Bravo');
  cmd('gamerule commandBlockOutput false');
  cmd('tp Alpha 8.5 -60 8.5'); cmd('tp Bravo 3000.5 -60 3000.5');
  await sleep(5000);
  cmd('summon pig 3003 -60 3003 {NoAI:1b}');
  cmd('scoreboard objectives add t dummy');
  const C = ['execute if entity @e[type=pig] run scoreboard players set mid t 1',
    'execute as @e[type=pig] run scoreboard players add cnt t 1',
    'setblock 2 -60 9 stone',
    'execute store result score s4 t run time query gametime'];
  cmd('setblock 2 -60 2 command_block[facing=east]{Command:"execute store result score s0 t run time query gametime"}');
  C.forEach((c, i) => cmd(`setblock ${3 + i} -60 2 chain_command_block[facing=east]{auto:1b,Command:"${c}"}`));
  await sleep(1500);
  const diffs = [];
  for (let n = 0; n < TRIALS; n++) {
    cmd('setblock 2 -60 1 air'); await sleep(300);
    cmd('setblock 2 -60 1 redstone_block'); await sleep(1500);
    const s0 = await score('s0'), s4 = await score('s4');
    diffs.push(s0 == null || s4 == null ? null : s4 - s0);
    console.log('trial', n, s0, s4);
  }
  const cnt = await score('cnt');
  console.log('RESULT', JSON.stringify({ diffs, pigCount: cnt }));
  a.quit(); b.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
