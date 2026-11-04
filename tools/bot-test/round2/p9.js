// PR #9: 펫 사다리·덩굴 낙하 사망 메시지, 다락문+사다리 칸 몹 하강 속도
const { setup, chat, sleep, ask, uuidInts, waitEnt } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const own = uuidInts(bot.player.uuid);
  for (const c of ['/gamemode creative', '/kill @e[type=!player]', '/gamerule doMobSpawning false', '/time set day']) await chat(bot, c);
  // 1) 하강 속도: x=20 기둥, 북쪽 벽(z=-1) 에 사다리·다락문 번갈아 y=-60..-31
  await chat(bot, '/fill 19 -61 -1 21 -30 1 minecraft:stone', 500);
  await chat(bot, '/fill 20 -60 0 20 -30 0 minecraft:air', 500);
  for (let y = -60; y <= -31; y++) await chat(bot, `/setblock 20 ${y} 0 ` + ((y + 60) % 2 === 0 ? 'minecraft:ladder[facing=south]' : 'minecraft:oak_trapdoor[facing=south,open=true]'), 60);
  await chat(bot, '/tp @s 23.5 -45 0.5', 1500);
  await chat(bot, '/fill 20 -45 1 20 -45 1 minecraft:glass', 300); // 창
  await chat(bot, `/fill 20 -60 1 20 -31 1 minecraft:glass`, 300);
  await chat(bot, '/tp @s 20.5 -45 2.5', 1500);
  await chat(bot, '/summon minecraft:wolf 20.5 -31 0.5', 300);
  const pig = await waitEnt(bot, 'wolf'); const t0 = Date.now(); const tr = [];
  while (Date.now() - t0 < 12000) { tr.push([(Date.now() - t0) / 1000, pig.position.y]); if (pig.position.y <= -59.99) break; await sleep(250); }
  console.log('RESULT 늑대 하강', JSON.stringify(tr.filter((_, i) => i % 4 === 0).map(([t, y]) => [t.toFixed(1), y.toFixed(2)])), '바닥까지', ((Date.now() - t0) / 1000).toFixed(1), '초');
  // 2) 펫 사다리 낙하 사망: x=40 기둥 꼭대기 y=20 사다리 1칸, 아래로 80칸 낙하
  await chat(bot, '/fill 40 -60 -1 40 21 -1 minecraft:stone', 500);
  await chat(bot, '/setblock 40 20 0 minecraft:ladder[facing=south]', 200);
  await chat(bot, '/fill 50 -60 -1 50 21 -1 minecraft:stone', 500);
  await chat(bot, '/setblock 50 20 0 minecraft:vine[north=true]', 200);
  await chat(bot, '/tp @s 45.5 -60 5.5', 1500);
  const msgs0 = bot.msgs.length;
  await chat(bot, `/summon minecraft:wolf 40.5 20 0.5 {CustomName:'"Rex"',Owner:${own},Tame:1b,Sitting:1b}`, 300);
  await chat(bot, `/summon minecraft:cat 50.5 20 0.5 {CustomName:'"Tom"',Owner:${own},Tame:1b,Sitting:1b}`, 300);
  await sleep(8000);
  console.log('RESULT 메시지', JSON.stringify(bot.msgs.slice(msgs0).map(m => m.m).filter(m => /Rex|Tom/.test(m))));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
