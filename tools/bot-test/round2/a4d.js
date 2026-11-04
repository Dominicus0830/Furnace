// 몹 동작: 조각 감지기 반응, 알레이, 파괴수 방패, 끈(보트·말·낙타·차원), 돼지
const { waitEnt, setup, chat, sleep, ask, num, near, count, hold, uuidInts } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s']) await chat(bot, c);
  await chat(bot, '/gamemode survival', 200); await chat(bot, '/tp @s 600.5 -60 0.5', 1000);
  // 5) 돼지
  await chat(bot, '/clear @s', 200); await chat(bot, '/summon minecraft:pig 600.5 -60 3.5', 600);
  await chat(bot, '/give @s minecraft:saddle', 300); await hold(bot, 'saddle');
  let pig = await waitEnt(bot, 'pig'); await bot.lookAt(pig.position.offset(0, 0.6, 0)); await bot.activateEntity(pig); await sleep(600);
  console.log('RESULT 돼지 안장', await ask(bot, '/data get entity @e[type=pig,limit=1] Saddle', /entity data|Found/), '안장 남음', count(bot, 'saddle'));
  await chat(bot, '/give @s minecraft:carrot_on_a_stick', 300); await hold(bot, 'carrot_on_a_stick');
  pig = near(bot, 'pig')[0]; bot.mount(pig); await sleep(1200);
  console.log('RESULT 돼지 탑승', !!bot.vehicle, bot.vehicle?.name);
  const dmg = () => bot.heldItem?.components?.find(c => c.type === 'damage')?.data ?? 0;
  bot.activateItem(); await sleep(200); bot.deactivateItem(); await sleep(400);
  console.log('RESULT 당근 낚싯대 가속 1회 손상', dmg());
  bot.activateItem(); await sleep(200); bot.deactivateItem(); await sleep(400);
  console.log('RESULT 가속 중 재클릭 손상(그대로면 1)', dmg(), '돼지 BoostTime', await ask(bot, '/data get entity @e[type=pig,limit=1]', /entity data|Found/).then(m => (m || '').match(/Boost[^,]*/)?.[0] ?? 'none'));
  bot.dismount(); await sleep(500);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
