// 몹·갑옷 거치대 장착 게임 이벤트: 조각 감지기의 last_vibration_frequency 로 본다(equip 5, unequip 4, 없음 0).
const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const SX = 4, SY = -60, SZ = 0;
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  for (const c of ['/gamerule doMobSpawning false', '/difficulty easy', '/time set midnight', '/weather clear', '/kill @e[type=!player]',
    '/tp @s 0.5 -60 0.5 0 0', '/clear @s', '/gamemode survival']) await chat(bot, c, 400);
  await sleep(1000);
  const zero = async () => chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  const fresh = async () => {
    await chat(bot, `/setblock ${SX} ${SY} ${SZ} minecraft:air`, 300);
    await chat(bot, `/setblock ${SX} ${SY} ${SZ} minecraft:sculk_sensor`, 300);
    await sleep(2500); await zero();
  };
  const freq = async () => {
    const t = Date.now();
    await chat(bot, `/data get block ${SX} ${SY} ${SZ} last_vibration_frequency`, 100);
    const m = await waitMsg(bot, /block data: (-?\d+)/, 4000, t);
    return m ? Number(m.match(/block data: (-?\d+)/)[1]) : null;
  };
  const results = [];
  const check = async (label, expect, wait = 1500) => { await sleep(wait); const f = await freq(); results.push([label, expect, f]); console.log('RESULT', label, 'expect', expect, 'got', f); };
  const entity = (type) => Object.values(bot.entities).find(e => e.name === type);
  const Z = '@e[type=zombie,limit=1]';

  // A. /item replace 로 좀비 투구 씌우기·벗기기·손에 칼
  await chat(bot, '/summon minecraft:zombie 1.5 -60 2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}', 800); await fresh();
  await chat(bot, `/item replace entity ${Z} armor.head with minecraft:iron_helmet`, 100); await check('좀비 투구 씌우기(equip)', 5);
  await sleep(2500); await zero();
  await chat(bot, `/item replace entity ${Z} armor.head with minecraft:air`, 100); await check('좀비 투구 벗기기(unequip)', 4);
  await sleep(2500); await zero();
  await chat(bot, `/item replace entity ${Z} weapon.mainhand with minecraft:iron_sword`, 100); await check('좀비 손에 칼(unequip, 장착 아이템 아님)', 4);
  await sleep(2500); await zero();
  await chat(bot, `/item replace entity ${Z} weapon.mainhand with minecraft:iron_sword`, 100); await check('같은 칼 다시(없음)', 0);
  await chat(bot, '/kill @e[type=zombie]', 500);

  await chat(bot, '/time set noon', 400);
  // B. 햇빛에 투구가 닳기(제자리 수정 — 없음). 다이아 투구라 몇 초 안에 부서지지 않는다
  await chat(bot, '/summon minecraft:zombie 1.5 -60 2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,ArmorItems:[{},{},{},{id:"minecraft:diamond_helmet",count:1}]}', 800);
  await fresh();
  await sleep(5000);
  const dmg = async () => { const t = Date.now(); await chat(bot, `/data get entity ${Z} ArmorItems[3].components."minecraft:damage"`, 100);
    const m = await waitMsg(bot, /entity data: (-?\d+)/, 3000, t); return m ? Number(m.match(/entity data: (-?\d+)/)[1]) : null; };
  const d = await dmg(); console.log('helmet damage', d);
  await check(`햇빛에 투구 닳기(없음, 피해 ${d})`, 0, 0);
  await chat(bot, '/kill @e[type=zombie]', 500);

  await chat(bot, '/time set midnight', 400);
  // C. 좀비가 떨어진 투구를 줍기(equip)
  await chat(bot, '/summon minecraft:zombie 1.5 -60 2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,CanPickUpLoot:1b}', 800); await fresh();
  await chat(bot, '/summon minecraft:item 1.5 -60 2.5 {Item:{id:"minecraft:golden_helmet",count:1},PickupDelay:0s}', 100);
  await check('좀비가 금 투구 줍기(equip)', 5, 2500);
  await chat(bot, '/kill @e[type=zombie]', 500); await chat(bot, '/kill @e[type=item]', 300);

  // D. 갑옷 거치대에 갑옷 입히기(플레이어 우클릭)
  await chat(bot, '/summon minecraft:armor_stand 1.5 -60 2.5', 800);
  await chat(bot, '/give @s minecraft:diamond_chestplate', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  const st = entity('armor_stand');
  await bot.activateEntityAt(st, st.position.offset(0, 1.2, 0));
  await check('갑옷 거치대 흉갑(equip)', 5);
  await chat(bot, '/kill @e[type=armor_stand]', 400); await chat(bot, '/kill @e[type=item]', 300); await chat(bot, '/clear @s', 400);

  console.log('SUMMARY', JSON.stringify(results));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
