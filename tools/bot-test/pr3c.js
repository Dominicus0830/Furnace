const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const fs = require('fs');
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(1500);
  await chat(bot, '/tp @s 17 -55 5', 1000);
  const N = +process.argv[2] || 600;
  for (let i = 0; i < N; i++) cmd(`summon minecraft:strider ${10 + (i % 15)} -61 ${10 + Math.floor(i / 15) % 15}`);
  const log = process.env.SRV + '/server.log';
  const cnt = () => (fs.readFileSync(log, 'utf8').match(/Summoned new Strider/g) || []).length;
  const base = cnt(); let last = -1;
  while (cnt() - base < N && cnt() !== last) { last = cnt(); await sleep(3000); }
  console.log('summoned', cnt() - base);
  // 땅 위 스트라이더(AI 켜짐) 떨기 메타데이터
  await chat(bot, '/summon minecraft:strider 3 -60 3', 3000);
  const e = Object.values(bot.entities).filter(e => e.name === 'strider').sort((a, b) => a.position.distanceTo({x:3,y:-60,z:3}) - b.position.distanceTo({x:3,y:-60,z:3}))[0];
  console.log('RESULT 땅 위(AI) 스트라이더', e.position, 'metadata', JSON.stringify(e.metadata));
  for (const c of ['kill @e[type=zombified_piglin]', 'kill @e[type=strider,nbt={Age:-24000}]', 'kill @e[type=strider]']) { cmd(c); await sleep(1500); }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
