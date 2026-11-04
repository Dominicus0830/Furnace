const { setup, chat, sleep, ask, near, hold, waitEnt } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/gamerule doMobSpawning false', '/gamerule randomTickSpeed 3']) await chat(bot, c);
  await chat(bot, '/tp @s 0.5 -60 -6.5', 800);
  await chat(bot, '/fill -2 -62 -2 2 -61 2 minecraft:water', 300);
  await chat(bot, '/setblock 0 -60 0 minecraft:frogspawn', 300);
  await chat(bot, '/setblock 10 -60 0 minecraft:sniffer_egg', 300);
  await chat(bot, '/setblock 14 -61 0 minecraft:moss_block', 300); await chat(bot, '/setblock 14 -60 0 minecraft:sniffer_egg', 300);
  const st = () => `알:${bot.blockAt(new V(0, -60, 0))?.name} 스니퍼알:${JSON.stringify(bot.blockAt(new V(10, -60, 0))?.getProperties())} 이끼위:${JSON.stringify(bot.blockAt(new V(14, -60, 0))?.getProperties())} 올챙이:${near(bot, 'tadpole').length} 아기스니퍼:${near(bot, 'sniffer').length}`;
  console.log('RESULT 시작', st());
  for (let i = 1; i <= 8; i++) {
    await chat(bot, '/tick sprint 3000', 300);
    for (let k = 0; k < 60; k++) { await sleep(500); if (bot.msgs.slice(-3).some(m => /Sprint completed/i.test(m.m))) break; }
    await sleep(500);
    console.log('RESULT', i * 3000, '틱 뒤', st());
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
