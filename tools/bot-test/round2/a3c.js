const { setup, chat, sleep, ask, num, near, count, hold } = require('./u');
const V = require('vec3');
async function drink(bot, ms = 2000) { bot.activateItem(); await sleep(ms); bot.deactivateItem(); await sleep(300); }
(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning true', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s']) await chat(bot, c);
  // 명령 블록 수레
  await chat(bot, '/tp @s 5 -60 -6', 800);
  await chat(bot, '/setblock 5 -60 0 minecraft:activator_rail', 300); await chat(bot, '/setblock 6 -60 0 minecraft:air', 300);
  await chat(bot, '/summon minecraft:command_block_minecart 5.5 -60 0.5 {Command:"say cart-fired"}', 800);
  const t2 = Date.now(); await chat(bot, '/setblock 6 -60 0 minecraft:redstone_block', 2500);
  console.log('RESULT 명령 블록 수레', bot.blockAt(new V(5, -60, 0)).name, JSON.stringify(bot.blockAt(new V(5, -60, 0)).getProperties()), JSON.stringify(bot.msgs.filter(m => m.t >= t2).map(m => m.m).slice(0, 4)));
  await chat(bot, '/setblock 6 -60 0 minecraft:air', 200); await chat(bot, '/kill @e[type=command_block_minecart]', 300);
  // 고고학(인벤토리 확인)
  await chat(bot, '/kill @e[type=item]', 200); await chat(bot, '/clear @s', 200); await chat(bot, '/gamemode survival', 200); await chat(bot, '/tp @s 20.5 -60 -1.5 0 30', 800);
  await chat(bot, '/setblock 20 -60 0 minecraft:suspicious_gravel', 300);
  await chat(bot, '/data merge block 20 -60 0 {LootTable:"minecraft:archaeology/trail_ruins_rare"}', 300);
  await chat(bot, '/give @s minecraft:brush', 400); await hold(bot, 'brush');
  const spawned = []; bot.on('entitySpawn', e => { if (e.name === 'item') spawned.push(e.position.clone()); });
  await bot.lookAt(new V(20.5, -59.5, 0.1)); await sleep(200);
  bot.activateBlock(bot.blockAt(new V(20, -60, 0))).catch(() => {}); await sleep(11000); bot.deactivateItem(); await sleep(1500);
  console.log('RESULT 솔질 뒤 블록', bot.blockAt(new V(20, -60, 0)).name, '생긴 아이템 엔티티', JSON.stringify(spawned), '인벤토리', JSON.stringify(bot.inventory.items().map(i => i.name)));
  // 시련 생성기(spawn_data 포함)
  await chat(bot, '/kill @e[type=item]', 200); await chat(bot, '/clear @s', 200); await chat(bot, '/effect give @s minecraft:resistance 300 4 true', 200);
  await chat(bot, '/tp @s 70.5 -60 -6.5', 600);
  await chat(bot, '/setblock 70 -60 0 minecraft:trial_spawner', 300);
  console.log('RESULT 설정', await ask(bot, '/data merge block 70 -60 0 {spawn_data:{entity:{id:"minecraft:zombie"}},normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}],total_mobs:2f,simultaneous_mobs:2f,ticks_between_spawn:20},target_cooldown_length:200}', /./));
  const st = []; for (let i = 0; i < 12; i++) { await sleep(500); st.push(bot.blockAt(new V(70, -60, 0)).getProperties().trial_spawner_state + '/' + near(bot, 'zombie').length); }
  console.log('RESULT 다가간 뒤 상태/좀비', st.join(' '));
  await chat(bot, '/kill @e[type=zombie]', 300); await sleep(3000);
  console.log('RESULT 처치 1회 뒤', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '좀비', near(bot, 'zombie').length);
  await chat(bot, '/kill @e[type=zombie]', 300);
  const st2 = []; for (let i = 0; i < 16; i++) { await sleep(500); st2.push(bot.blockAt(new V(70, -60, 0)).getProperties().trial_spawner_state + '/' + near(bot, 'item').length); }
  console.log('RESULT 다 잡은 뒤 상태/아이템', st2.join(' '), await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  // 불길한 병
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/tp @s 90.5 -60 -30.5', 800);
  await chat(bot, '/setblock 90 -60 0 minecraft:trial_spawner', 300);
  await chat(bot, '/data merge block 90 -60 0 {spawn_data:{entity:{id:"minecraft:zombie"}},normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}]},ominous_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:husk"}}}]}}', 300);
  await chat(bot, '/give @s minecraft:ominous_bottle[ominous_bottle_amplifier=1]', 400); await hold(bot, 'ominous_bottle');
  await drink(bot, 2500);
  await chat(bot, '/tp @s 90.5 -60 -6.5', 8000);
  console.log('RESULT 불길한 병 뒤 접근: 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/), '생성기', JSON.stringify(bot.blockAt(new V(90, -60, 0)).getProperties()), '허스크', near(bot, 'husk').length, '좀비', near(bot, 'zombie').length);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
