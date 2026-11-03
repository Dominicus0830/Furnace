const { setup, chat, sleep, ask, near, hold, waitEnt } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/difficulty easy', '/gamerule doMobSpawning false']) await chat(bot, c);
  const angry = async () => (await ask(bot, '/execute if entity @e[type=piglin,nbt={Brain:{memories:{"minecraft:angry_at":{}}}}]', /passed|failed/)) || '';
  async function arena(x) {
    await chat(bot, `/execute in minecraft:the_nether run fill ${x - 8} 99 -8 ${x + 8} 99 8 minecraft:netherrack`, 300);
    await chat(bot, `/execute in minecraft:the_nether run fill ${x - 8} 100 -8 ${x + 8} 104 8 minecraft:air`, 300);
  }
  await chat(bot, '/execute in minecraft:the_nether run tp @s 0.5 100 0.5', 5000);
  await chat(bot, '/kill @e[type=!player]', 300);
  // 1) 금 블록 캐기: 벽 뒤 피글린
  await arena(0);
  await chat(bot, '/fill 3 100 -3 3 102 3 minecraft:netherrack', 300);
  await chat(bot, '/summon minecraft:piglin 6.5 100 0.5 {PersistenceRequired:1b,IsImmuneToZombification:1b}', 300);
  await chat(bot, '/setblock -1 100 0 minecraft:gold_block', 300);
  await chat(bot, '/gamemode survival', 300);
  await chat(bot, '/item replace entity @s armor.head with minecraft:golden_helmet', 300);
  await chat(bot, '/give @s minecraft:diamond_pickaxe', 300); await hold(bot, 'diamond_pickaxe');
  await sleep(2000);
  console.log('RESULT 캐기 전 분노', await angry());
  await bot.dig(bot.blockAt(new V(-1, 100, 0)), true).catch(e => console.log('dig', e.message)); await sleep(1000);
  console.log('RESULT 금 블록 캔 뒤 분노(벽 뒤 피글린)', await angry(), await ask(bot, '/data get entity @e[type=piglin,limit=1] Brain.memories', /entity data/));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 2) 상자 열기: 보는 피글린만
  await chat(bot, '/fill 3 100 -3 3 102 3 minecraft:air', 300);
  await chat(bot, '/summon minecraft:piglin 4.5 100 0.5 {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:["see"]}', 300);
  await chat(bot, '/summon minecraft:piglin -6.5 100 6.5 {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:["blind"],Rotation:[0f,0f]}', 300);
  await chat(bot, '/fill -8 100 3 -3 103 3 minecraft:netherrack', 300);
  await chat(bot, '/setblock 0 100 -2 minecraft:chest', 1500);
  const w = await bot.openBlock(bot.blockAt(new V(0, 100, -2))); await sleep(800); bot.closeWindow(w); await sleep(800);
  console.log('RESULT 상자 연 뒤: 보는 피글린', await ask(bot, '/execute if entity @e[tag=see,nbt={Brain:{memories:{"minecraft:angry_at":{}}}}]', /passed|failed/), '벽 뒤 피글린', await ask(bot, '/execute if entity @e[tag=blind,nbt={Brain:{memories:{"minecraft:angry_at":{}}}}]', /passed|failed/));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 3) 깔때기 수레 열기 → 분노 없음
  await chat(bot, '/summon minecraft:piglin 4.5 100 0.5 {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:["see"]}', 300);
  await chat(bot, '/summon minecraft:hopper_minecart 0.5 100 -1.5', 1500);
  const hm = await waitEnt(bot, 'hopper_minecart'); await bot.activateEntity(hm); await sleep(800); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  console.log('RESULT 깔때기 수레 연 뒤', await angry());
  await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/summon minecraft:piglin 4.5 100 0.5 {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:["see"]}', 300);
  await chat(bot, '/summon minecraft:chest_minecart 0.5 100 -1.5', 1500);
  const cm = await waitEnt(bot, 'chest_minecart'); await bot.activateEntity(cm); await sleep(800); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  console.log('RESULT 상자 수레 연 뒤', await angry());
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
