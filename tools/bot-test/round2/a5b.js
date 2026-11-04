const { setup, chat, sleep, ask } = require('./u');
(async () => {
  const bot = await setup();
  const pk = []; bot._client.on('entity_update_attributes', p => { if (p.entityId === bot.entity.id) pk.push(p.properties.map(x => x.key + '=' + x.value + JSON.stringify(x.modifiers.map(m => [m.uuid ?? m.id, m.amount]))).join(' ')); });
  for (const c of ['/gamemode survival', '/clear @s', '/effect clear @s']) await chat(bot, c);
  await chat(bot, '/fill 10 -61 -5 14 -61 5 minecraft:soul_sand', 300);
  await chat(bot, '/tp @s 12.5 -60 -4.5 0 0', 1500);
  await chat(bot, '/item replace entity @s armor.feet with minecraft:diamond_boots[enchantments={levels:{"minecraft:soul_speed":3}}]', 800);
  pk.length = 0;
  console.log('봇 위치', bot.entity.position, 'onGround', bot.entity.onGround, '발밑', bot.blockAt(bot.entity.position.offset(0, -0.6, 0))?.name);
  bot._client.on('packet', (d, m) => { if (m.name.includes('attrib')) console.log('PKT', m.name, JSON.stringify(d).slice(0, 300)); }); bot.setControlState('forward', true); await sleep(2000); bot.setControlState('forward', false); await sleep(500);
  console.log('RESULT 영혼 모래 위 걸으며 받은 속성 패킷', JSON.stringify(pk), '위치', bot.entity.position);
  console.log('RESULT 서버 위치', await ask(bot, '/data get entity @s Pos', /entity data/), await ask(bot, '/data get entity @s OnGround', /entity data/));
  bot.quit(); await sleep(300); process.exit(0);
})();
