// 인챈트 속성·속성 표시·발전 과제·SamplePlugin
const { setup, chat, sleep, ask, num, near, count, hold, waitEnt } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const titles = []; bot._client.on('set_title_text', p => titles.push(JSON.stringify(p.text)));
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/effect clear @s']) await chat(bot, c);
  const mods = {}; bot._client.on('entity_update_attributes', p => { if (p.entityId !== bot.entity.id) return; for (const x of p.properties) mods[x.key] = x.value + (x.modifiers.length ? '+' + JSON.stringify(x.modifiers.map(m => [m.uuid ?? m.id, m.amount, m.operation])) : ''); });
  const show = keys => keys.map(k => k + '=' + (mods[k] ?? '?')).join(' ');
  // 1) 인챈트 → 속성
  for (const [slot, item, keys] of [
    ['armor.feet', 'minecraft:diamond_boots[enchantments={levels:{"minecraft:depth_strider":3}}]', ['generic.water_movement_efficiency']],
    ['armor.head', 'minecraft:diamond_helmet[enchantments={levels:{"minecraft:respiration":3,"minecraft:aqua_affinity":1}}]', ['generic.oxygen_bonus', 'player.submerged_mining_speed']],
    ['armor.legs', 'minecraft:diamond_leggings[enchantments={levels:{"minecraft:swift_sneak":3}}]', ['player.sneaking_speed']],
  ]) {
    await chat(bot, `/item replace entity @s ${slot} with ${item}`, 800);
    console.log('RESULT 인챈트', item.split('[')[1].slice(0, 60), show(keys));
  }
  await chat(bot, '/clear @s', 600);
  console.log('RESULT 벗은 뒤', show(['generic.water_movement_efficiency', 'generic.oxygen_bonus', 'player.submerged_mining_speed', 'player.sneaking_speed']));
  // 영혼 가속: 영혼 모래 위에서 이동 속도 수정자
  await chat(bot, '/fill 10 -61 -2 14 -61 2 minecraft:soul_sand', 300);
  await chat(bot, '/item replace entity @s armor.feet with minecraft:diamond_boots[enchantments={levels:{"minecraft:soul_speed":3}}]', 300);
  await chat(bot, '/tp @s 12.5 -60 0.5', 1500);
  bot.setControlState('forward', true); await sleep(1500); bot.setControlState('forward', false); await sleep(300);
  console.log('RESULT 영혼 가속(영혼 모래 위 걸음)', show(['generic.movement_speed']), '장화 손상', await ask(bot, '/data get entity @s Inventory[{Slot:100b}].components."minecraft:damage"', /entity data|Found/));
  await chat(bot, '/tp @s 20.5 -60 0.5', 1500);
  console.log('RESULT 영혼 모래 밖', show(['generic.movement_speed']));
  // 서리 걸음
  await chat(bot, '/fill 30 -61 -5 40 -61 5 minecraft:water', 300);
  await chat(bot, '/item replace entity @s armor.feet with minecraft:diamond_boots[enchantments={levels:{"minecraft:frost_walker":2}}]', 300);
  await chat(bot, '/tp @s 29.5 -60 0.5 -90 0', 1200);
  bot.setControlState('forward', true); await sleep(2500); bot.setControlState('forward', false); await sleep(300);
  let ice = 0; for (let x = 30; x <= 40; x++) for (let z = -5; z <= 5; z++) if (bot.blockAt(new V(x, -61, z))?.name === 'frosted_ice') ice++;
  console.log('RESULT 서리 걸음: 얼음 칸', ice, '위치', bot.entity.position.x.toFixed(1));
  await chat(bot, '/clear @s', 300);
  // 2) 속성 표시
  await chat(bot, '/attribute @s minecraft:generic.max_health base set 40', 800);
  await chat(bot, '/effect give @s minecraft:instant_health 1 5', 800);
  console.log('RESULT 최대 체력 40', show(['generic.max_health']), '체력', bot.health);
  await chat(bot, '/effect give @s minecraft:speed 30 1', 800);
  console.log('RESULT 신속 II', show(['generic.movement_speed']));
  await chat(bot, '/effect give @s minecraft:health_boost 30 1', 800);
  console.log('RESULT 체력 강화 II', show(['generic.max_health']));
  await chat(bot, '/effect clear @s', 300); await chat(bot, '/attribute @s minecraft:generic.max_health base set 20', 300);
  // 3) 발전 과제: 신호기, 마법 부여, 위더 소환
  const adv = () => bot.msgs.filter(m => /advancement|challenge|goal/i.test(m.m)).map(m => m.m);
  await chat(bot, '/gamemode creative', 200);
  await chat(bot, '/tp @s 60.5 -60 -4.5', 800);
  await chat(bot, '/fill 59 -60 -1 61 -60 1 minecraft:iron_block', 300); await chat(bot, '/setblock 60 -59 0 minecraft:beacon', 4500);
  console.log('RESULT 신호기 뒤 발전 과제 메시지', JSON.stringify(adv()));
  await chat(bot, '/gamemode survival', 200);
  await chat(bot, '/setblock 70 -60 0 minecraft:enchanting_table', 300); await chat(bot, '/tp @s 70.5 -60 -1.5', 800);
  await chat(bot, '/xp add @s 30 levels', 300); await chat(bot, '/give @s minecraft:iron_sword', 300); await chat(bot, '/give @s minecraft:lapis_lazuli 10', 500);
  try {
    const et = await bot.openEnchantmentTable(bot.blockAt(new V(70, -60, 0)));
    await et.putTargetItem(bot.inventory.items().find(i => i.name === 'iron_sword')); await et.putLapis(bot.inventory.items().find(i => i.name === 'lapis_lazuli')); await sleep(800);
    console.log('enchant options', JSON.stringify(et.enchantments.map(e => e.level)));
    await et.enchant(0).catch(e => console.log('enchant err', e.message)); await sleep(600);
    const sw = await et.takeTargetItem(); et.close();
    console.log('RESULT 마법 부여 결과', JSON.stringify(sw?.components?.map(c => c.type)), '발전 과제', JSON.stringify(adv()));
  } catch (e) { console.log('enchant table err', e.message); }
  await chat(bot, '/gamemode creative', 200);
  await chat(bot, '/tp @s 90.5 -60 -6.5', 800);
  await chat(bot, '/difficulty easy', 200);
  await chat(bot, '/setblock 90 -60 0 minecraft:soul_sand', 100); await chat(bot, '/setblock 90 -59 0 minecraft:soul_sand', 100);
  await chat(bot, '/setblock 89 -59 0 minecraft:soul_sand', 100); await chat(bot, '/setblock 91 -59 0 minecraft:soul_sand', 100);
  await chat(bot, '/setblock 89 -58 0 minecraft:wither_skeleton_skull', 100); await chat(bot, '/setblock 91 -58 0 minecraft:wither_skeleton_skull', 300);
  await chat(bot, '/give @s minecraft:wither_skeleton_skull', 300); await hold(bot, 'wither_skeleton_skull');
  await bot.placeBlock(bot.blockAt(new V(90, -59, 0)), new V(0, 1, 0)).catch(e => console.log('place', e.message)); await sleep(2000);
  console.log('RESULT 위더 소환: 위더', near(bot, 'wither').length, '발전 과제', JSON.stringify(adv()));
  await chat(bot, '/kill @e[type=wither]', 300);
  // 4) SamplePlugin
  console.log('RESULT 접속 제목', JSON.stringify(titles));
  await chat(bot, '/gamemode survival', 200); await chat(bot, '/tp @s 120.5 -60 0.5', 800);
  await chat(bot, '/summon minecraft:villager 120.5 -60 2.5 {NoAI:1b}', 800);
  const vil = await waitEnt(bot, 'villager'); await bot.lookAt(vil.position.offset(0, 1.5, 0)); bot.attack(vil); await sleep(800);
  console.log('RESULT 주민 때림 → 체력(20 그대로면 취소)', await num(bot, '@e[type=villager,limit=1]', 'Health'));
  await chat(bot, '/summon minecraft:cow 121.5 -60 2.5 {NoAI:1b}', 800);
  const cw = await waitEnt(bot, 'cow'); await sleep(800); await bot.lookAt(cw.position.offset(0, 1, 0)); bot.attack(cw); await sleep(800);
  console.log('RESULT 소 때림 → 체력(10 미만이면 피해)', await num(bot, '@e[type=cow,limit=1]', 'Health'));
  await chat(bot, '/give @s minecraft:diamond', 400); await hold(bot, 'diamond');
  await bot.tossStack(bot.heldItem); await sleep(1500);
  console.log('RESULT 다이아 버림 → 5칸 위 유리', bot.blockAt(new V(120, -56, 0))?.name);
  await chat(bot, '/clear @s', 200); await chat(bot, '/give @s minecraft:bedrock', 400); await chat(bot, '/give @s minecraft:stone', 400);
  const bed = bot.inventory.items().find(i => i.name === 'bedrock'), st = bot.inventory.items().find(i => i.name === 'stone');
  await bot.clickWindow(bed.slot, 0, 0).catch(e => console.log('click', e.message)); await sleep(600);
  await bot.clickWindow(st.slot, 0, 0).catch(e => console.log('click', e.message)); await sleep(600);
  console.log('RESULT 기반암 클릭 뒤 커서', bot.inventory.selectedItem?.name ?? null, '돌 클릭 뒤 칸', JSON.stringify(bot.inventory.items().map(i => i.name + '@' + i.slot)));
  await chat(bot, '/tp @s 2000 -60 2000', 6000); await chat(bot, '/tp @s 0.5 -60 0.5', 4000);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
