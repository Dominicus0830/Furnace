// NoAI 주민 한 대 맞았을 때 미끄러지는 거리(바닐라 기대: 0.4/(1-0.546*0.98) ≈ 0.86칸)
const U = require('../round2/u'); const { sleep, chat } = U;
(async () => {
  const bot = await U.setup('Zv');
  for (const c of ['/gamemode creative', '/difficulty easy', '/gamerule doMobSpawning false', '/kill @e[type=!player]']) await chat(bot, c, 150);
  await chat(bot, '/tp @s 615.5 -60 615.5', 3000);
  await chat(bot, '/fill 595 -61 595 606 -57 606 stone hollow', 300);
  await chat(bot, '/fill 596 -60 596 605 -58 605 air', 300);
  await chat(bot, '/summon villager 600.5 -60 600.5 {PersistenceRequired:1b,NoAI:1b}', 200);
  await chat(bot, '/summon zombie 604.5 -60 600.5 {PersistenceRequired:1b,NoAI:1b}', 200);
  await sleep(4000);
  const pos = () => { const v = Object.values(bot.entities).find(e => e.name === 'villager'); return v && v.position.x; };
  console.log('RESULT before', pos());
  await chat(bot, '/damage @e[type=villager,limit=1] 1 minecraft:mob_attack by @e[type=zombie,limit=1]', 200);
  await sleep(3000);
  console.log('RESULT after', pos(), 'slide', (600.5 - pos()).toFixed(3));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
