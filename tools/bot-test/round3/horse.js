// U4: 말 타기/내리기 시점을 set_passengers 패킷으로 보고, 내린 뒤 몇 ms 만에 다시 타지는지 잰다(바닐라 boardingCooldown 60틱)
const U = require('../round2/u');
const { sleep, near, ask } = U;
(async () => {
  const bot = await U.setup('Rider');
  const t0 = Date.now(); const ev = [];
  let onHorse = false, lastOff = 0;
  bot._client.on('set_passengers', p => { const me = p.passengers.includes(bot.entity.id); if (onHorse && !me) lastOff = Date.now(); onHorse = me; ev.push(`${Date.now() - t0}ms set_passengers ${p.entityId} [${p.passengers}]${me ? ' 탐' : ' 내림'}`); });
  bot._client.on('position', p => ev.push(`${Date.now() - t0}ms position ${p.x.toFixed(2)},${p.y.toFixed(2)},${p.z.toFixed(2)}`));
  bot.chat('/gamemode survival'); bot.chat('/difficulty peaceful'); bot.chat('/kill @e[type=!player]'); bot.chat('/tp @s 40.5 -60 0.5'); await sleep(1200);
  bot.chat(`/summon horse 40.5 -60 3.5 {Tame:1b,NoAI:1b,Owner:${U.uuidInts(bot.player.uuid)},SaddleItem:{id:"minecraft:saddle",count:1}}`); await sleep(1000);
  const horse = near(bot, 'horse')[0];
  const srvRiding = async () => onHorse;
  const mark = s => ev.push(`${Date.now() - t0}ms ${s}`);
  mark('우클릭'); bot.activateEntity(horse); await sleep(700);
  mark('서버 탑승=' + await srvRiding());
  for (const [label, holdMs] of [['웅크림 0.3초', 300], ['웅크림 0.05초(한 틱)', 50], ['웅크림 2초 유지', 2000]]) {
    mark(label + ' 누름'); bot.setControlState('sneak', true); await sleep(holdMs); bot.setControlState('sneak', false); mark('뗌');
    await sleep(300); mark('서버 탑승=' + await srvRiding());
    // 1초마다 다시 타기
    let ok = -1; for (let i = 0; i < 40; i++) { bot.activateEntity(horse); await sleep(200); if (onHorse) { ok = i; break; } }
    console.log(`RESULT ${label}: ${ok < 0 ? '8초 안에 다시 못 탐' : '내린 뒤 ' + (Date.now() - lastOff) + 'ms 에 다시 탐'}`);
  }
  console.log(ev.join('\n'));
  bot.quit(); await sleep(500); process.exit(0);
})();
