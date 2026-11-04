// 재시험: 방어구 수정자, 가시(허스크 — 햇빛에 안 탐), 전투 종료, 철퇴 낙하 피해 면제
const { setup, chat, sleep, ask, num, near, hold } = require('./u');
(async () => {
  const bot = await setup();
  const attrs = [];
  bot._client.on('entity_update_attributes', p => { if (p.entityId === bot.entity.id) attrs.push(p.properties.filter(x => /armor/.test(x.key)).map(x => x.key + '=' + x.value + '+' + JSON.stringify(x.modifiers.map(m => m.amount))).join(',')); });
  const pk = {}; for (const n of ['damage_event', 'enter_combat_event', 'end_combat_event']) bot._client.on(n, p => { (pk[n] ??= []).push({ t: Date.now(), ...p }); });
  for (const c of ['/gamerule doMobSpawning false', '/gamerule naturalRegeneration false', '/time set day', '/gamemode survival', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
  attrs.length = 0; await chat(bot, '/item replace entity @s armor.chest with minecraft:iron_chestplate', 1000);
  console.log('RESULT 철 갑옷 입음', JSON.stringify(attrs));
  attrs.length = 0; await chat(bot, '/item replace entity @s armor.head with minecraft:netherite_helmet', 1000);
  console.log('RESULT 네더라이트 투구 추가', JSON.stringify(attrs));
  attrs.length = 0; await chat(bot, '/clear @s', 1000);
  console.log('RESULT 모두 벗음', JSON.stringify(attrs));
  // 가시
  await chat(bot, '/tp @s 0 -60 0 0 0', 800);
  await chat(bot, '/item replace entity @s armor.chest with minecraft:diamond_chestplate[enchantments={levels:{"minecraft:thorns":3}}]', 600);
  await chat(bot, '/effect give @s minecraft:resistance 120 4 true', 300);
  await chat(bot, '/difficulty easy', 300);
  await chat(bot, '/summon minecraft:husk 0 -60 3 {PersistenceRequired:1b}', 300);
  await sleep(15000);
  const hits = (pk.damage_event || []).filter(p => p.entityId === bot.entity.id && p.sourceTypeId === 26).length;
  console.log('RESULT 허스크가 때린 수', hits, '허스크 체력(20 시작)', await num(bot, '@e[type=husk,limit=1]', 'Health'), '갑옷', await ask(bot, '/data get entity @s Inventory[{Slot:102b}].components', /entity data|Found/));
  const huskHurt = (pk.damage_event || []).filter(p => p.entityId !== bot.entity.id).map(p => p.sourceTypeId);
  console.log('RESULT 허스크가 받은 피해 종류 id(thorns=?)', JSON.stringify(huskHurt));
  await chat(bot, '/kill @e[type=husk]', 300); const tk = Date.now();
  await sleep(17000); console.log('RESULT end_combat', JSON.stringify((pk.end_combat_event || []).map(p => ({ dt: p.t - tk, d: p.duration }))), 'enter', pk.enter_combat_event?.length);
  // 철퇴: 체력 기록 후 낙하
  await chat(bot, '/clear @s', 200); await chat(bot, '/effect clear @s', 200); await chat(bot, '/effect give @s minecraft:instant_health 1 5', 600);
  await chat(bot, '/give @s minecraft:mace', 500); await hold(bot, 'mace');
  await chat(bot, '/summon minecraft:pig 30.5 -60 30.5 {NoAI:1b,Tags:["m"],Health:100f,attributes:[{id:"minecraft:generic.max_health",base:100d}]}', 800);
  await chat(bot, '/tp @s 30.5 -50 30.5 0 90', 2000);
  console.log('RESULT 착지 전 체력', bot.health);
  await chat(bot, '/tp @s 30.5 -50 30.5 0 90', 300);
  const mp = near(bot, 'pig')[0]; let at = false;
  for (let i = 0; i < 100 && !at; i++) { await sleep(25); if (bot.entity.position.y - (mp.position.y + 0.9) < 1.6) { bot.attack(mp); at = true; } }
  await sleep(1500);
  console.log('RESULT 철퇴 돼지 피해', 100 - await num(bot, '@e[type=pig,tag=m,limit=1]', 'Health'), '낙하 뒤 체력(10칸 낙하 → 면제 아니면 7 피해)', bot.health);
  // 대조: 철퇴 없이 같은 높이
  await chat(bot, '/effect give @s minecraft:instant_health 1 5', 800); await chat(bot, '/tp @s 40.5 -50 40.5', 3000);
  console.log('RESULT 대조 철퇴 없이 10칸 낙하 체력', bot.health);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
