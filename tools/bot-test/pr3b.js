const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const V = require('vec3');
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(1500);
  await chat(bot, '/kill @e[type=!player]', 800);
  await chat(bot, '/tp @s 2 -60 2', 800);
  // 떨기 메타데이터
  await chat(bot, '/summon minecraft:strider 4 -60 4 {NoAI:1b}', 1500);
  await chat(bot, '/summon minecraft:strider 15 -61 15 {NoAI:1b}', 1500);
  await sleep(1500);
  for (const e of Object.values(bot.entities).filter(e => e.name === 'strider')) console.log('RESULT 스트라이더', e.position, 'metadata', JSON.stringify(e.metadata));
  // 안장 드롭(땅 위)
  await chat(bot, '/summon minecraft:strider 6 -60 2 {NoAI:1b,Saddle:1b}', 1200);
  console.log('RESULT Saddle 저장값', await ask(bot, '/data get entity @e[type=strider,limit=1,sort=nearest,x=6,y=-60,z=2,distance=..1] Saddle', /Strider|Saddle|has|Found/i));
  await chat(bot, '/kill @e[type=strider,limit=1,sort=nearest,x=6,y=-60,z=2,distance=..1]', 2500);
  const drops = Object.values(bot.entities).filter(e => e.name === 'item').map(e => { try { return e.getDroppedItem()?.name } catch { return '?' } });
  console.log('RESULT 처치 후 바닥 아이템', JSON.stringify(drops));
  await chat(bot, '/kill @e[type=!player]', 800);
  bot.quit(); await sleep(500);
  // 기수 확률: 콘솔에서 용암 위로 600마리 소환
  for (let i = 0; i < 600; i++) cmd(`summon minecraft:strider ${10 + (i % 15)} -61 ${10 + Math.floor(i / 40) % 15} {NoAI:1b}`);
  await sleep(15000);
  const t = Date.now();
  cmd('execute store result score #z t run kill @e[type=zombified_piglin]');
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
