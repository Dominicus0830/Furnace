const { setup, chat, sleep, ask, near } = require('./u');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/difficulty easy', '/gamerule doMobSpawning true', '/gamerule doDaylightCycle true', '/effect give @s minecraft:resistance 999 4 true']) await chat(bot, c);
  const loc = await ask(bot, '/locate structure #minecraft:village', /located|Could not|nearest/i, 60000);
  console.log('RESULT 마을', loc);
  const m = loc.match(/\[(-?\d+), (~|-?\d+), (-?\d+)\]/);
  await chat(bot, `/tp @s ${m[1]} 200 ${m[3]}`, 4000);
  await chat(bot, `/spreadplayers ${m[1]} ${m[3]} 0 1 false @s`, 3000);
  await chat(bot, '/gamemode survival', 300);
  const beds = await ask(bot, `/execute if block ${m[1]} ${Math.floor(bot.entity.position.y)} ${m[3]} minecraft:air`, /./);
  console.log('위치', bot.entity.position);
  let found = null;
  for (let night = 1; night <= 30 && !found; night++) {
    await chat(bot, '/kill @e[type=zombie]', 150);
    await chat(bot, '/time set 1000', 300);
    await chat(bot, '/time set 17990', 2500);
    const z = near(bot, 'zombie', e => e.position.distanceTo(bot.entity.position) < 40).length;
    if (z >= 4) found = { night, z };
  }
  console.log('RESULT 마을 포위', JSON.stringify(found));
  if (found) { await sleep(2000); console.log('RESULT 2초 뒤 좀비', near(bot, 'zombie').length); }
  await chat(bot, '/time set 1000', 300);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
