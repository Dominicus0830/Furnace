const { setup, chat, sleep, ask, near, hold, count } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
  await chat(bot, '/tp @s 140 -60 0 0 30', 800);
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box', 500);
  await chat(bot, '/data modify block 140 -60 2 Items set value [{Slot:0b,id:"minecraft:diamond",count:5}]', 300);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(2500);
  const it = bot.inventory.items().find(i => i.name === 'red_shulker_box');
  console.log('RESULT 크리에이티브 부순 뒤 인벤토리 셜커', !!it, JSON.stringify(it?.components));
  console.log('RESULT 엔티티 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  // 주워진 것(혹은 아이템 엔티티)을 다시 놓기
  if (!it) { await chat(bot, '/tp @s 140.5 -60 2.5', 1500); await chat(bot, '/tp @s 140 -60 0 0 30', 600); }
  await hold(bot, 'red_shulker_box');
  await bot.placeBlock(bot.blockAt(new V(142, -61, 2)), new V(0, 1, 0)).catch(e => console.log('place', e.message)); await sleep(800);
  console.log('RESULT 다시 놓은 셜커', await ask(bot, '/data get block 142 -60 2', /./));
  // 서바이벌
  await chat(bot, '/clear @s', 300);
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box', 500);
  await chat(bot, '/data modify block 140 -60 2 Items set value [{Slot:0b,id:"minecraft:diamond",count:5}]', 300);
  await chat(bot, '/gamemode survival', 300);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(2500);
  const it2 = bot.inventory.items().find(i => i.name === 'red_shulker_box');
  console.log('RESULT 서바이벌 부순 뒤 인벤토리', !!it2, JSON.stringify(it2?.components), '아이템 엔티티', near(bot, 'item').length);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
