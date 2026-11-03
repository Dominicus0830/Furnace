const { setup, chat, sleep, ask, near, hold, count } = require('./u');
const V = require('vec3');
const phase = process.argv[2];
(async () => {
  const bot = await setup();
  if (phase === '1') {
    for (const c of ['/gamemode creative', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
    await chat(bot, '/tp @s 150 -60 0', 800);
    await chat(bot, '/give @s minecraft:green_shulker_box[container=[{slot:0,item:{id:"minecraft:emerald",count:3}}]]', 500);
    await chat(bot, '/summon minecraft:item 150 -60 6 {PickupDelay:32767s,Age:-32768s,Item:{id:"minecraft:blue_shulker_box",count:1,components:{"minecraft:container":[{slot:0,item:{id:"minecraft:emerald",count:7}}]}}}', 500);
    await chat(bot, '/setblock 152 -60 6 minecraft:chest', 300);
    await chat(bot, '/item replace block 152 -60 6 container.0 with minecraft:yellow_shulker_box[container=[{slot:4,item:{id:"minecraft:gold_ingot",count:9}}]]', 300);
    console.log('RESULT 저장 전 인벤토리', JSON.stringify(bot.inventory.items().map(i => [i.name, JSON.stringify(i.components)])));
    await chat(bot, '/save-all flush', 2000);
  } else {
    await chat(bot, '/tp @s 150 -60 0', 1500);
    console.log('RESULT 재시작 뒤 인벤토리', JSON.stringify(bot.inventory.items().map(i => [i.name, JSON.stringify(i.components)])));
    console.log('RESULT 재시작 뒤 바닥 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
    await chat(bot, '/data merge entity @e[type=item,limit=1,sort=nearest] {PickupDelay:0s}', 300);
    await chat(bot, '/tp @s 150 -60 6', 1500);
    console.log('RESULT 주운 바닥 셜커', JSON.stringify(bot.inventory.items().filter(i => i.name === 'blue_shulker_box').map(i => i.components)));
    await chat(bot, '/item replace entity @s hotbar.8 from block 152 -60 6 container.0', 600);
    console.log('RESULT 상자 속 셜커', JSON.stringify(bot.inventory.items().filter(i => i.name === 'yellow_shulker_box').map(i => i.components)));
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
