// 투사체-블록 반응 중 2026-11-03 봇 시험에서 빠진 것: 삼지창→종유석, 화살→종 울림, 스폰 보호(비op 주인 불화살), 몹 화살 + mobGriefing false,
// 그리고 엔티티 블록 훅의 서리 보행자 장화 마그마. 서버는 spawn-protection=16 으로 띄운다(op 가 있어야 보호가 걸린다).
const U = require('../round2/u'); const { sleep, chat, ask, cmd } = U;
(async () => {
  const bot = await U.setup('Shooter');
  const noop = U.makeBot('NoOp', { plugins: { resource_pack: false } }); await U.spawned(noop); await sleep(1500);
  const blk = async (x, y, z, b) => /passed/.test(await ask(bot, `/execute if block ${x} ${y} ${z} ${b}`, /Test (passed|failed)/) ?? '');
  const actions = []; bot._client.on('block_action', p => actions.push(p));
  const spawnPos = bot.spawnPoint ?? { x: 0, z: 0 };
  console.log('RESULT 월드 스폰', JSON.stringify(spawnPos));
  for (const c of ['/gamemode creative', '/difficulty easy', '/gamerule doMobSpawning false', '/gamerule mobGriefing true', '/kill @e[type=!player]', '/tp @s 40.5 -60 0.5']) await chat(bot, c);
  cmd('tp NoOp 60.5 -60 0.5'); await sleep(500);
  // 1) 삼지창: 아래에서 위로 종유석(아래를 향한 뾰족한 점적석 끝)에
  await chat(bot, '/fill 40 -55 4 40 -55 4 stone'); await chat(bot, '/setblock 40 -56 4 pointed_dripstone[vertical_direction=down,thickness=tip]');
  await chat(bot, '/summon trident 40.5 -60 4.5 {Motion:[0.0,1.2,0.0],pickup:2b}', 1500);
  console.log('RESULT 삼지창(빠름) → 종유석 남음?', await blk(40, -56, 4, 'pointed_dripstone'));
  await chat(bot, '/setblock 42 -55 4 stone'); await chat(bot, '/setblock 42 -56 4 pointed_dripstone[vertical_direction=down,thickness=tip]');
  await chat(bot, '/summon trident 42.5 -56.6 4.5 {Motion:[0.0,0.3,0.0],pickup:2b,NoGravity:1b}', 1500);
  console.log('RESULT 삼지창(느림 0.3) → 종유석 남음?(남아야 함)', await blk(42, -56, 4, 'pointed_dripstone'));
  await chat(bot, '/setblock 44 -55 4 stone'); await chat(bot, '/setblock 44 -56 4 pointed_dripstone[vertical_direction=down,thickness=tip]');
  await chat(bot, '/summon arrow 44.5 -60 4.5 {Motion:[0.0,1.2,0.0],pickup:2b}', 1500);
  console.log('RESULT 화살(빠름) → 종유석 남음?(남아야 함)', await blk(44, -56, 4, 'pointed_dripstone'));
  // 2) 종: 바닥 종은 facing 축과 같은 면(여기선 서쪽 면)을 맞아야 울린다
  await chat(bot, '/setblock 48 -60 4 bell[attachment=floor,facing=east]'); actions.length = 0;
  await chat(bot, '/summon arrow 45.5 -59.7 4.5 {Motion:[1.5,0.0,0.0],pickup:2b,NoGravity:1b}', 1500);
  console.log('RESULT 종 울림 block_action', JSON.stringify(actions.filter(a => a.location.x === 48).map(a => [a.byte1, a.byte2])));
  // 3) 스폰 보호: 스폰 근처 TNT 에 비op(NoOp) 주인 불화살 / op(Shooter) 주인 불화살
  const sx = Math.floor(spawnPos.x), sz = Math.floor(spawnPos.z);
  const fire = async (owner, x, label) => {
    await chat(bot, `/setblock ${x} -60 ${sz + 3} tnt`, 300);
    bot.chat(`/summon arrow ${x + 0.5} -56 ${sz + 3.5} {Motion:[0.0,-1.5,0.0],Fire:200s,pickup:2b,Owner:${owner}}`); await sleep(1500);
    console.log('RESULT 스폰 보호', label, 'TNT 남음?', await blk(x, -60, sz + 3, 'tnt'), '불붙은 TNT', await ask(bot, `/execute if entity @e[type=tnt]`, /Test/));
    await chat(bot, '/kill @e[type=tnt]', 300);
  };
  await fire(U.uuidInts(noop.player.uuid), sx + 2, '비op 주인');
  await fire(U.uuidInts(bot.player.uuid), sx - 2, 'op 주인');
  // 4) 몹 화살 + mobGriefing false: 스켈레톤을 주인으로 한 불화살이 TNT·모닥불에
  await chat(bot, '/summon skeleton 70.5 -60 10.5 {NoAI:1b,UUID:[I;11,22,33,44]}');
  await chat(bot, '/gamerule mobGriefing false');
  for (const [b, x] of [['tnt', 70], ['campfire[lit=false]', 72], ['candle[lit=false]', 74]]) {
    await chat(bot, `/setblock ${x} -60 4 ${b}`, 300);
    await chat(bot, `/summon arrow ${x + 0.5} -57 4.5 {Motion:[0.0,-1.5,0.0],Fire:200s,pickup:2b,Owner:[I;11,22,33,44]}`, 1500);
  }
  console.log('RESULT mobGriefing false 몹 불화살: TNT 남음', await blk(70, -60, 4, 'tnt'), '모닥불 꺼진 채', await blk(72, -60, 4, 'campfire[lit=false]'), '양초 켜짐(바닐라도 켬)', await blk(74, -60, 4, 'candle[lit=true]'));
  await chat(bot, '/gamerule mobGriefing true'); await chat(bot, '/kill @e[type=!player]');
  // 6) /effect 를 몹에게 + 몸 효과 입자(메타데이터 10)
  const meta10 = {}; bot._client.on('entity_metadata', p => { for (const m of p.metadata) if (m.key === 10) meta10[p.entityId] = m.value; });
  await chat(bot, '/summon pig 40.5 -60 8.5 {NoAI:1b,Tags:["fx"]}', 800);
  const r = await ask(bot, '/effect give @e[tag=fx,limit=1] minecraft:speed 30 1', /./, 2000); await sleep(800);
  const pig = U.near(bot, 'pig')[0];
  console.log('RESULT /effect 몹 대상 응답', r, '돼지 효과', await ask(bot, '/data get entity @e[tag=fx,limit=1] active_effects', /entity data|Found no/), '입자 메타데이터', JSON.stringify(pig && meta10[pig.id]));
  // 5) 서리 보행자 장화로 마그마 걷기
  noop.quit(); await sleep(500);
  await chat(bot, '/gamemode survival'); await chat(bot, '/gamerule naturalRegeneration false'); await chat(bot, '/effect clear @s');
  await chat(bot, '/fill 80 -61 0 80 -61 20 magma_block'); await chat(bot, '/tp @s 80.5 -60 0.5 0 0', 800);
  await chat(bot, '/item replace entity @s armor.feet with diamond_boots[enchantments={levels:{"minecraft:frost_walker":2}}]', 500);
  const hp0 = bot.health; bot.setControlState('back', true); await sleep(3000); bot.clearControlStates(); await sleep(500);   // mineflayer yaw 0 = 북(-z) → 뒤로 = +z
  console.log('RESULT 서리 보행자 마그마 걷기 체력', hp0, '→', bot.health, '끝 z', bot.entity.position.z.toFixed(1));
  await chat(bot, '/item replace entity @s armor.feet with air'); await chat(bot, '/tp @s 80.5 -60 0.5 0 0', 800);
  const hp1 = bot.health; bot.setControlState('back', true); await sleep(3000); bot.clearControlStates(); await sleep(500);
  console.log('RESULT 대조: 장화 없이 마그마 걷기 체력', hp1, '→', bot.health);
  await chat(bot, '/gamerule naturalRegeneration true'); await chat(bot, '/gamemode creative'); await chat(bot, '/effect give @s instant_health 1 5');
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
