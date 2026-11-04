// 노이즈 월드: 엔더의 눈, 보너스 상자, 네더 화석, 마을 포위
const { setup, chat, sleep, ask, near, hold, waitEnt } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode creative', '/clear @s', '/gamerule doDaylightCycle false', '/time set day']) await chat(bot, c);
  const loc = await ask(bot, '/locate structure minecraft:stronghold', /located|Could not|nearest/i, 60000);
  console.log('RESULT 요새 위치', loc);
  const m = loc && loc.match(/\[(-?\d+), (~|-?\d+), (-?\d+)\]/);
  // 엔더의 눈 던지기
  await chat(bot, '/gamemode survival', 200);
  await chat(bot, '/effect give @s minecraft:resistance 600 4 true', 200);
  const p0 = bot.entity.position.clone();
  await chat(bot, '/give @s minecraft:ender_eye 3', 400); await hold(bot, 'ender_eye');
  await bot.look(0, 0, true);
  bot.activateItem(); await sleep(100); bot.deactivateItem();
  const eye = await waitEnt(bot, 'eye_of_ender', 2000);
  const e0 = eye?.position?.clone(); await sleep(1500); const e1 = eye?.position?.clone();
  if (m && e0 && e1) {
    const tx = +m[1] - p0.x, tz = +m[3] - p0.z, dx = e1.x - e0.x, dz = e1.z - e0.z;
    const ang = Math.acos((tx * dx + tz * dz) / Math.hypot(tx, tz) / Math.hypot(dx, dz)) * 180 / Math.PI;
    console.log('RESULT 엔더의 눈 이동', e0, '→', e1, '요새 방향과 각도(도)', ang.toFixed(1));
  } else console.log('RESULT 엔더의 눈 엔티티', !!eye, e0, e1);
  await sleep(3000);
  // 보너스 상자
  await chat(bot, '/gamemode creative', 200);
  const pos = bot.entity.position.floored();
  console.log('RESULT 보너스 상자 place', await ask(bot, `/place feature minecraft:bonus_chest ${pos.x} ${pos.y} ${pos.z}`, /./));
  await sleep(800);
  const ch = bot.findBlock({ matching: b => b.name === 'chest', maxDistance: 16 });
  console.log('RESULT 상자', ch?.position, ch && await ask(bot, `/data get block ${ch.position.x} ${ch.position.y} ${ch.position.z} LootTable`, /block data|Found/), ch && await ask(bot, `/data get block ${ch.position.x} ${ch.position.y} ${ch.position.z} Items`, /block data|Found/));
  // 네더 화석
  await chat(bot, '/execute in minecraft:the_nether run tp @s 0 100 0', 5000);
  const nf = await ask(bot, '/locate structure minecraft:nether_fossil', /located|Could not|nearest/i, 60000);
  console.log('RESULT 네더 화석 위치', nf);
  const fm = nf && nf.match(/\[(-?\d+), (~|-?\d+), (-?\d+)\]/);
  if (fm) {
    await chat(bot, `/tp @s ${fm[1]} 120 ${fm[3]}`, 6000);
    let bones = 0; const c = bot.entity.position.floored();
    for (let x = -12; x <= 12; x++) for (let z = -12; z <= 12; z++) for (let y = 20; y <= 110; y++) if (bot.blockAt(new V(+fm[1] + x, y, +fm[3] + z))?.name === 'bone_block') bones++;
    console.log('RESULT 화석 근처 뼈 블록 수', bones, '바이옴', await ask(bot, `/execute if biome ${fm[1]} 40 ${fm[3]} minecraft:soul_sand_valley`, /passed|failed/));
  }
  await chat(bot, '/execute in minecraft:overworld run tp @s 0 100 0', 5000);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
