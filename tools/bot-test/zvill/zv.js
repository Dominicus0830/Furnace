// 좀비가 AI 없는 주민을 계속 치는지(바닐라: 무적 시간 20틱마다 계속) — 체력을 1초마다 읽는다.
// 사용: SRV=<서버 폴더> node zvill/zv.js [상자안:1 | 열린곳:0]
const U = require('../round2/u'); const { sleep, chat, ask, num } = U;
(async () => {
  const bot = await U.setup('Zv');
  for (const c of ['/gamemode creative', '/difficulty easy', '/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set midnight', '/kill @e[type=!player]']) await chat(bot, c, 150);
  await chat(bot, '/tp @s 615.5 -60 615.5', 3000);
  await chat(bot, '/fill 595 -61 595 606 -57 606 glass hollow', 300);
  await chat(bot, '/fill 596 -60 596 605 -58 605 air', 300);
  await chat(bot, '/summon villager 600.5 -60 600.5 {PersistenceRequired:1b,NoAI:1b}', 200);
  await chat(bot, '/summon zombie 604.5 -60 604.5 {PersistenceRequired:1b}', 200);
  const out = [];
  for (let i = 0; i < 12; i++) {
    await sleep(1000);
    const hp = await num(bot, '@e[type=villager,limit=1,sort=nearest]', 'Health');
    const z = Object.values(bot.entities).find(e => e.name === 'zombie'); const v = Object.values(bot.entities).find(e => e.name === 'villager');
    out.push(`${i + 1}s hp=${hp} z=${z && z.position.toString()} v=${v && v.position.toString()}`);
  }
  console.log('RESULT\n' + out.join('\n'));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
