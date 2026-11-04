const { makeBot, spawned, chat, cmd, sleep } = require('./lib');
const fs = require('fs');
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(500);
  await chat(bot, '/gamerule doMobSpawning false');
  await chat(bot, '/fill 10 -61 10 24 -61 24 minecraft:lava', 800);
  await chat(bot, '/tp @s 17 -55 5', 1000);
  const N = 600;
  for (let i = 0; i < N; i++) cmd(`summon minecraft:strider ${10 + (i % 15)} -61 ${10 + Math.floor(i / 15) % 15}`);
  const log = process.env.SRV + '/server.log';
  const cnt = () => (fs.readFileSync(log, 'utf8').match(/Summoned new Strider/g) || []).length;
  let last = -1; while (cnt() < N && cnt() !== last) { last = cnt(); await sleep(3000); }
  await sleep(3000);
  const all = Object.values(bot.entities);
  const striders = all.filter(e => e.name === 'strider');
  const babies = striders.filter(e => e.metadata[16] === true);
  const piglins = all.filter(e => e.name === 'zombified_piglin');
  const ridingPiglin = striders.filter(e => e.passengers?.some(p => p.name === 'zombified_piglin'));
  const ridingBaby = striders.filter(e => e.passengers?.some(p => p.name === 'strider'));
  console.log(`RESULT 소환 ${cnt()}, 보이는 스트라이더 ${striders.length}(새끼 ${babies.length}), 좀비 피글린 ${piglins.length}, 피글린 태운 스트라이더 ${ridingPiglin.length}, 새끼 태운 스트라이더 ${ridingBaby.length}`);
  const pg = piglins[0]; if (pg) console.log('RESULT 피글린 장비', JSON.stringify(pg.equipment?.map(i => i?.name)), 'baby', pg.metadata[16]);
  const pgSaddleStrider = ridingPiglin[0]; if (pgSaddleStrider) console.log('RESULT 피글린 탄 스트라이더 metadata[19]', pgSaddleStrider.metadata[19]);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
