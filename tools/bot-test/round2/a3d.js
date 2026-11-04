const { setup, chat, sleep, ask, near, hold } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/setblock 70 -60 0 minecraft:air', '/setblock 90 -60 0 minecraft:air', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/effect clear @s', '/effect give @s minecraft:resistance 300 4 true', '/time set day']) await chat(bot, c);
  await chat(bot, '/tp @s 130.5 -60 -30.5', 800);
  await chat(bot, '/setblock 130 -60 0 minecraft:trial_spawner', 300);
  await chat(bot, '/data merge block 130 -60 0 {spawn_data:{entity:{id:"minecraft:zombie"}},normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}]},ominous_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:husk"}}}]}}', 300);
  await chat(bot, '/give @s minecraft:ominous_bottle', 400); await hold(bot, 'ominous_bottle');
  bot.activateItem(); await sleep(2500); bot.deactivateItem(); await sleep(300);
  await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/tp @s 130.5 -60 -6.5', 300);
  const tl = []; for (let i = 0; i < 20; i++) { await sleep(500); const p = bot.blockAt(new V(130, -60, 0)).getProperties(); tl.push(`${p.trial_spawner_state}${p.ominous ? '*' : ''}/z${near(bot, 'zombie').length}/h${near(bot, 'husk').length}`); }
  console.log('RESULT 불길한 생성기 시간순(상태/좀비/허스크)', tl.join(' '));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
