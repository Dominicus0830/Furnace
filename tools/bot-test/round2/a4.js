// 몹 동작: 조각 감지기 반응, 알레이, 파괴수 방패, 끈(보트·말·낙타·차원), 돼지
const { setup, chat, sleep, ask, num, near, count, hold, uuidInts } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s']) await chat(bot, c);
  // 1) 조각 감지기: 행동마다 새 감지기
  async function sensor(x, label, setupCmds, action, wait = 1500) {
    await chat(bot, `/tp @s ${x} -60 -12`, 800);
    for (const c of setupCmds) await chat(bot, c, 300);
    await sleep(1200);
    await chat(bot, `/setblock ${x} -60 -4 minecraft:sculk_sensor`, 1500);
    await action(); await sleep(wait);
    const f = await ask(bot, `/data get block ${x} -60 -4 last_vibration_frequency`, /block data|Found/);
    console.log('RESULT 감지기', label, f, JSON.stringify(bot.blockAt(new V(x, -60, -4)).getProperties()));
  }
  await sensor(200, '양 털 깎기(기대 6)', ['/summon minecraft:sheep 200.5 -60 0.5 {NoAI:1b}', '/give @s minecraft:shears'], async () => {
    await chat(bot, '/tp @s 200.5 -60 -2', 600); await hold(bot, 'shears'); const s = near(bot, 'sheep')[0]; await bot.lookAt(s.position.offset(0, 0.8, 0)); await bot.activateEntity(s);
  });
  await sensor(230, '크리퍼 폭발(기대 15)', ['/summon minecraft:creeper 230.5 -60 3.5 {NoAI:1b,Fuse:200s}'], async () => {
    await chat(bot, '/data merge entity @e[type=creeper,limit=1] {ignited:1b,Fuse:5s}', 300);
  }, 2500);
  await sensor(260, '화살 착탄(기대 2)', [], async () => {
    await chat(bot, '/summon minecraft:arrow 260.5 -55 0.5 {Motion:[0.0,-1.0,0.0]}', 300);
  });
  await sensor(290, '몹 피격(기대 7)', ['/summon minecraft:cow 290.5 -60 0.5 {NoAI:1b}'], async () => {
    await chat(bot, '/damage @e[type=cow,limit=1] 1 minecraft:player_attack by @s', 300);
  });
  await sensor(320, '엔더맨 순간이동(기대 14)', ['/summon minecraft:enderman 320.5 -60 0.5 {NoAI:1b}'], async () => {
    await chat(bot, '/data merge entity @e[type=enderman,limit=1] {NoAI:0b}', 100);
    await chat(bot, '/damage @e[type=enderman,limit=1] 1 minecraft:arrow', 300);
  });
  await chat(bot, '/kill @e[type=!player]', 300);
  // 2) 알레이: 음표 블록에 끌림, 주크박스 춤
  await chat(bot, '/tp @s 400 -60 -5', 800);
  await chat(bot, `/summon minecraft:allay 400.5 -59 0.5`, 600);
  await chat(bot, '/setblock 408 -60 0 minecraft:note_block', 300);
  await chat(bot, '/gamemode survival', 300);
  await chat(bot, '/tp @s 407.5 -60 -1.5', 600);
  await bot.lookAt(new V(408.5, -59.5, 0.5)); await bot.activateBlock(bot.blockAt(new V(408, -60, 0))); await sleep(1500);
  console.log('RESULT 알레이 기억(음표 블록 뒤)', await ask(bot, '/data get entity @e[type=allay,limit=1] Brain.memories', /entity data|Found/));
  await chat(bot, '/setblock 400 -60 4 minecraft:jukebox', 300); await chat(bot, '/give @s minecraft:music_disc_cat', 400); await hold(bot, 'music_disc_cat');
  await chat(bot, '/tp @s 400.5 -60 2.5', 600);
  await bot.lookAt(new V(400.5, -59.5, 4.5)); await bot.activateBlock(bot.blockAt(new V(400, -60, 4))); await sleep(2500);
  const al = near(bot, 'allay')[0];
  console.log('RESULT 주크박스 뒤 알레이 메타데이터', JSON.stringify(al?.metadata?.slice(15)), '주크박스', bot.blockAt(new V(400, -60, 4)).getProperties());
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/setblock 400 -60 4 minecraft:air', 300);
  // 3) 파괴수 방패
  await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 500.5 -60 0.5 0 0', 600);
  await chat(bot, '/item replace entity @s weapon.offhand with minecraft:shield', 300);
  await chat(bot, '/effect give @s minecraft:resistance 120 4 true', 200);
  await chat(bot, '/effect give @s minecraft:instant_health 1 5 true', 200);
  const ev = []; bot._client.on('entity_status', p => { const e = bot.entities[p.entityId]; if (e?.name === 'ravager') ev.push(p.entityStatus); });
  await chat(bot, '/summon minecraft:ravager 500.5 -60 6.5', 600);
  const rv = near(bot, 'ravager')[0];
  bot.activateItem(true);
  for (let i = 0; i < 40; i++) { await bot.lookAt(rv.position.offset(0, 1.5, 0), true); await sleep(250); }
  bot.deactivateItem();
  console.log('RESULT 파괴수 엔티티 상태 패킷(39=기절, 4=공격)', JSON.stringify(ev), '방패 손상', JSON.stringify(bot.inventory.slots[45]?.components?.find(c => c.type === 'damage')));
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/effect clear @s', 200);
  // 4) 끈: 보트·말·낙타, 차원 이동
  await chat(bot, '/gamemode survival', 200); await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 600.5 -60 0.5', 600);
  const at = []; bot._client.on('attach_entity', p => at.push(p));
  for (const [mob, nbt] of [['oak_boat', ''], ['horse', `{Tame:1b,Owner:${OWN}}`], ['camel', `{Tame:1b,Owner:${OWN}}`]]) {
    await chat(bot, `/summon minecraft:${mob} 600.5 -60 3.5 ${nbt}`, 800);
    await chat(bot, '/give @s minecraft:lead', 300); await hold(bot, 'lead');
    const name = mob === 'oak_boat' ? 'boat' : mob; const e = Object.values(bot.entities).find(x => x.name === name || x.name === mob);
    at.length = 0; await bot.lookAt(e.position.offset(0, 0.5, 0)); await bot.activateEntity(e); await sleep(600);
    const att = JSON.stringify(at);
    await chat(bot, '/tp @s 600.5 -60 18.5', 2500);
    console.log('RESULT 끈', mob, '맴', att, '15칸 뒤', JSON.stringify(at.slice(-1)), '대상 위치', e.position.z.toFixed(1));
    await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/tp @s 600.5 -60 0.5', 600);
  }
  await chat(bot, '/summon minecraft:cow 600.5 -60 3.5 {NoAI:1b}', 600); await chat(bot, '/give @s minecraft:lead', 300); await hold(bot, 'lead');
  const cw = near(bot, 'cow')[0]; at.length = 0; await bot.lookAt(cw.position.offset(0, 1, 0)); await bot.activateEntity(cw); await sleep(600);
  const before = JSON.stringify(at);
  await chat(bot, '/execute in minecraft:the_nether run tp @s 0 100 0', 4000);
  await chat(bot, '/execute in minecraft:overworld run tp @s 600.5 -60 0.5', 4000);
  console.log('RESULT 끈 + 차원 이동: 맴', before, '돌아온 뒤 소의 끈', await ask(bot, '/data get entity @e[type=cow,limit=1] leash', /entity data|Found/), '바닥 끈 아이템', await ask(bot, '/execute if entity @e[type=item,nbt={Item:{id:"minecraft:lead"}}]', /passed|failed/));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 5) 돼지
  await chat(bot, '/clear @s', 200); await chat(bot, '/summon minecraft:pig 600.5 -60 3.5', 600);
  await chat(bot, '/give @s minecraft:saddle', 300); await hold(bot, 'saddle');
  let pig = near(bot, 'pig')[0]; await bot.lookAt(pig.position.offset(0, 0.6, 0)); await bot.activateEntity(pig); await sleep(600);
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
