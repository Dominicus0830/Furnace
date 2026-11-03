const { setup, chat, sleep, ask, hold, waitEnt } = require('./u');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode survival', '/clear @s', '/tp @s 99.5 140 -24.5']) await chat(bot, c, 800);
  const res = [];
  for (const yaw of [0, Math.PI / 2, Math.PI]) {
    const p0 = bot.entity.position.clone();
    await chat(bot, '/give @s minecraft:ender_eye 1', 400); await hold(bot, 'ender_eye');
    await bot.look(yaw, 0, true);
    bot.activateItem(); await sleep(100); bot.deactivateItem();
    const eye = await waitEnt(bot, 'eye_of_ender', 2000);
    const e0 = eye.position.clone(); await sleep(1500); const e1 = eye.position.clone();
    const tx = 1760 - p0.x, tz = 368 - p0.z, dx = e1.x - e0.x, dz = e1.z - e0.z;
    res.push(`시선 yaw ${(yaw * 180 / Math.PI).toFixed(0)}°: 이동 (${dx.toFixed(2)}, ${dz.toFixed(2)}), 요새 방향과 각 ${(Math.acos((tx * dx + tz * dz) / Math.hypot(tx, tz) / Math.hypot(dx, dz)) * 180 / Math.PI).toFixed(1)}°`);
    await sleep(3500);
  }
  console.log('RESULT 엔더의 눈', res.join(' | '));
  bot.quit(); await sleep(300); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
