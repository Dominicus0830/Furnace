const { setup, chat, sleep, ask } = require('./u');
(async () => {
  const bot = await setup();
  await chat(bot, '/gamerule doMobSpawning true', 300);
  console.log('RESULT rule', await ask(bot, '/gamerule doTraderSpawning', /./));
  await sleep(+process.argv[2]);
  await chat(bot, '/save-all flush', 3000);
  bot.quit(); await sleep(500); process.exit(0);
})();
