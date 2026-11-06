// 양조 발전 과제(Local Brewery, nether/brew_potion): 양조기에 물병·네더 사마귀·블레이즈 가루를 넣고, 다 되면 꺼낸다
const U = require('../round2/u'); const { sleep, chat, waitMsg } = U; const V = require('vec3');
(async () => {
  const bot = await U.setup('Brewer');
  for (const c of ['/gamemode survival', '/difficulty peaceful', '/clear @s', '/advancement revoke @s everything', '/tp @s 150.5 -60 0.5', '/setblock 150 -60 2 brewing_stand',
    '/give @s potion[potion_contents={potion:"minecraft:water"}] 3', '/give @s nether_wart 1', '/give @s blaze_powder 1']) await chat(bot, c, 250);
  await sleep(800);
  const w = await bot.openBlock(bot.blockAt(new V(150, -60, 2)));
  console.log('RESULT 창', w.type, w.title, '칸 수', w.slots.length);
  const inv = n => w.slots.findIndex((s, i) => i >= 5 && s && s.name === n);
  for (const dst of [0, 1, 2]) { const src = inv('potion'); await bot.clickWindow(src, 1, 0); await bot.clickWindow(dst, 1, 0); }   // 오른쪽 클릭으로 하나씩
  await bot.clickWindow(inv('blaze_powder'), 0, 0); await bot.clickWindow(4, 0, 0);
  await bot.clickWindow(inv('nether_wart'), 0, 0); await bot.clickWindow(3, 0, 0);
  await sleep(500);
  console.log('RESULT 넣은 뒤', w.slots.slice(0, 5).map(s => s ? s.name + 'x' + s.count : '-').join(' '));
  const t = Date.now(); await chat(bot, '/tick rate 200', 200);
  for (let i = 0; i < 100; i++) { await sleep(200); if (!w.slots[3]) break; }
  await chat(bot, '/tick rate 20', 200);
  console.log('RESULT 양조 끝', ((Date.now() - t) / 1000).toFixed(1), '초, 칸', w.slots.slice(0, 5).map(s => s ? s.name + JSON.stringify(s.components?.map(c => c.type)) : '-').join(' '));
  const since = Date.now(); await bot.clickWindow(0, 0, 1);   // shift 클릭으로 꺼내기
  const m = await waitMsg(bot, /advancement|Local Brewery/i, 3000, since);
  console.log('RESULT 꺼낸 뒤 발전 과제 메시지', m);
  bot.closeWindow(w); bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
