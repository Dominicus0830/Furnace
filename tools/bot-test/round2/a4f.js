const { setup, chat, sleep, ask, num, near, count, hold, uuidInts, waitEnt } = require('./u');
(async () => {
  let bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/difficulty easy', '/effect clear @s', '/effect give @s minecraft:resistance 600 4 true']) await chat(bot, c);
  const seen = {}; bot.on('entitySpawn', e => { seen[e.name] = (seen[e.name] || 0) + 1; });
  await chat(bot, '/tp @s 700.5 -60 0.5 0 0', 1500);
  await chat(bot, '/summon minecraft:ghast 700.5 -59 20.5 {PersistenceRequired:1b}', 300);
  const g = await waitEnt(bot, 'ghast'); let hits = 0;
  const end = Date.now() + 45000;
  while (Date.now() < end && bot.entities[g.id]) {
    const fb = Object.values(bot.entities).find(e => e.name === 'fireball' && e.position.distanceTo(bot.entity.position.offset(0, 1.6, 0)) < 4);
    if (fb) { await bot.lookAt(g.position.offset(0, 2, 0), true); bot.attack(fb); hits++; await sleep(500); }
    await sleep(25);
  }
  console.log('RESULT 가스트: 본 엔티티', JSON.stringify(seen), '불덩이 친 횟수', hits, '가스트 남음', !!bot.entities[g.id], await ask(bot, '/execute if entity @e[type=ghast]', /passed|failed/));
  await chat(bot, '/kill @e[type=!player]', 300);
  for (const k in seen) delete seen[k];
  await chat(bot, '/tp @s 760.5 -60 0.5 0 0', 1000);
  await chat(bot, '/summon minecraft:breeze 760.5 -60 8.5 {PersistenceRequired:1b}', 300);
  const bz = await waitEnt(bot, 'breeze'); let wh = 0; const vel = [];
  const end2 = Date.now() + 40000;
  while (Date.now() < end2 && wh < 3) {
    const wc = Object.values(bot.entities).find(e => e.name === 'breeze_wind_charge' && e.position.distanceTo(bot.entity.position.offset(0, 1.6, 0)) < 4);
    if (wc) { const z0 = wc.position.z; await bot.lookAt(bz.position.offset(0, 1, 0), true); bot.attack(wc); wh++; await sleep(150); vel.push([z0.toFixed(2), wc.position.z.toFixed(2), JSON.stringify(wc.velocity)]); }
    await sleep(25);
  }
  console.log('RESULT 브리즈: 본 엔티티', JSON.stringify(seen), '친 횟수', wh, JSON.stringify(vel), '브리즈 체력', await num(bot, '@e[type=breeze,limit=1]', 'Health'));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 앵무새: 6초 기다린 뒤 플레이어 자리로 계속 옮김
  await chat(bot, '/tp @s 850.5 -60 0.5', 1000);
  await chat(bot, `/summon minecraft:parrot 851.5 -60 1.5 {Owner:${OWN},Variant:2}`, 6000);
  const sh = () => [19, 20].filter(i => { const v = bot.entity.metadata?.[i]; return v && typeof v === 'object' && Object.keys(v).length && JSON.stringify(v) !== '{"type":"compound","name":"","value":{}}'; });
  let landed = -1;
  for (let i = 0; i < 30 && landed < 0; i++) { await chat(bot, '/tp @e[type=parrot] @s', 200); await sleep(300); if (sh().length) landed = i; }
  console.log('RESULT 앵무새 어깨', landed, JSON.stringify(sh()), JSON.stringify(bot.entity.metadata?.[19]).slice(0, 120), '앵무새 남음', near(bot, 'parrot').length);
  console.log('RESULT 저장', await ask(bot, '/data get entity @s ShoulderEntityLeft.id', /entity data|Found/));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
