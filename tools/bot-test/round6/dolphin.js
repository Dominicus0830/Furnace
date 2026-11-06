// 돌고래 보물 탐색: 바다에서 돌고래에게 대구를 주면 가장 가까운 난파선·바다 유적(태그 dolphin_located)을 TreasurePos 로 잡는다(노이즈 월드)
const U = require('../round2/u'); const { sleep, chat, ask } = U;
(async () => {
  const bot = await U.setup('Diver');
  for (const c of ['/gamemode survival', '/difficulty peaceful', '/effect give @s water_breathing 1000 0 true']) await chat(bot, c);
  const oc = await ask(bot, '/locate biome #minecraft:is_ocean', /nearest|Could not|Unknown|Incorrect/i, 60000);
  console.log('RESULT 바다', oc);
  const m = oc && oc.match(/\[(-?\d+), (~|-?\d+), (-?\d+)\]/); if (!m) { bot.quit(); return; }
  const [x, z] = [+m[1], +m[3]];
  await chat(bot, `/tp @s ${x} 70 ${z}`, 8000);
  await chat(bot, `/tp @s ${x} 61 ${z}`, 1500);
  const near = await ask(bot, '/locate structure #minecraft:dolphin_located', /nearest|Could not/i, 60000);
  console.log('RESULT 봇 자리에서 가까운 보물 구조물', near);
  await chat(bot, '/kill @e[type=dolphin]', 500); await chat(bot, '/clear @s'); await chat(bot, '/give @s cod 4'); await U.hold(bot, 'cod');
  bot.physicsEnabled = false;   // 물속에서 가라앉지 않게 제자리
  const bp = bot.entity.position; bot.chat(`/summon dolphin ${bp.x} ${bp.y + 0.5} ${bp.z + 1.5}`);   // 헤엄쳐 가기 전에 바로 먹인다
  const d = await U.waitEnt(bot, 'dolphin', 4000); if (!d) { console.log('RESULT 돌고래 안 보임'); bot.quit(); return; }
  console.log('RESULT 돌고래 거리', d.position.distanceTo(bot.entity.position).toFixed(2), '손', bot.heldItem?.name, 'id', d.id);
  const S = require('./snd'); const snd = []; S.listen(bot, snd); const evs = []; bot._client.on('entity_status', p => { if (p.entityId === d.id) evs.push(p.entityStatus); });
  await bot.lookAt(d.position.offset(0, 0.3, 0)); await bot.activateEntity(d); await sleep(150);
  console.log('RESULT 먹인 직후 GotFish', await ask(bot, '/data get entity @e[type=dolphin,limit=1,sort=nearest] GotFish', /data:|Found no|No /), 'Air', await ask(bot, '/data get entity @e[type=dolphin,limit=1,sort=nearest] Air', /data:|Found no|No /), '상태 이벤트', evs.join(','), '소리', snd.map(x => x.name).join(','), '대구', U.count(bot, 'cod'));
  for (let i = 0; i < 20; i++) {
    await sleep(1500);
    const g = await ask(bot, '/data get entity @e[type=dolphin,limit=1,sort=nearest] GotFish', /data:|Found no|No /);
    const tx = await ask(bot, '/data get entity @e[type=dolphin,limit=1,sort=nearest] TreasurePosX', /data:|Found no|No /);
    const tz = await ask(bot, '/data get entity @e[type=dolphin,limit=1,sort=nearest] TreasurePosZ', /data:|Found no|No /);
    const num = s => s && (s.match(/data: (-?\d+)/) || [])[1];
    if (i % 4 === 0 || +num(tx)) console.log('RESULT', (i + 1) * 1.5, '초: GotFish', num(g), 'TreasurePos', num(tx), num(tz));
    if (+num(tx) || +num(tz)) {
      console.log('RESULT 돌고래 자리에서 locate', await ask(bot, '/execute at @e[type=dolphin,limit=1,sort=nearest] run locate structure #minecraft:dolphin_located', /nearest|Could not/i, 60000));
      break;
    }
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
