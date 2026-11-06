// 전달체: 활성화(16) → 물속 봇에게 전달체의 힘, 42개면 물속 익사체를 마법 피해 4로 공격(Target 저장), 틀 하나 빼면 사냥 멈춤.
const U = require('../round2/u.js');
const { cmd, sleep } = U;
const SND = require('../round6/snd.js');
const C = [8, -50, 8];
function frame() {
  const out = [];
  for (let i = -2; i <= 2; i++) for (let j = -2; j <= 2; j++) for (let k = -2; k <= 2; k++) {
    const a = Math.abs(i), b = Math.abs(j), c = Math.abs(k);
    if ((a > 1 || b > 1 || c > 1) && (i == 0 && (b == 2 || c == 2) || j == 0 && (a == 2 || c == 2) || k == 0 && (a == 2 || b == 2))) out.push([C[0] + i, C[1] + j, C[2] + k]);
  }
  return out;
}
(async () => {
  const res = {};
  const bot = await U.setup('Tester');
  const sounds = [];
  SND.listen(bot, sounds);
  cmd('difficulty easy'); cmd('gamemode survival Tester'); cmd('gamerule doMobSpawning false'); cmd('kill @e[type=!player]');
  // 물을 먼저 채우고 들어간다(먼저 tp 하면 봇이 땅까지 떨어져 물 밖에 있게 된다)
  cmd('fill 2 -56 2 14 -44 14 glass'); cmd('fill 3 -55 3 13 -45 13 water'); await sleep(1000);
  bot.chat('/tp 8.5 -52 12.5'); await sleep(2000);
  cmd(`setblock ${C.join(' ')} conduit`);
  const fr = frame();
  console.log('frame', fr.length);
  for (const p of fr.slice(0, 15)) cmd(`setblock ${p.join(' ')} prismarine`);
  await sleep(4500);
  res.botPos = bot.entity.position;
  const m15 = await U.ask(bot, `/data get block ${C.join(' ')}`, /block data|has the following|not a block entity/i);
  res.blockEntity = m15;
  res.effects15 = await U.ask(bot, '/data get entity Tester active_effects', /entity data|has the following|Found no/i);
  for (const p of fr.slice(15, 16)) cmd(`setblock ${p.join(' ')} prismarine`);
  await sleep(4500);
  const r1 = await U.ask(bot, '/data get entity Tester active_effects', /entity data|has the following|no/i);
  res.effects16 = r1;
  // 사냥
  for (const p of fr.slice(16)) cmd(`setblock ${p.join(' ')} prismarine_bricks`);
  cmd('summon drowned 8.5 -53 4.5 {NoAI:1b,PersistenceRequired:1b,Health:20f}');
  await sleep(500);
  const hp = [];
  for (let i = 0; i < 6; i++) { hp.push(await U.num(bot, '@e[type=drowned,limit=1]', 'Health')); await sleep(1000); }
  res.hpHunting = hp;
  res.target = await U.ask(bot, `/data get block ${C.join(' ')} Target`, /block data|has the following|Found no|not/i);
  // 틀 하나 빼면 41 → 사냥 멈춤
  cmd(`setblock ${fr[41].join(' ')} water`);
  await sleep(2500);
  const hp2 = [];
  for (let i = 0; i < 5; i++) { hp2.push(await U.num(bot, '@e[type=drowned,limit=1]', 'Health')); await sleep(1000); }
  res.hpAfterRemove = hp2;
  res.conduitSounds = sounds.filter(x => /conduit/.test(x.name)).reduce((m, x) => (m[x.name] = (m[x.name] || 0) + 1, m), {});
  console.log('RESULT', JSON.stringify(res, null, 1));
  bot.quit(); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
