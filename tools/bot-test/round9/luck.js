// 행운·불운이 전리품(상자·몹 처치)에 반영되는지: test:t = 돌 100 / 흙 100(quality 100). 흙 비율 기대: 불운 0, 보통 .5, 행운 .67
const U = require('../round2/u.js');
const { cmd, sleep } = U;
const N = 24;
async function chestRound(bot, label) {
  let dirt = 0, stone = 0;
  for (let i = 0; i < N; i++) {
    cmd('setblock 4 -60 4 chest{LootTable:"test:t"}'); await sleep(250);
    const b = bot.blockAt(bot.entity.position.offset(0, 0, 0).floored().set(4, -60, 4));
    const w = await bot.openContainer(b);
    const items = w.containerItems();
    dirt += items.filter(x => x.name === 'dirt').reduce((a, x) => a + x.count, 0) ? 1 : 0;
    stone += items.filter(x => x.name === 'stone').reduce((a, x) => a + x.count, 0) ? 1 : 0;
    w.close(); await sleep(150);
    cmd('setblock 4 -60 4 air'); await sleep(100);
  }
  return `${label} 상자: 흙 ${dirt} / 돌 ${stone} (N=${N})`;
}
async function mobRound(bot, label) {
  let dirt = 0, stone = 0;
  for (let i = 0; i < N; i++) {
    cmd('kill @e[type=item]');
    cmd('summon pig 8.5 -60 8.5 {NoAI:1b,Tags:["lp"],DeathLootTable:"test:t"}'); await sleep(250);
    cmd('damage @e[tag=lp,limit=1] 100 minecraft:player_attack by Tester'); await sleep(350);
    const m = await U.ask(bot, '/data get entity @e[type=item,limit=1] Item.id', /entity data|has the following|No entity|Found no/i, 1500);
    if (m && /dirt/.test(m)) dirt++; else if (m && /stone/.test(m)) stone++;
  }
  return `${label} 몹: 흙 ${dirt} / 돌 ${stone} (N=${N})`;
}
(async () => {
  const out = [];
  const bot = await U.setup('Tester');
  cmd('gamemode creative Tester'); cmd('gamerule doMobSpawning false'); cmd('difficulty peaceful'); cmd('tp Tester 4.5 -60 6.5'); cmd('datapack list'); await sleep(1500);
  out.push(await U.ask(bot, '/attribute Tester minecraft:generic.luck get', /Luck|luck/i));
  const states = [['보통', 'effect clear Tester'], ['행운', 'effect give Tester luck 600 0 true'], ['불운', 'effect give Tester unluck 600 0 true'], ['행운 속성 +5(modifier)', 'effect clear Tester']];
  for (const [label, c] of states) {
    cmd('effect clear Tester'); cmd(c); await sleep(500);
    if (label.startsWith('행운 속성')) cmd('attribute Tester minecraft:generic.luck base set 5');
    else cmd('attribute Tester minecraft:generic.luck base set 0');
    await sleep(400);
    out.push(await U.ask(bot, '/attribute Tester minecraft:generic.luck get', /Luck|luck|value/i));
    out.push(await chestRound(bot, label));
    out.push(await mobRound(bot, label));
  }
  console.log('=== 결과 ===\n' + out.join('\n'));
  process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
