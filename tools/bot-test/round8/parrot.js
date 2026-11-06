// 앵무새 어깨: 봇 어깨에 앉힌 뒤 콘솔(전역 스레드)의 /gamemode spectator·/damage 로 내려놓으면 앵무새가 봇 자리에 다시 선다.
const U = require('../round2/u.js');
const { cmd, sleep } = U;
async function sitOnShoulder(bot) {
  cmd(`summon parrot ${bot.entity.position.x} ${bot.entity.position.y} ${bot.entity.position.z} {Owner:${U.uuidInts(bot.player.uuid)},Tame:1b,PersistenceRequired:1b}`);
  for (let i = 0; i < 60; i++) {
    cmd('tp @e[type=parrot] Tester');
    await sleep(250);
    const m = await U.ask(bot, '/data get entity Tester ShoulderEntityLeft.id', /entity data|has the following|Found no/i, 1500);
    if (m && /parrot/.test(m)) return true;
  }
  return false;
}
async function parrots() {
  const fs = require('fs');
  const before = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').length;
  cmd('execute if entity @e[type=parrot]');
  await sleep(800);
  const tail = fs.readFileSync(process.env.SRV + '/server.log', 'utf8').slice(before);
  const m = tail.match(/Test passed, count: (\d+)|Test failed/);
  return m ? (m[1] ? +m[1] : 0) : -1;
}
(async () => {
  const bot = await U.setup('Tester');
  cmd('gamemode survival Tester'); cmd('difficulty peaceful'); cmd('gamerule doMobSpawning false'); cmd('kill @e[type=!player]');
  bot.chat('/tp 8.5 -60 8.5'); await sleep(2000);
  const res = {};
  res.sat1 = await sitOnShoulder(bot);
  res.parrotsWhileSitting = await parrots();
  await sleep(1500);   // 앉은 지 20틱이 지나야 내려놓는다
  cmd('gamemode spectator Tester'); await sleep(800);
  res.shoulderAfterSpectator = await U.ask(bot, '/data get entity Tester ShoulderEntityLeft', /entity data|has the following|Found no/i);
  res.parrotsAfterSpectator = await parrots();
  const p = U.near(bot, 'parrot')[0];
  res.parrotDist = p ? +p.position.distanceTo(bot.entity.position).toFixed(2) : null;
  cmd('gamemode survival Tester'); await sleep(500);
  cmd('kill @e[type=parrot]'); await sleep(500);
  res.sat2 = await sitOnShoulder(bot);
  await sleep(1500);
  cmd('damage Tester 1'); await sleep(800);
  res.shoulderAfterDamage = await U.ask(bot, '/data get entity Tester ShoulderEntityLeft', /entity data|has the following|Found no/i);
  res.parrotsAfterDamage = await parrots();
  console.log('RESULT', JSON.stringify(res, null, 1));
  bot.quit(); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
