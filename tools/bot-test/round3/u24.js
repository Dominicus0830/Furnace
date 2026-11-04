// 겉날개 폭죽 비행 되돌림, U4 말 재탑승 시점, U2 파괴수가 갇힌 나뭇잎을 부수는지
const U = require('../round2/u');
const { sleep, near, hold } = U;
const fs = require('fs');
const SRV = process.env.SRV;
const logLen = () => fs.readFileSync(SRV + '/server.log', 'utf8').length;
const logSince = n => fs.readFileSync(SRV + '/server.log', 'utf8').slice(n).split('\n').filter(l => /moved|floating|kicked/i.test(l));
const blockAt = (bot, x, y, z) => bot.blockAt(new (require('vec3'))(x, y, z))?.name;
(async () => {
  const bot = await U.setup('Flyer');
  let corr = 0, expect = 0;
  bot._client.on('position', () => { if (expect > 0) { expect--; return; } corr++; });
  async function tp(x, y, z, yaw = 0, pitch = 0) { expect++; bot.chat(`/tp @s ${x} ${y} ${z} ${yaw} ${pitch}`); await sleep(1200); }
  bot.chat('/gamemode survival'); bot.chat('/difficulty peaceful'); await sleep(400);
  // 1) 겉날개
  if (process.argv[2] !== 'skip-elytra') {
    bot.chat('/clear @s'); bot.chat('/give @s elytra'); bot.chat('/give @s firework_rocket 64'); bot.chat('/effect give @s resistance 300 4 true'); await sleep(800);
    const el = bot.inventory.items().find(i => i.name === 'elytra'); await bot.equip(el, 'torso'); await hold(bot, 'firework_rocket');
    const c0 = corr, l0 = logLen();
    await tp(0.5, 20, 0.5, 0, -10);
    bot.setControlState('jump', true); await sleep(100); bot.setControlState('jump', false); await sleep(400);
    try { await bot.elytraFly(); } catch (e) { console.log('elytraFly ERR', e.message); }
    await sleep(300);
    let flew = 0; const p0 = bot.entity.position.clone();
    for (let i = 0; i < 6; i++) { await bot.look(0, -0.2, true); bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(1500); if (bot.entity.elytraFlying) flew++; }
    const d = bot.entity.position.distanceTo(p0);
    const lines = logSince(l0);
    console.log(`RESULT 겉날개 폭죽: 비행 표본 ${flew}/6, 이동 ${d.toFixed(1)}칸, 되돌림 ${corr - c0}, 로그 ${lines.length} ${lines.slice(0, 3).join(' | ')}`);
    await sleep(6000);
  }
  // 2) U4 말 재탑승
  bot.chat('/clear @s'); await tp(40.5, -60, 0.5); bot.chat('/kill @e[type=!player]'); await sleep(500);
  const UUIDI = U.uuidInts(bot.player.uuid);
  bot.chat(`/summon horse 40.5 -60 3.5 {Tame:1b,NoAI:1b,Owner:${UUIDI},SaddleItem:{id:"minecraft:saddle",count:1}}`); await sleep(1000);
  const horse = near(bot, 'horse')[0];
  const mounted = () => bot.vehicle != null && bot.vehicle.id === horse.id;
  async function tryMount(ms) {   // 최대 ms 동안 250ms 마다 우클릭, 처음 탄 시간
    const t0 = Date.now();
    while (Date.now() - t0 < ms) { bot.activateEntity(horse); await sleep(250); if (mounted()) return Date.now() - t0; }
    return -1;
  }
  const first = await tryMount(2000);
  console.log('RESULT 말 첫 탑승', first);
  for (const mode of ['release', 'release', 'hold']) {
    if (!mounted()) { await sleep(3500); await tryMount(3000); }
    // 웅크려 내리기
    bot.setControlState('sneak', true); await sleep(300);
    if (mode === 'release') bot.setControlState('sneak', false);
    await sleep(200);
    const tDown = Date.now(); const off = !mounted();
    // 웅크림을 유지하면 3.5초 뒤 놓는다
    if (mode === 'hold') { await sleep(3500); bot.setControlState('sneak', false); await sleep(100); }
    const t = await tryMount(8000);
    console.log(`RESULT 말 재탑승(${mode}): 내림=${off}, 내린 뒤 ${t < 0 ? '8초 안에 못 탐' : (Date.now() - tDown - 0) + 'ms 만에 탐(시도 시작 후 ' + t + 'ms)'}`);
  }
  if (mounted()) { bot.setControlState('sneak', true); await sleep(300); bot.setControlState('sneak', false); }
  await sleep(500);
  // 3) U2 파괴수: 나뭇잎 상자에 가둔 파괴수, 바깥에 플레이어
  bot.chat('/difficulty easy'); bot.chat('/gamerule mobGriefing true'); bot.chat('/kill @e[type=!player]'); await sleep(400);
  for (const leaf of ['oak_leaves', 'birch_leaves', 'azalea_leaves']) {
    bot.chat('/fill 70 -60 -3 76 -57 3 air'); await sleep(200);
    bot.chat(`/fill 70 -60 -3 76 -57 3 ${leaf}[persistent=true] hollow`); await sleep(200);
    bot.chat('/fill 71 -60 -2 75 -58 2 air'); await sleep(200);
    bot.chat('/summon ravager 73 -60 0'); await sleep(300);
    await tp(73.5, -60, 8.5, 180);
    bot.chat('/gamemode survival'); bot.chat('/effect give @s resistance 60 4 true');
    let broke = -1; const t0 = Date.now();
    while (Date.now() - t0 < 20000) {
      await sleep(500);
      let air = 0; for (let x = 70; x <= 76; x++) for (let y = -60; y <= -58; y++) if (blockAt(bot, x, y, 3) === 'air') air++;
      if (air > 0) { broke = Date.now() - t0; break; }
    }
    const rv = near(bot, 'ravager')[0];
    console.log(`RESULT 파괴수 ${leaf}: ${broke < 0 ? '20초 동안 앞면 안 부숨' : broke + 'ms 에 앞면 부숨'}, 파괴수 위치 ${rv ? rv.position.toString() : '없음'}`);
    bot.chat('/kill @e[type=ravager]'); await sleep(400);
  }
  bot.quit(); await sleep(500); process.exit(0);
})();
