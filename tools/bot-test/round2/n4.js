const { setup, chat, sleep, ask } = require('./u');
(async () => {
  const bot = await setup();
  await chat(bot, '/gamerule doMobSpawning true', 300);
  await chat(bot, '/gamemode creative', 300);
  await chat(bot, '/tick sprint 6000', 300);
  await sleep(+process.argv[2] || 20000);
  await chat(bot, '/save-all flush', 3000);
  bot.quit(); await sleep(500); process.exit(0);
})();
