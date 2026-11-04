// 방어구 표시·가시·힘/나약함/살충·피격 화면·철퇴
const { setup, chat, sleep, ask, num, near, count, hold } = require('./u');
(async () => {
  const bot = await setup();
  const myId = () => bot.entity.id;
  const attrs = [];
  bot._client.on('entity_update_attributes', p => { if (p.entityId === myId()) attrs.push(p.properties.map(x => x.key + '=' + x.value).join(',')); });
  const pk = {}; for (const n of ['hurt_animation', 'damage_event', 'enter_combat_event', 'end_combat_event', 'death_combat_event']) bot._client.on(n, p => { (pk[n] ??= []).push(p); });
  for (const c of ['/gamerule doMobSpawning false', '/gamerule naturalRegeneration false', '/time set day', '/gamemode survival', '/clear @s']) await chat(bot, c);
  // 1) 방어구 표시
  attrs.length = 0;
  await chat(bot, '/item replace entity @s armor.chest with minecraft:iron_chestplate', 1000);
  console.log('RESULT 갑옷 입은 뒤 속성 패킷', JSON.stringify(attrs));
  attrs.length = 0;
  await chat(bot, '/item replace entity @s armor.chest with minecraft:air', 1000);
  console.log('RESULT 갑옷 벗은 뒤 속성 패킷', JSON.stringify(attrs));
  await chat(bot, '/item replace entity @s armor.head with minecraft:diamond_helmet', 600);
  attrs.length = 0;
  await chat(bot, '/kill @s', 1500); bot.respawn?.(); await sleep(2500);
  console.log('RESULT 리스폰 뒤 속성 패킷(투구는 사망으로 떨어짐)', JSON.stringify(attrs));
  await chat(bot, '/item replace entity @s armor.legs with minecraft:diamond_leggings', 600);
  await chat(bot, '/kill @e[type=item]', 300);
  // 2) 피격 화면 + 가시
  await chat(bot, '/tp @s 0 -60 0 0 0', 800);
  await chat(bot, '/item replace entity @s armor.chest with minecraft:diamond_chestplate[enchantments={levels:{"minecraft:thorns":3}}]', 600);
  await chat(bot, '/effect give @s minecraft:resistance 60 3 true', 300);
  await chat(bot, '/difficulty easy', 300);
  await chat(bot, '/summon minecraft:zombie 0 -60 3 {PersistenceRequired:1b}', 300);
  const t0 = Date.now(); let hp0 = 20;
  await sleep(12000);
  console.log('RESULT 좀비 체력(최대 20, 12초 맞은 뒤)', await num(bot, '@e[type=zombie,limit=1]', 'Health'));
  console.log('RESULT 가시 갑옷 손상', await ask(bot, '/data get entity @s Inventory[{Slot:102b}].components', /entity data|Found/));
  console.log('RESULT hurt_animation', pk.hurt_animation?.length, JSON.stringify(pk.hurt_animation?.slice(0, 2)), 'damage_event', pk.damage_event?.length, JSON.stringify(pk.damage_event?.slice(0, 2)), 'enter_combat', pk.enter_combat_event?.length);
  await chat(bot, '/kill @e[type=zombie]', 300); await chat(bot, '/effect clear @s', 300);
  await sleep(6000); console.log('RESULT end_combat 수', pk.end_combat_event?.length, JSON.stringify(pk.end_combat_event));
  // 3) 힘·나약함·살충 (돼지 체력 100, 방어 0)
  await chat(bot, '/tp @s 20 -60 0 0 0', 800);
  const pig = async (tag) => { await chat(bot, `/summon minecraft:pig 20 -60 2 {NoAI:1b,Tags:["${tag}"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}`, 800); };
  async function hit(tag, item, effect) {
    await chat(bot, '/clear @s', 200); await chat(bot, '/effect clear @s', 200);
    if (effect) await chat(bot, `/effect give @s ${effect} 60 ${effect.endsWith('strength') ? 1 : 0}`, 200);
    if (item) { await chat(bot, `/give @s ${item}`, 500); await hold(bot, item.split('[')[0].replace('minecraft:', '')); } else bot.setQuickBarSlot(8);
    await pig(tag); const e = near(bot, 'pig')[0]; await bot.lookAt(e.position.offset(0, 0.5, 0)); await sleep(1300);
    bot.attack(e); await sleep(700);
    const h = await num(bot, `@e[type=pig,tag=${tag},limit=1]`, 'Health'); await chat(bot, `/kill @e[type=pig]`, 300); return 100 - h;
  }
  console.log('RESULT 맨손 피해', await hit('p1'));
  console.log('RESULT 힘 II 맨손 피해(기대 7)', await hit('p2', null, 'minecraft:strength'));
  console.log('RESULT 나약함 I 맨손 피해(기대 0)', await hit('p3', null, 'minecraft:weakness'));
  console.log('RESULT 철검 피해(기대 6)', await hit('p4', 'minecraft:iron_sword'));
  // 살충(거미, 방어 0): 철검 6 + 살충 V 12.5 = 18.5, 느림 IV
  await chat(bot, '/clear @s', 200); await chat(bot, '/effect clear @s', 200);
  await chat(bot, '/give @s minecraft:iron_sword[enchantments={levels:{"minecraft:bane_of_arthropods":5}}]', 500); await hold(bot, 'iron_sword');
  await chat(bot, '/summon minecraft:spider 20 -60 2 {NoAI:1b,Tags:["sp"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}', 800);
  let sp = near(bot, 'spider')[0]; await bot.lookAt(sp.position.offset(0, 0.4, 0)); await sleep(1300); bot.attack(sp); await sleep(700);
  console.log('RESULT 살충 V 거미 피해(기대 18.5)', 100 - await num(bot, '@e[type=spider,limit=1]', 'Health'), '효과', await ask(bot, '/data get entity @e[type=spider,limit=1] active_effects', /entity data|Found/));
  await chat(bot, '/kill @e[type=spider]', 300);
  // 4) 철퇴: 돼지 위 6칸에서 떨어지며 내려침
  await chat(bot, '/clear @s', 200); await chat(bot, '/effect clear @s', 200);
  await chat(bot, '/effect give @s minecraft:resistance 60 4 true', 200);
  await chat(bot, '/give @s minecraft:mace', 500); await hold(bot, 'mace');
  await chat(bot, '/summon minecraft:pig 30.5 -60 30.5 {NoAI:1b,Tags:["m"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}', 800);
  await chat(bot, '/summon minecraft:zombie 32.5 -60 30.5 {NoAI:1b,Tags:["z"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}', 600);
  await chat(bot, '/tp @s 30.5 -52 30.5 0 90', 600);
  const mp = near(bot, 'pig')[0];
  let fallStartY = bot.entity.position.y, attacked = false;
  for (let i = 0; i < 100 && !attacked; i++) { await sleep(25); if (bot.entity.position.y - (mp.position.y + 0.9) < 1.6) { bot.attack(mp); attacked = true; } }
  console.log('RESULT 철퇴 공격 시 y', bot.entity.position.y.toFixed(2), '시작 y', fallStartY);
  await sleep(1200);
  console.log('RESULT 철퇴 낙하 공격 돼지 피해', 100 - await num(bot, '@e[type=pig,tag=m,limit=1]', 'Health'), '옆 좀비 피해(충격파 넉백만, 피해 없음)', 100 - await num(bot, '@e[type=zombie,tag=z,limit=1]', 'Health'));
  console.log('RESULT 철퇴 사용자 체력(낙하 피해 면제면 20)', bot.health, '철퇴 손상', JSON.stringify(bot.heldItem?.components?.find(c => c.type === 'damage')));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
