const { chat, sleep, ask, near, hold, makeBot, spawned, cmd } = require('./u');
const V = require('vec3');
(async () => {
  const bot = makeBot('Tester', { plugins: { resource_pack: false } });
  const titles = []; bot._client.on('set_title_text', p => titles.push(JSON.stringify(p.text)));
  const props = []; bot._client.on('craft_progress_bar', p => props.push([p.property, p.value]));
  await spawned(bot); await sleep(2000); cmd('op Tester'); await sleep(800);
  console.log('RESULT 접속 제목', JSON.stringify(titles));
  for (const c of ['/gamemode survival', '/clear @s', '/kill @e[type=!player]']) await chat(bot, c);
  // 깊이 보호(-60 아래 부수기 취소)
  await chat(bot, '/tp @s 150.5 -60 0.5', 800); await chat(bot, '/give @s minecraft:diamond_shovel', 400); await hold(bot, 'diamond_shovel');
  const t = Date.now(); await bot.dig(bot.blockAt(new V(150, -61, 1)), true).catch(e => console.log('dig', e.message)); await sleep(800);
  console.log('RESULT y-61 부수기 뒤 블록', bot.blockAt(new V(150, -61, 1)).name, '메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t).map(m => m.m)));
  // 마법 부여(창 직접 조작)
  await chat(bot, '/clear @s', 200);
  await chat(bot, '/setblock 70 -60 0 minecraft:enchanting_table', 300); await chat(bot, '/tp @s 70.5 -60 -1.5', 800);
  await chat(bot, '/xp add @s 30 levels', 300); await chat(bot, '/give @s minecraft:iron_sword', 300); await chat(bot, '/give @s minecraft:lapis_lazuli 10', 500);
  const win = await bot.openBlock(bot.blockAt(new V(70, -60, 0))); await sleep(500);
  console.log('창', win.type, win.slots.length);
  const sw = win.slots.findIndex((s, i) => i >= 2 && s?.name === 'iron_sword'), lp = win.slots.findIndex((s, i) => i >= 2 && s?.name === 'lapis_lazuli');
  await bot.clickWindow(sw, 0, 0); await bot.clickWindow(0, 0, 0); await sleep(300);
  await bot.clickWindow(lp, 0, 0); await bot.clickWindow(1, 0, 0); await sleep(1000);
  console.log('RESULT 창 속성(비용·인챈트 id)', JSON.stringify(props));
  bot._client.write('enchant_item', { windowId: win.id, enchantment: 0 }); await sleep(800);
  console.log('RESULT 마법 부여 뒤 칸 0', JSON.stringify(win.slots[0]?.components?.map(c => c.type + JSON.stringify(c.data).slice(0, 60))), '레벨', bot.experience.level);
  bot.closeWindow(win); await sleep(500);
  console.log('RESULT 발전 과제 메시지', JSON.stringify(bot.msgs.filter(m => /advancement/.test(m.m)).map(m => m.m)));
  // 위더 소환(마지막 해골을 봇이 놓음)
  await chat(bot, '/gamemode creative', 200); await chat(bot, '/difficulty easy', 200);
  await chat(bot, '/tp @s 90.5 -60 -3.5', 800);
  for (const c of ['/setblock 90 -60 0 minecraft:soul_sand', '/setblock 90 -59 0 minecraft:soul_sand', '/setblock 89 -59 0 minecraft:soul_sand', '/setblock 91 -59 0 minecraft:soul_sand', '/setblock 89 -58 0 minecraft:wither_skeleton_skull', '/setblock 91 -58 0 minecraft:wither_skeleton_skull']) await chat(bot, c, 150);
  await chat(bot, '/give @s minecraft:wither_skeleton_skull', 300); await hold(bot, 'wither_skeleton_skull');
  await bot.lookAt(new V(90.5, -58, 0.5)); await sleep(200);
  bot._client.write('block_place', { hand: 0, location: { x: 90, y: -59, z: 0 }, direction: 1, cursorX: 0.5, cursorY: 1, cursorZ: 0.5, insideBlock: false, sequence: 99 }); await sleep(2500);
  console.log('RESULT 위더', near(bot, 'wither').length, '발전 과제', JSON.stringify(bot.msgs.filter(m => /advancement/.test(m.m)).map(m => m.m)));
  await chat(bot, '/kill @e[type=wither]', 300);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
