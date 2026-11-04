const { setup, chat, sleep, ask, num, near, count, hold, uuidInts } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
  await chat(bot, '/tp @s 140 -60 0 0 30', 800);
  console.log('RESULT setblock', await ask(bot, '/setblock 140 -60 2 minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', /./));
  console.log('RESULT 블록 데이터', await ask(bot, '/data get block 140 -60 2', /./));
  const b = bot.blockAt(new V(140, -60, 2)); console.log('봇이 보는 블록', b?.name);
  await bot.dig(b, true).catch(e => console.log('dig err', e.message)); await sleep(1500);
  console.log('RESULT 부순 뒤 블록', bot.blockAt(new V(140, -60, 2))?.name, '아이템', near(bot, 'item').length, await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  await chat(bot, '/kill @e[type=item]', 300);
  // 서바이벌로 부수기(대조)
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', 500);
  await chat(bot, '/gamemode survival', 300);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(1500);
  console.log('RESULT 서바이벌로 부순 뒤', near(bot, 'item').length, await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  await chat(bot, '/kill @e[type=item]', 300);
  // 고양이
  console.log('RESULT 고양이 소환', await ask(bot, `/summon minecraft:cat 141 -60 3 {NoAI:1b,Owner:${OWN},CustomName:'"Tom"'}`, /./));
  console.log('RESULT 허스크 소환', await ask(bot, '/summon minecraft:husk 141 -60 5 {NoAI:1b}', /./));
  const t6 = Date.now(); await chat(bot, '/damage @e[type=cat,limit=1] 100 minecraft:mob_attack by @e[type=husk,limit=1]', 800);
  console.log('RESULT 고양이 죽음 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t6).map(m => m.m)));
  await chat(bot, '/kill @e[type=!player]', 300);
  for (let i = 0; i < 5; i++) await chat(bot, `/summon minecraft:pig ${30.5 + i * 0.05} -60 0.5 {attributes:[{id:"minecraft:generic.movement_speed",base:0d}]}`, 60);
  await chat(bot, '/tp @s 30 -60 -5', 3000);
  const ps = near(bot, 'pig').map(e => e.position); const spread = Math.max(...ps.map(a => Math.max(...ps.map(b => a.distanceTo(b)))));
  console.log('RESULT 0.05 간격 속도0 돼지 5마리 3초 뒤 최대 간격(처음 0.2)', spread.toFixed(2));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
