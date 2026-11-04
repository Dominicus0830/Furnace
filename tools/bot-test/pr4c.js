const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  const bot = makeBot('Tester'); let unloads = 0, loads = 0;
  bot._client.on('unload_chunk', () => unloads++); bot._client.on('map_chunk', () => loads++);
  await spawned(bot); await sleep(5000);
  console.log('RESULT 처음 받은 청크', loads);
  await ask(bot, '/vd 2', /PROBE/); await sleep(5000);
  console.log('RESULT vd 2 후 5초(가만히): unload', unloads);
  await chat(bot, '/tp @s 1 -60 1', 3000);
  console.log('RESULT 1칸 이동 후: unload', unloads);
  await chat(bot, '/tp @s 40 -60 1', 4000);
  console.log('RESULT 청크 경계 넘어 이동 후: unload', unloads, 'loads', loads);
  const l1 = loads; await ask(bot, '/vd 6', /PROBE/); await sleep(5000);
  console.log('RESULT vd 6 후 5초(가만히): 새 로드', loads - l1);
  await chat(bot, '/tp @s 0 -60 1', 4000);
  console.log('RESULT 이동 후: 새 로드', loads - l1);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
