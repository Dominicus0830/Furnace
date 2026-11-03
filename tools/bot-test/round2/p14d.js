const { setup, chat, sleep, ask } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  await chat(bot, '/tp @s 0.0 -60 0.0', 1500);
  console.log('RESULT biome', await ask(bot, '/locate biome minecraft:snowy_plains', /./));
  console.log('RESULT weather', await ask(bot, '/weather rain', /./));
  await chat(bot, '/gamerule randomTickSpeed 1000', 4000);
  const cnt = (n) => bot.findBlocks({ matching: b => b.name === n, maxDistance: 64, count: 5000 }).length;
  console.log('RESULT 눈', cnt('snow'), '얼음', cnt('ice'));
  await chat(bot, '/fillbiome -48 -64 -48 47 -60 47 minecraft:snowy_plains', 500);
  await chat(bot, '/gamerule randomTickSpeed 1000', 4000);
  console.log('RESULT 눈2', cnt('snow'));
  await chat(bot, '/gamerule randomTickSpeed 3', 300);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
