// 게임 이벤트(진동) 누락분: 조각 감지기의 last_vibration_frequency 로 어떤 이벤트가 닿았는지 본다.
// 빈도: container_open 10, container_close 9, entity_mount 6, entity_dismount 5, equip 5, eat 8, item_interact_finish 3, splash 2
const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const SX = 4, SY = -60, SZ = 0;
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  for (const c of ['/gamerule doMobSpawning false', '/difficulty peaceful', '/time set day', '/kill @e[type=!player]',
    '/tp @s 0.5 -60 0.5 0 0', '/clear @s', '/gamemode survival']) await chat(bot, c, 400);
  await sleep(1000);
  const fresh = async () => {   // 감지기를 새로 놓아 빈도 0 으로
    await chat(bot, `/setblock ${SX} ${SY} ${SZ} minecraft:air`, 300);
    await chat(bot, `/setblock ${SX} ${SY} ${SZ} minecraft:sculk_sensor`, 300);
    await sleep(2500);   // 놓을 때의 진동·쿨다운이 끝나게
    await chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  };
  const freq = async () => {
    const t = Date.now();
    await chat(bot, `/data get block ${SX} ${SY} ${SZ} last_vibration_frequency`, 100);
    const m = await waitMsg(bot, /block data: (-?\d+)/, 4000, t);
    return m ? Number(m.match(/block data: (-?\d+)/)[1]) : null;
  };
  const results = [];
  const check = async (label, expect) => { await sleep(1500); const f = await freq(); results.push([label, expect, f]); console.log('RESULT', label, 'expect', expect, 'got', f); };
  const entity = (type) => Object.values(bot.entities).find(e => e.name === type);

  // A. 상자 수레 열기·닫기
  await chat(bot, '/summon minecraft:chest_minecart 1.5 -60 2.5', 500); await fresh();
  const cart = entity('chest_minecart');
  bot.activateEntity(cart); await check('상자 수레 열기(container_open)', 10);
  await sleep(2500); await chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await check('상자 수레 닫기(container_close)', 9);
  await chat(bot, '/kill @e[type=chest_minecart]', 500);

  // B. 배 타기·내리기
  await chat(bot, '/summon minecraft:boat 1.5 -60 2.5', 500); await fresh();
  const boat = entity('boat');
  bot.mount(boat); await check('배 타기(entity_mount)', 6);
  await sleep(2500); await chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  bot.dismount(); await check('배 내리기(entity_dismount)', 5);
  await chat(bot, '/kill @e[type=boat]', 500); await chat(bot, '/tp @s 0.5 -60 0.5 0 0', 800);

  // C. 방패 들기 → 내리기(item_interact_finish)
  await chat(bot, '/give @s minecraft:shield', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  bot.activateItem(); await sleep(3000);
  await chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  bot.deactivateItem(); await check('방패 내리기(item_interact_finish)', 3);
  await chat(bot, '/clear @s', 400);

  // D. 낚싯대 던지기 → 감기
  await chat(bot, '/give @s minecraft:fishing_rod', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  bot.activateItem(); await sleep(3000);
  await chat(bot, `/data merge block ${SX} ${SY} ${SZ} {last_vibration_frequency:0}`, 300);
  bot.activateItem(); await check('낚싯대 감기(item_interact_finish)', 3);
  await chat(bot, '/kill @e[type=fishing_bobber]', 300); await chat(bot, '/clear @s', 400);

  // E. 갑옷 우클릭 장착
  await chat(bot, '/give @s minecraft:iron_helmet', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  bot.activateItem(); await check('철 투구 우클릭 장착(equip)', 5);
  await chat(bot, '/clear @s', 400);

  // F. 돼지 안장
  await chat(bot, '/summon minecraft:pig 1.5 -60 2.5 {NoAI:1b}', 500);
  await chat(bot, '/give @s minecraft:saddle', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  bot.activateEntity(entity('pig')); await check('돼지 안장(equip)', 5);
  await chat(bot, '/kill @e[type=pig]', 400); await chat(bot, '/clear @s', 400);

  // G. 먹기(배불러도 먹을 수 있는 황금 사과)
  await chat(bot, '/give @s minecraft:golden_apple 4', 600); await fresh();
  bot.setQuickBarSlot(0); await sleep(300);
  try { await bot.consume(); } catch (e) { console.log('consume', e.message); }
  await check('황금 사과 먹기(eat)', 8);
  await chat(bot, '/clear @s', 400);

  // H. 물에 뛰어들기(splash)
  await chat(bot, '/fill 0 -61 5 2 -61 7 minecraft:water', 500); await fresh();
  await chat(bot, '/tp @s 1.5 -55 6.5', 100); await check('물에 떨어지기(splash)', 2);

  console.log('SUMMARY', JSON.stringify(results));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
