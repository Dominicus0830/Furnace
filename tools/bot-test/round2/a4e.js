// 가스트 되치기, 브리즈 되치기·투사체 튕김, 돌고래, 앵무새 어깨
const { setup, chat, sleep, ask, num, near, count, hold, uuidInts, waitEnt, makeBot, spawned } = require('./u');
const V = require('vec3');
(async () => {
  let bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s', '/effect give @s minecraft:resistance 600 4 true', '/effect give @s minecraft:instant_health 1 5 true']) await chat(bot, c);
  // 1) 가스트
  await chat(bot, '/tp @s 700.5 -60 0.5 0 0', 1500);
  await chat(bot, '/summon minecraft:ghast 700.5 -52 22.5 {PersistenceRequired:1b}', 300);
  const g = await waitEnt(bot, 'ghast'); let hits = 0;
  const end = Date.now() + 40000;
  while (Date.now() < end && g.isValid !== false && bot.entities[g.id]) {
    const fb = Object.values(bot.entities).find(e => e.name === 'fireball' && e.position.distanceTo(bot.entity.position.offset(0, 1.6, 0)) < 3.5);
    if (fb) { await bot.lookAt(g.position.offset(0, 2, 0), true); bot.attack(fb); hits++; await sleep(400); }
    await sleep(30);
  }
  console.log('RESULT 가스트: 불덩이 친 횟수', hits, '가스트 남음', !!bot.entities[g.id], await ask(bot, '/execute if entity @e[type=ghast]', /passed|failed/));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 2) 브리즈: 다른 투사체 튕김(화살), 바람 구슬 되치기
  await chat(bot, '/tp @s 740.5 -60 -10.5', 1000);
  await chat(bot, '/summon minecraft:breeze 740.5 -60 6.5 {NoAI:1b}', 600);
  await chat(bot, '/summon minecraft:arrow 740.5 -59 0.5 {Motion:[0.0,0.0,1.2],damage:6d}', 1500);
  const ar = await waitEnt(bot, 'arrow', 500);
  console.log('RESULT 브리즈에 화살: 브리즈 체력(30)', await num(bot, '@e[type=breeze,limit=1]', 'Health'), '화살 위치 z(6.5 넘으면 통과, 작으면 튕김)', ar?.position?.z?.toFixed(2), '속도', ar?.velocity && JSON.stringify(ar.velocity));
  await chat(bot, '/kill @e[type=!player]', 300);
  await chat(bot, '/tp @s 760.5 -60 0.5 0 0', 1000);
  await chat(bot, '/summon minecraft:breeze 760.5 -60 12.5 {PersistenceRequired:1b}', 300);
  const bz = await waitEnt(bot, 'breeze'); let wh = 0; const vel = [];
  const end2 = Date.now() + 30000;
  while (Date.now() < end2 && wh < 3) {
    const wc = Object.values(bot.entities).find(e => e.name === 'breeze_wind_charge' && e.position.distanceTo(bot.entity.position.offset(0, 1.6, 0)) < 3.5);
    if (wc) { const z0 = wc.position.z; await bot.lookAt(bz.position.offset(0, 1, 0), true); bot.attack(wc); wh++; await sleep(200); vel.push([z0.toFixed(2), wc.position.z.toFixed(2)]); }
    await sleep(30);
  }
  console.log('RESULT 브리즈 바람 구슬 친 횟수', wh, '친 직후 z 변화(커지면 되튕김)', JSON.stringify(vel), '브리즈 체력', await num(bot, '@e[type=breeze,limit=1]', 'Health'));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 3) 돌고래
  await chat(bot, '/fill 800 -63 -10 815 -61 10 minecraft:water', 800);
  await chat(bot, '/tp @s 807.5 -61 0.5', 1500);
  await chat(bot, '/summon minecraft:dolphin 808.5 -62 2.5', 600);
  await chat(bot, '/give @s minecraft:cod', 300); await hold(bot, 'cod');
  const d = await waitEnt(bot, 'dolphin'); await bot.lookAt(d.position); await bot.activateEntity(d); await sleep(800);
  console.log('RESULT 돌고래 물고기 주기: GotFish', await num(bot, '@e[type=dolphin,limit=1]', 'GotFish'), '대구 남음', count(bot, 'cod'));
  // 헤엄: 달리기 + 앞으로
  bot.setControlState('sprint', true); bot.setControlState('forward', true);
  for (let i = 0; i < 20; i++) { await bot.look(i % 2 ? 0 : Math.PI, 0.3, true); await sleep(400); }
  bot.clearControlStates();
  console.log('RESULT 돌고래 옆 헤엄 뒤 효과', await ask(bot, '/data get entity @s active_effects', /entity data|Found/));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 4) 앵무새 어깨
  await chat(bot, '/tp @s 850.5 -60 0.5', 1000);
  await chat(bot, `/summon minecraft:parrot 851.5 -60 1.5 {Owner:${OWN},Variant:2}`, 300);
  const sh = () => (bot.entity.metadata || []).map((v, i) => [i, v]).filter(([i, v]) => i >= 19 && i <= 20 && v && Object.keys(v).length).map(([i]) => i);
  let landed = 0; for (let i = 0; i < 40 && !landed; i++) { await sleep(500); if (sh().length) landed = i; }
  console.log('RESULT 앵무새 어깨 앉음', !!landed, '걸린 시간(0.5초 단위)', landed, '메타데이터 칸', JSON.stringify(sh()), '앵무새 엔티티 남음', near(bot, 'parrot').length);
  await chat(bot, '/data get entity @s ShoulderEntityLeft.id', 300);
  console.log('RESULT 저장 꼴', await ask(bot, '/data get entity @s ShoulderEntityLeft.id', /entity data|Found/), await ask(bot, '/data get entity @s ShoulderEntityRight.id', /entity data|Found/));
  bot.quit(); await sleep(1500);
  bot = makeBot('Tester', { plugins: { resource_pack: false } }); await spawned(bot); await sleep(2500);
  console.log('RESULT 재접속 뒤 어깨', JSON.stringify(sh()), await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /entity data|Found/));
  await chat(bot, '/damage @s 1 minecraft:mob_attack', 1500);
  console.log('RESULT 피해 뒤 어깨', JSON.stringify(sh()), '앵무새 엔티티', near(bot, 'parrot').length);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
