// 블록·아이템: 명령 블록, 고고학, 가마솥, 아이템 효과, 금고, 시련 생성기, 보너스 상자
const { setup, chat, sleep, ask, num, near, count, hold, waitMsg } = require('./u');
const V = require('vec3');
const P = (x, y, z) => ({ x, y, z });
(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/gamerule commandBlockOutput true']) await chat(bot, c);
  await chat(bot, '/tp @s 0 -60 -10', 1000);
  // 1) 명령 블록: 충격(빨강 신호) → 체인 → 반복
  await chat(bot, '/setblock 0 -60 0 minecraft:command_block[facing=east]', 300);
  await chat(bot, '/setblock 1 -60 0 minecraft:chain_command_block[facing=east]', 300);
  await chat(bot, '/setblock 0 -60 3 minecraft:repeating_command_block', 300);
  await chat(bot, '/scoreboard objectives add rep dummy', 300);
  bot._client.write('update_command_block', { location: P(0, -60, 0), command: 'say impulse-fired', mode: 2, flags: 1 });
  bot._client.write('update_command_block', { location: P(1, -60, 0), command: 'say chain-fired', mode: 0, flags: 1 | 4 });
  bot._client.write('update_command_block', { location: P(0, -60, 3), command: 'scoreboard players add #c rep 1', mode: 1, flags: 1 | 4 });
  await sleep(800);
  const t1 = Date.now();
  await chat(bot, '/setblock 0 -60 -1 minecraft:redstone_block', 1500);
  console.log('RESULT 충격+체인 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t1).map(m => m.m)));
  await sleep(1000);
  const r1 = await ask(bot, '/scoreboard players get #c rep', /has|Can't|not/i); await sleep(1000);
  const r2 = await ask(bot, '/scoreboard players get #c rep', /has|Can't|not/i);
  console.log('RESULT 반복 블록 점수 1초 간격', r1, '→', r2);
  await chat(bot, '/setblock 0 -60 3 minecraft:air', 300);
  console.log('RESULT 명령 블록 데이터', await ask(bot, '/data get block 0 -60 0 SuccessCount', /data|Found/));
  // 명령 블록 수레
  await chat(bot, '/setblock 5 -60 0 minecraft:activator_rail', 300); await chat(bot, '/setblock 5 -61 0 minecraft:stone', 100);
  await chat(bot, '/summon minecraft:command_block_minecart 5.5 -60 0.5 {Command:"say cart-fired"}', 600);
  const t2 = Date.now(); await chat(bot, '/setblock 6 -60 0 minecraft:redstone_block', 2000);
  console.log('RESULT 명령 블록 수레', JSON.stringify(bot.msgs.filter(m => m.t >= t2).map(m => m.m).slice(0, 3)));
  await chat(bot, '/setblock 6 -60 0 minecraft:air', 200); await chat(bot, '/kill @e[type=command_block_minecart]', 300);
  // 2) 고고학
  await chat(bot, '/gamemode survival', 200); await chat(bot, '/tp @s 20.5 -60 -1.5 0 30', 800);
  await chat(bot, '/setblock 20 -60 0 minecraft:suspicious_sand', 300);
  await chat(bot, '/data merge block 20 -60 0 {LootTable:"minecraft:archaeology/desert_pyramid"}', 300);
  await chat(bot, '/give @s minecraft:brush', 400); await hold(bot, 'brush');
  await bot.lookAt(new V(20.5, -59.5, 0.1)); await sleep(200);
  const states = [];
  bot.activateBlock(bot.blockAt(new V(20, -60, 0))).catch(() => {});
  for (let i = 0; i < 24; i++) { await sleep(500); const b = bot.blockAt(new V(20, -60, 0)); states.push(b.name + ':' + (b.getProperties?.().dusted ?? '')); }
  bot.deactivateItem();
  console.log('RESULT 솔질 중 블록 상태(0.5초 간격)', states.join(' '));
  await sleep(500);
  console.log('RESULT 솔질 뒤 나온 아이템', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/), '솔 손상', JSON.stringify(bot.heldItem?.components?.find(c => c.type === 'damage')));
  await chat(bot, '/kill @e[type=item]', 200);
  // 3) 가마솥
  await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 30.5 -60 -1.5 0 30', 600);
  await chat(bot, '/setblock 30 -60 0 minecraft:water_cauldron[level=3]', 300);
  const cauld = async (give, name, label) => {
    await chat(bot, '/clear @s', 200); await chat(bot, give, 400); await hold(bot, name);
    await bot.activateBlock(bot.blockAt(new V(30, -60, 0))); await sleep(600);
    const it = bot.inventory.items()[0];
    console.log('RESULT 가마솥', label, '→', it?.name, JSON.stringify(it?.components?.map(c => c.type + ':' + JSON.stringify(c.data).slice(0, 80))), '물 높이', bot.blockAt(new V(30, -60, 0)).getProperties().level);
  };
  await cauld('/give @s minecraft:leather_chestplate[dyed_color={rgb:16711680}]', 'leather_chestplate', '염색 가죽 갑옷');
  await cauld('/give @s minecraft:white_banner[banner_patterns=[{pattern:"minecraft:stripe_top",color:"red"},{pattern:"minecraft:cross",color:"blue"}]]', 'white_banner', '무늬 2겹 깃발');
  await cauld('/give @s minecraft:red_shulker_box', 'red_shulker_box', '빨간 셜커');
  // 4) 아이템 효과
  await chat(bot, '/clear @s', 200); await chat(bot, '/effect clear @s', 200);
  await chat(bot, '/tp @s 40 -60 0', 600);
  await chat(bot, '/give @s minecraft:suspicious_stew[suspicious_stew_effects=[{id:"minecraft:night_vision",duration:200}]]', 400);
  await chat(bot, '/effect give @s minecraft:hunger 5 50 true', 2500); await chat(bot, '/effect clear @s minecraft:hunger', 300);
  console.log('배고픔', bot.food);
  await hold(bot, 'suspicious_stew'); await bot.consume().catch(e => console.log('consume', e.message)); await sleep(600);
  console.log('RESULT 수상한 스튜 뒤 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/), '손', bot.heldItem?.name);
  await chat(bot, '/give @s minecraft:chorus_fruit', 400); await hold(bot, 'chorus_fruit');
  const p0 = bot.entity.position.clone(); await bot.consume().catch(e => console.log('consume', e.message)); await sleep(1000);
  console.log('RESULT 후렴과 뒤 위치', p0, '→', bot.entity.position, '쿨다운 확인용');
  const snd = []; bot._client.on('sound_effect', p => snd.push(p.soundId ?? p.sound)); bot._client.on('entity_sound_effect', p => snd.push(p.soundId ?? p.sound));
  const cds = []; bot._client.on('set_cooldown', p => cds.push(p));
  await chat(bot, '/give @s minecraft:goat_horn[instrument="minecraft:ponder_goat_horn"]', 400); await hold(bot, 'goat_horn');
  bot.activateItem(); await sleep(300); bot.deactivateItem(); await sleep(500);
  console.log('RESULT 염소 뿔 쿨다운 패킷', JSON.stringify(cds), '소리 패킷(본인은 못 받음이 바닐라)', JSON.stringify(snd));
  await chat(bot, '/summon minecraft:cow 40.5 -60 2.5 {NoAI:1b}', 500);
  await chat(bot, '/give @s minecraft:name_tag[custom_name=\'"Bessie"\']', 400); await hold(bot, 'name_tag');
  const cow = near(bot, 'cow')[0]; await bot.lookAt(cow.position.offset(0, 1, 0)); await bot.activateEntity(cow); await sleep(600);
  console.log('RESULT 이름표', await ask(bot, '/data get entity @e[type=cow,limit=1] CustomName', /entity data|Found/), '이름표 남음', count(bot, 'name_tag'));
  await chat(bot, '/clear @s', 200); await chat(bot, '/give @s minecraft:writable_book', 400); await hold(bot, 'writable_book');
  bot._client.write('edit_book', { hand: bot.quickBarSlot, pages: ['Hello page 1', 'Page two'], title: undefined }); await sleep(600);
  console.log('RESULT 책 편집', JSON.stringify(bot.inventory.items()[0]?.components?.map(c => c.type + ':' + JSON.stringify(c.data).slice(0, 120))));
  bot._client.write('edit_book', { hand: bot.quickBarSlot, pages: ['Hello page 1', 'Page two'], title: 'My Book' }); await sleep(600);
  const wb = bot.inventory.items()[0];
  console.log('RESULT 책 서명', wb?.name, JSON.stringify(wb?.components?.map(c => c.type + ':' + JSON.stringify(c.data).slice(0, 160))));
  // 5) 금고
  await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 50.5 -60 -1.5 0 20', 600);
  await chat(bot, '/setblock 50 -60 0 minecraft:vault', 1500);
  console.log('금고 상태', JSON.stringify(bot.blockAt(new V(50, -60, 0)).getProperties()));
  await chat(bot, '/give @s minecraft:trial_key 2', 400); await hold(bot, 'trial_key');
  await bot.activateBlock(bot.blockAt(new V(50, -60, 0))); await sleep(4000);
  console.log('RESULT 금고 1회: 열쇠', count(bot, 'trial_key'), '상태', JSON.stringify(bot.blockAt(new V(50, -60, 0)).getProperties()), '나온 아이템 수', near(bot, 'item').length, '내 인벤토리', JSON.stringify(bot.inventory.items().map(i => i.name + 'x' + i.count)));
  await sleep(3000);
  await bot.activateBlock(bot.blockAt(new V(50, -60, 0))); await sleep(1500);
  console.log('RESULT 금고 2회: 열쇠', count(bot, 'trial_key'), '보상받은 플레이어', await ask(bot, '/data get block 50 -60 0 server_data.rewarded_players', /block data|Found/));
  // 6) 시련 생성기
  await chat(bot, '/kill @e[type=item]', 200); await chat(bot, '/clear @s', 200);
  await chat(bot, '/tp @s 70.5 -60 -6.5', 600);
  await chat(bot, '/setblock 70 -60 0 minecraft:trial_spawner', 300);
  console.log('RESULT 생성기 설정', await ask(bot, '/data merge block 70 -60 0 {normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}],total_mobs:2f,simultaneous_mobs:2f,ticks_between_spawn:20},target_cooldown_length:200}', /./));
  await sleep(5000);
  console.log('RESULT 다가간 뒤 생성기 상태', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '좀비', near(bot, 'zombie').length);
  await chat(bot, '/kill @e[type=zombie]', 300); await sleep(4000);
  console.log('RESULT 몹 처치 뒤 생성기 상태', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '좀비', near(bot, 'zombie').length);
  await chat(bot, '/kill @e[type=zombie]', 300); await sleep(6000);
  console.log('RESULT 다 잡은 뒤', JSON.stringify(bot.blockAt(new V(70, -60, 0)).getProperties()), '보상 아이템', JSON.stringify(near(bot, 'item').map(e => e.metadata?.[8]?.itemId ?? '?')));
  // 불길한 병
  await chat(bot, '/setblock 70 -60 0 minecraft:air', 300); await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/tp @s 90.5 -60 -20.5', 800);
  await chat(bot, '/setblock 90 -60 0 minecraft:trial_spawner', 300);
  await chat(bot, '/data merge block 90 -60 0 {normal_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:zombie"}}}]},ominous_config:{spawn_potentials:[{weight:1,data:{entity:{id:"minecraft:husk"}}}]}}', 300);
  await chat(bot, '/give @s minecraft:ominous_bottle[ominous_bottle_amplifier=1]', 400); await hold(bot, 'ominous_bottle');
  await bot.consume().catch(e => console.log('consume', e.message)); await sleep(500);
  console.log('RESULT 불길한 병 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/));
  await chat(bot, '/tp @s 90.5 -60 -6.5', 6000);
  console.log('RESULT 접근 뒤 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/), '생성기', JSON.stringify(bot.blockAt(new V(90, -60, 0)).getProperties()), '허스크', near(bot, 'husk').length, '좀비', near(bot, 'zombie').length);
  // 7) 보너스 상자
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/tp @s 120 -60 0', 600);
  console.log('RESULT 보너스 상자', await ask(bot, '/place feature minecraft:bonus_chest 120 -60 0', /./));
  await sleep(500);
  const ch = bot.findBlock({ matching: b => b.name === 'chest', maxDistance: 8 });
  console.log('RESULT 상자 위치', ch?.position, ch && await ask(bot, `/data get block ${ch.position.x} ${ch.position.y} ${ch.position.z} LootTable`, /block data|Found/));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
