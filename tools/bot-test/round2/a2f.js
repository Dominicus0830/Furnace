const { setup, chat, sleep, ask, near, hold, count } = require('./u');
(async () => {
  const bot = await setup();
  console.log('RESULT 바닥 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  await chat(bot, '/clear @s', 300);
  await chat(bot, '/give @s minecraft:green_shulker_box[container=[{slot:0,item:{id:"minecraft:emerald",count:3}}]]', 500);
  console.log('RESULT 플레이어 인벤토리 NBT', await ask(bot, '/data get entity @s Inventory', /entity data|Found/));
  await chat(bot, '/summon minecraft:item 145 -60 5 {Item:{id:"minecraft:blue_shulker_box",count:1,components:{"minecraft:container":[{slot:0,item:{id:"minecraft:emerald",count:3}}]}}}', 500);
  console.log('RESULT 소환 아이템 NBT', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest,x=145,y=-60,z=5] Item', /entity data|Found|No entity/));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
