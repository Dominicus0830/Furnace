// 몹 동작: 조각 감지기 반응, 알레이, 파괴수 방패, 끈(보트·말·낙타·차원), 돼지
const { setup, chat, sleep, ask, num, near, count, hold, uuidInts } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s']) await chat(bot, c);
  // 4) 끈: 보트·말·낙타, 차원 이동
  await chat(bot, '/gamemode survival', 200); await chat(bot, '/clear @s', 200); await chat(bot, '/tp @s 600.5 -60 0.5', 600);
  const at = []; bot._client.on('attach_entity', p => at.push(p));
  for (const [mob, nbt] of [['boat', ''], ['horse', `{Tame:1b,Owner:${OWN}}`], ['camel', `{Tame:1b,Owner:${OWN}}`]]) {
    await chat(bot, `/summon minecraft:${mob} 600.5 -60 3.5 ${nbt}`.trim(), 800);
    await chat(bot, '/give @s minecraft:lead', 300); await hold(bot, 'lead');
    let e; for (let k = 0; k < 30 && !e; k++) { await sleep(100); e = Object.values(bot.entities).filter(x => x.name?.includes(mob)).sort((a,b)=>a.position.distanceTo(bot.entity.position)-b.position.distanceTo(bot.entity.position))[0]; } console.log('대상', e?.name);
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
