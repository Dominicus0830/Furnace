const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const dim = process.argv[2] || 'the_nether', TICKS = +process.argv[3] || 100000;
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  await chat(bot, '/difficulty hard'); await chat(bot, '/gamerule doInsomnia true'); await chat(bot, '/gamerule doPatrolSpawning true');
  await chat(bot, '/gamerule doTraderSpawning false');
  await chat(bot, '/gamemode creative'); await chat(bot, '/time set 121000');
  if (dim === 'the_nether') {
    await chat(bot, '/execute in minecraft:the_nether run fill -3 100 -3 3 100 3 minecraft:glass', 2000);
    await chat(bot, '/execute in minecraft:the_nether run tp @s 0.5 101 0.5', 6000);
  } else {
    await chat(bot, '/tp @s 0.5 -60 0.5', 1000); if (process.env.SURF) { await chat(bot, '/tp @s 0.5 150 0.5', 4000); await chat(bot, '/fill -3 149 -3 3 149 3 minecraft:stone', 1000); await chat(bot, '/tp @s 0.5 150 0.5', 1500); }
  }
  console.log('dim', bot.game.dimension, bot.entity.position);
  const seen = {}; bot.on('entitySpawn', e => { if (['pillager', 'phantom', 'cat', 'vindicator', 'evoker', 'zombie', 'wandering_trader'].includes(e.name)) seen[e.name] = (seen[e.name] || 0) + 1; });
  const t0 = Date.now(); let t = Date.now(); bot.chat(`/tick sprint ${TICKS}`);
  const done = await waitMsg(bot, /Sprint completed|sprint/i, 900000, t + 500);
  console.log('RESULT sprint', TICKS, '소요', ((Date.now() - t0) / 1000).toFixed(0), 's', done);
  await sleep(1000);
  console.log('RESULT', dim, '봇이 본 엔티티 출현', JSON.stringify(seen));
  for (const ty of ['pillager', 'phantom', 'cat']) { t = Date.now(); bot.chat(`/execute in minecraft:${dim} run kill @e[type=${ty}]`); console.log('RESULT', dim, ty, await waitMsg(bot, /Killed|No entity/, 3000, t)); }
  t = Date.now(); bot.chat('/time query day'); console.log('RESULT day', await waitMsg(bot, /time is/i, 3000, t));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
