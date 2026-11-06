// PR #14 pushEntitiesUp: 경작지→흙, 엔드 차원문 틀 눈, 눈 쌓임
const { setup, chat, sleep, ask, hold } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const pos = []; bot._client.on('position', p => pos.push({ y: p.y, flags: p.flags, t: Date.now() }));
  const sy = async () => { const m = await ask(bot, '/data get entity Tester Pos[1]', /data:/); return m && parseFloat(m.match(/data: (-?[\d.]+)/)[1]); };
  for (const c of ['/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/gamerule doMobSpawning false', '/effect give @s resistance 1000 4 true', '/effect give @s saturation 1000 1 true']) await chat(bot, c);
  if(0){ // 1) 경작지 무작위 틱(물 없음)
  await chat(bot, '/setblock 0 -61 0 minecraft:farmland[moisture=0]', 300);
  await chat(bot, '/tp @s 0.5 -60 0.5', 1500);
  console.log('RESULT 경작지 위 서버y', await sy(), '봇y', bot.entity.position.y);
  pos.length = 0;
  await chat(bot, '/gamerule randomTickSpeed 4096', 2000);
  await chat(bot, '/gamerule randomTickSpeed 0', 500);
  console.log('RESULT 흙 바뀜 블록', bot.blockAt(new V(0, -61, 0))?.name, '서버y', await sy(), '봇y', bot.entity.position.y, 'position 패킷', JSON.stringify(pos.map(p => p.y)));
  // 2) 점프로 밟기(높이 3 에서 떨어짐)
  await chat(bot, '/setblock 4 -61 0 minecraft:farmland[moisture=7]', 300);
  await chat(bot, '/tp @s 4.5 -57 0.5', 2500);
  console.log('RESULT 떨어진 뒤 블록', bot.blockAt(new V(4, -61, 0))?.name, '서버y', await sy(), '봇y', bot.entity.position.y);
  // 3) 엔드 차원문 틀
  await chat(bot, '/setblock 8 -61 0 minecraft:end_portal_frame[eye=false]', 300);
  await chat(bot, '/give @s minecraft:ender_eye 4', 300);
  await chat(bot, '/tp @s 8.5 -60 0.5', 1500);
  console.log('RESULT 틀 위 서버y', await sy(), '봇y', bot.entity.position.y);
  await hold(bot, 'ender_eye'); pos.length = 0;
  await bot.lookAt(new V(8.5, -60.2, 0.5), true);
  await bot.activateBlock(bot.blockAt(new V(8, -61, 0)), new V(0, 1, 0)); await sleep(1500);
  console.log('RESULT 눈 끼운 뒤', JSON.stringify(bot.blockAt(new V(8, -61, 0))?.getProperties()), '서버y', await sy(), '봇y', bot.entity.position.y, 'position 패킷', JSON.stringify(pos.map(p => p.y)));
  } // 4) 눈 쌓임
  console.log('RESULT fb', await ask(bot, '/fillbiome -16 -64 -16 15 -56 15 minecraft:snowy_plains'));
  await chat(bot, '/gamerule snowAccumulationHeight 8', 300);
  await chat(bot, '/weather rain 1000000', 300);
  await chat(bot, '/tp @s 0.0 -60 0.0', 2000); pos.length = 0;
  const snow = () => [[-1, -1], [-1, 0], [0, -1], [0, 0]].map(([x, z]) => { const b = bot.blockAt(new V(x, -60, z)); return b?.name === 'snow' ? b.getProperties().layers : 0; });
  for (let i = 1; i <= 6; i++) {
    await chat(bot, '/tick sprint 6000', 300);
    for (let k = 0; k < 60; k++) { await sleep(500); if (bot.msgs.slice(-3).some(m => /Sprint completed/i.test(m.m))) break; }
    await sleep(800);
    console.log('RESULT 눈', i * 6000, '층', JSON.stringify(snow()), '서버y', await sy(), '봇y', bot.entity.position.y.toFixed(4), 'position 패킷 수', pos.length);
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
