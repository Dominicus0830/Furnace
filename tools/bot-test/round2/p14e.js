const { setup, chat, sleep, ask } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const pos = []; bot._client.on('position', p => pos.push(p.y));
  const sy = async () => { const m = await ask(bot, '/data get entity Tester Pos[1]', /data:/); return m && parseFloat(m.match(/data: (-?[\d.]+)/)[1]); };
  await chat(bot, '/gamemode survival', 300);
  await chat(bot, '/effect give @s resistance 1000 4 true', 300);
  await chat(bot, '/fill -8 -60 -8 8 -59 8 minecraft:air', 500);
  await chat(bot, '/weather rain', 300);
  await chat(bot, '/gamerule snowAccumulationHeight 8', 300);
  await chat(bot, '/tp @s 0.0 -60 0.0', 2000); pos.length = 0;
  const snow = () => [[-1, -1], [-1, 0], [0, -1], [0, 0]].map(([x, z]) => { const b = bot.blockAt(new V(x, -60, z)); return b?.name === 'snow' ? b.getProperties().layers : 0; });
  await chat(bot, '/gamerule randomTickSpeed 200', 300);
  for (let i = 1; i <= 10; i++) {
    await sleep(3000);
    console.log('RESULT', i * 3, '초 층', JSON.stringify(snow()), '서버y', await sy(), '봇y', bot.entity.position.y.toFixed(4), 'position 패킷', JSON.stringify(pos));
  }
  await chat(bot, '/gamerule randomTickSpeed 3', 300);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
