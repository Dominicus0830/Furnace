const { setup, chat, sleep, ask, near, hold } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
  await chat(bot, '/tp @s 140 -60 0 0 30', 800);
  await chat(bot, '/setblock 141 -60 2 minecraft:chest{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', 500);
  console.log('RESULT 상자 setblock NBT', await ask(bot, '/data get block 141 -60 2', /./));
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box', 500);
  console.log('RESULT data modify', await ask(bot, '/data modify block 140 -60 2 Items set value [{Slot:0b,id:"minecraft:diamond",count:5}]', /./));
  console.log('RESULT 셜커 data', await ask(bot, '/data get block 140 -60 2', /./));
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(1500);
  console.log('RESULT 크리에이티브 부순 뒤', bot.blockAt(new V(140, -60, 2))?.name, await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  await chat(bot, '/kill @e[type=item]', 300);
  // 서바이벌
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box', 500);
  await chat(bot, '/data modify block 140 -60 2 Items set value [{Slot:0b,id:"minecraft:diamond",count:5}]', 300);
  await chat(bot, '/gamemode survival', 300);
  const t = Date.now(); await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); console.log('dig ms', Date.now() - t); await sleep(1500);
  console.log('RESULT 서바이벌 부순 뒤', bot.blockAt(new V(140, -60, 2))?.name, await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
