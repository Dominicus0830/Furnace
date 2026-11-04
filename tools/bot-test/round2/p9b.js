const { setup, chat, sleep, ask, waitEnt } = require('./u');
(async () => {
  const bot = await setup();
  await chat(bot, '/tp @s 23.5 -60 2.5', 1500);
  await chat(bot, '/summon minecraft:pig 25.5 -40 2.5', 300);
  const pig = await waitEnt(bot, 'pig');
  for (let i = 0; i < 6; i++) { console.log('RESULT 열린 공중 돼지 y', pig.position.y.toFixed(2), await ask(bot, '/data get entity @e[type=pig,limit=1,sort=nearest] Pos[1]', /data:|No/)); await sleep(500); }
  console.log('RESULT 기둥', await ask(bot, '/data get entity @e[type=pig,limit=1,x=20,y=-31,z=0,distance=..3] Pos', /data:|No|Found/));
  console.log('RESULT 늑대', await ask(bot, '/data get entity @e[type=wolf,limit=1] Pos', /data:|No|Found/));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
