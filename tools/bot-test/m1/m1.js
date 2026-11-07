// M1: 영혼 가속 입자·소리, 착지 블록 입자, 스폰 알 물에 쓰기를 패킷·엔티티로 확인한다.
const { makeBot, spawned, chat, sleep } = require('../lib');
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  require('fs').appendFileSync(process.env.SRV + '/cmds', 'op Tester\n'); await sleep(800);
  const parts = [], sounds = [];
  bot._client.on('world_particles', p => parts.push({ t: Date.now(), count: p.amount ?? p.count, id: p.particleId ?? p.particle?.type, x: p.x, y: p.y, z: p.z, dx: p.offsetX, dy: p.offsetY, dz: p.offsetZ, speed: p.velocityOffset ?? p.maxSpeed, raw: p }));
  bot._client.on('sound_effect', p => sounds.push({ t: Date.now(), p }));
  for (const c of ['/gamerule doMobSpawning false', '/difficulty peaceful', '/time set day', '/kill @e[type=!player]', '/clear @s', '/gamemode survival']) await chat(bot, c, 400);
  const R = [];
  const res = (name, ok, extra = '') => { R.push(ok); console.log('RESULT', ok ? 'PASS' : 'FAIL', name, extra); };

  // A. 착지 블록 입자: 13칸 높이에서 떨어진다
  await chat(bot, '/tp @s 0.5 -47 0.5', 600); await sleep(300);
  parts.length = 0;
  await sleep(5000);
  const big = parts.filter(x => x.count > 50);
  console.log('A particles', parts.length, JSON.stringify(parts.slice(0, 3).map(({ raw, ...r }) => r)));
  res('착지 블록 입자(count>50, 속도 0.15)', big.length >= 1 && Math.abs((big[0].speed || 0) - 0.15) < 1e-3, JSON.stringify(big[0] && { count: big[0].count, y: big[0].y }));

  // B. 영혼 가속: 영혼 모래 위를 걷는다
  await chat(bot, '/tp @s 0.5 -60 0.5 -90 0', 600);
  await chat(bot, '/fill -2 -61 -2 12 -61 2 minecraft:soul_sand', 800);
  await chat(bot, '/give @s minecraft:iron_boots[enchantments={levels:{"minecraft:soul_speed":3}}]', 600);
  const boots = bot.inventory.items().find(i => i.name === 'iron_boots');
  if (boots) await bot.equip(boots, 'feet');
  await sleep(500);
  parts.length = 0; sounds.length = 0;
  bot.setControlState('forward', true); await sleep(4000); bot.setControlState('forward', false);
  const soul = parts.filter(x => x.count === 0);
  console.log('B count0 particles', soul.length, JSON.stringify(soul.slice(0, 3).map(({ raw, ...r }) => r)));
  console.log('B sounds', sounds.length, JSON.stringify(sounds.slice(0, 2).map(s => s.p.soundEvent ?? s.p)));
  res('영혼 입자(count 0, 속도 1.0)', soul.length >= 1 && Math.abs((soul[0].speed || 0) - 1.0) < 1e-3, `n=${soul.length}`);
  res('영혼 소리 1번 이상', sounds.length >= 1, `n=${sounds.length}`);
  parts.length = 0;
  await sleep(1500);
  res('가만히 서면 영혼 입자 없음', parts.filter(x => x.count === 0).length === 0);

  // C. 스폰 알을 물에
  await chat(bot, '/tp @s 0.5 -60 8.5 0 90', 600);
  await chat(bot, '/setblock 0 -61 8 minecraft:water', 600);
  await chat(bot, '/give @s minecraft:cow_spawn_egg', 600);
  const egg = bot.inventory.items().find(i => i.name === 'cow_spawn_egg');
  if (egg) await bot.equip(egg, 'hand');
  await bot.look(0, -Math.PI / 2, true); await sleep(300);
  const cows0 = Object.values(bot.entities).filter(e => e.name === 'cow').length;
  bot.activateItem(); await sleep(1500);
  const cows1 = Object.values(bot.entities).filter(e => e.name === 'cow').length;
  res('물 원천에 스폰 알 → 소 1마리', cows1 === cows0 + 1, `${cows0}->${cows1}`);
  console.log(R.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.end(); process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
