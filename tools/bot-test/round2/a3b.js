const { setup, chat, sleep, ask, num, near, count, hold } = require('./u');
const V = require('vec3');
const P = (x, y, z) => ({ x, y, z });
async function drink(bot, ms = 2000) { bot.activateItem(); await sleep(ms); bot.deactivateItem(); await sleep(300); }
(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning true', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/gamerule commandBlockOutput true']) await chat(bot, c);
  await chat(bot, '/tp @s 0 -60 -10', 1000);
  for (const c of ['/setblock 0 -60 0 minecraft:command_block[facing=east]', '/setblock 1 -60 0 minecraft:chain_command_block[facing=east]', '/setblock 0 -60 3 minecraft:repeating_command_block', '/scoreboard objectives add rep dummy', '/setblock 0 -60 -1 minecraft:air', '/setblock 6 -60 0 minecraft:air']) await chat(bot, c, 300);
  bot._client.write('update_command_block', { location: P(0, -60, 0), command: 'say impulse-fired', mode: 2, flags: 1 });
  bot._client.write('update_command_block', { location: P(1, -60, 0), command: 'say chain-fired', mode: 0, flags: 1 | 4 });
  bot._client.write('update_command_block', { location: P(0, -60, 3), command: 'scoreboard players add #c rep 1', mode: 1, flags: 1 | 4 });
  await sleep(800);
  const t1 = Date.now();
  await chat(bot, '/setblock 0 -60 -1 minecraft:redstone_block', 1500);
  console.log('RESULT 충격+체인 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t1).map(m => m.m)));
  const r1 = await ask(bot, '/scoreboard players get #c rep', /has|Can't|not/i); await sleep(1000);
  const r2 = await ask(bot, '/scoreboard players get #c rep', /has|Can't|not/i);
  console.log('RESULT 반복 블록 점수 1초 간격', r1, '→', r2);
  await chat(bot, '/setblock 0 -60 3 minecraft:air', 300);
  console.log('RESULT 명령 블록 SuccessCount·LastOutput', await ask(bot, '/data get block 0 -60 0', /block data|Found/));
  await chat(bot, '/summon minecraft:command_block_minecart 5.5 -60 0.5 {Command:"say cart-fired"}', 600);
  const t2 = Date.now(); await chat(bot, '/setblock 6 -60 0 minecraft:redstone_block', 2000);
  console.log('RESULT 명령 블록 수레', JSON.stringify(bot.msgs.filter(m => m.t >= t2).map(m => m.m).slice(0, 3)));
  await chat(bot, '/setblock 6 -60 0 minecraft:air', 200); await chat(bot, '/kill @e[type=command_block_minecart]', 300);
  // 고고학 아이템 확인
  await chat(bot, '/kill @e[type=item]', 200); await chat(bot, '/gamemode survival', 200); await chat(bot, '/tp @s 20.5 -60 -1.5 0 30', 800);
  await chat(bot, '/setblock 20 -60 0 minecraft:suspicious_gravel', 300);
  await chat(bot, '/data merge block 20 -60 0 {LootTable:"minecraft:archaeology/trail_ruins_rare"}', 300);
  await chat(bot, '/give @s minecraft:brush', 400); await hold(bot, 'brush');
  await bot.lookAt(new V(20.5, -59.5, 0.1)); await sleep(200);
  bot.activateBlock(bot.blockAt(new V(20, -60, 0))).catch(() => {}); await sleep(11000); bot.deactivateItem(); await sleep(500);
  console.log('RESULT 솔질 뒤 블록', bot.blockAt(new V(20, -60, 0)).name, '나온 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest,x=20.5,y=-59,z=0.5,distance=..3] Item', /entity data|Found|No entity/));
  // 스튜·후렴과
  await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 40 -60 0', 600);
  await chat(bot, '/effect give @s minecraft:hunger 3 255 true', 3500); await chat(bot, '/effect clear @s', 300);
  console.log('배고픔', bot.food);
  await chat(bot, '/give @s minecraft:suspicious_stew[suspicious_stew_effects=[{id:"minecraft:night_vision",duration:200}]]', 400); await hold(bot, 'suspicious_stew');
  await drink(bot, 2000);
  console.log('RESULT 수상한 스튜 뒤 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/), '손', bot.heldItem?.name, '배고픔', bot.food);
  await chat(bot, '/give @s minecraft:chorus_fruit', 400); await hold(bot, 'chorus_fruit');
  const p0 = bot.entity.position.clone(); const cds = []; bot._client.on('set_cooldown', p => cds.push(p));
  await drink(bot, 2000); await sleep(500);
  console.log('RESULT 후렴과 뒤 위치', p0, '→', bot.entity.position, '쿨다운', JSON.stringify(cds));
  // 시련 생성기 (doMobSpawning true)
  await chat(bot, '/kill @e[type=item]', 200); await chat(bot, '/clear @s', 200); await chat(bot, '/effect give @s minecraft:resistance 300 4 true', 200);
  await chat(bot, '/tp @s 70.5 -60 -6.5', 600);
  await chat(bot, '/setblock 70 -60 0 minecraft:trial_spawner', 300);
  await chat(bot, '/data merge block 70 -60 0 {normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}],total_mobs:2f,simultaneous_mobs:2f,ticks_between_spawn:20},target_cooldown_length:200}', 300);
  await sleep(6000);
  console.log('RESULT 다가간 뒤 생성기', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '좀비', near(bot, 'zombie').length);
  await chat(bot, '/kill @e[type=zombie]', 300); await sleep(4000);
  console.log('RESULT 처치 뒤 생성기', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '좀비', near(bot, 'zombie').length);
  await chat(bot, '/kill @e[type=zombie]', 300); await sleep(8000);
  console.log('RESULT 다 잡은 뒤', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '보상 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/), near(bot, 'item').length);
  // 불길한 병
  await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/tp @s 90.5 -60 -30.5', 800);
  await chat(bot, '/setblock 90 -60 0 minecraft:trial_spawner', 300);
  await chat(bot, '/data merge block 90 -60 0 {normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}]},ominous_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:husk"}}}]}}', 300);
  await chat(bot, '/give @s minecraft:ominous_bottle[ominous_bottle_amplifier=1]', 400); await hold(bot, 'ominous_bottle');
  await drink(bot, 2500);
  console.log('RESULT 불길한 병 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/));
  await chat(bot, '/tp @s 90.5 -60 -6.5', 7000);
  console.log('RESULT 접근 뒤 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/), '생성기', JSON.stringify(bot.blockAt(new V(90, -60, 0)).getProperties()), '허스크', near(bot, 'husk').length, '좀비', near(bot, 'zombie').length);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
