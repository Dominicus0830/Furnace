const { setup, chat, sleep, ask, near, uuidInts, makeBot, spawned } = require('./u');
(async () => {
  let bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamemode survival', '/kill @e[type=!player]', '/effect clear @s', '/data remove entity @s ShoulderEntityLeft']) await chat(bot, c);
  const meta = []; const hook = b => b._client.on('entity_metadata', p => { if (p.entityId === b.entity?.id) for (const m of p.metadata) if (m.key === 19 || m.key === 20) meta.push(m.key + ':' + (JSON.stringify(m.value).includes('parrot') ? 'parrot' : 'empty')); });
  hook(bot);
  await chat(bot, '/tp @s 850.5 -60 0.5', 1000);
  await chat(bot, `/summon minecraft:parrot 851.5 -60 1.5 {Owner:${OWN},Variant:2}`, 6000);
  for (let i = 0; i < 10 && !meta.length; i++) { await chat(bot, '/tp @e[type=parrot] @s', 200); await sleep(300); }
  console.log('RESULT 앉음 메타', JSON.stringify(meta), await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /entity data|Found/));
  bot.quit(); await sleep(1500);
  bot = makeBot('Tester', { plugins: { resource_pack: false } }); meta.length = 0;
  bot._client.on('entity_metadata', p => { for (const m of p.metadata) if ((m.key === 19 || m.key === 20) && JSON.stringify(m.value).includes('parrot')) meta.push('id' + p.entityId + ' ' + m.key); });
  await spawned(bot); await sleep(2500);
  console.log('RESULT 재접속 뒤 메타(어깨에 parrot)', JSON.stringify(meta), '내 id', bot.entity.id, await ask(bot, '/data get entity @s ShoulderEntityLeft.Variant', /entity data|Found/));
  await chat(bot, '/damage @s 1 minecraft:mob_attack', 1500);
  console.log('RESULT 피해 뒤', await ask(bot, '/data get entity @s ShoulderEntityLeft', /entity data|Found/), '앵무새 엔티티', near(bot, 'parrot').length);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
