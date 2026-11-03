const { setup, chat, sleep, ask, waitEnt } = require('./u');
(async () => {
  let bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/gamemode survival', '/kill @e[type=!player]', '/difficulty easy', '/effect give @s minecraft:resistance 600 4 true']) await chat(bot, c);
  await chat(bot, '/tp @s 700.5 -60 0.5 0 0', 1500);
  await chat(bot, '/summon minecraft:ghast 700.5 -59 20.5 {PersistenceRequired:1b}', 300);
  const g = await waitEnt(bot, 'ghast');
  const fbv = {}; bot._client.on('entity_velocity', p => { const e = bot.entities[p.entityId]; if (e?.name === 'fireball') (fbv[p.entityId] ??= []).push(p.velocityZ); });
  const gd = []; bot._client.on('damage_event', p => { if (p.entityId === g.id) gd.push(p.sourceTypeId); });
  const dist = []; let hits = 0;
  const end = Date.now() + 30000;
  while (Date.now() < end && bot.entities[g.id]) {
    const fb = Object.values(bot.entities).find(e => e.name === 'fireball');
    if (fb) { dist.push(fb.position.distanceTo(bot.entity.position.offset(0, 1.62, 0)).toFixed(1)); await bot.lookAt(g.position.offset(0, 2, 0), true); bot.attack(fb); hits++; }
    await sleep(50);
  }
  console.log('RESULT 가스트 불덩이 z속도 기록', JSON.stringify(fbv), '가스트 피해', JSON.stringify(gd), '거리 표본', dist.slice(0, 40).join(','), '남음', !!bot.entities[g.id]);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
