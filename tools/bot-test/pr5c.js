const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(2000);
  await chat(bot, '/tp @s 0.5 -60 0.5 90 0', 800);
  bot.chat('/samplespawn'); await sleep(150); const t = Date.now(); bot.chat('/data get entity @e[type=zombie,limit=1] Pos');
  console.log('RESULT 스폰 직후 좀비 Pos', await waitMsg(bot, /Zombie has/, 3000, t));
  await chat(bot, '/kill @e[type=zombie]', 300);
  process.exit(0);
})();
