const { setup, chat, sleep, ask } = require('./u');
(async () => {
  const bot = await setup();
  const pk = []; bot._client.on('entity_update_attributes', p => { if (p.entityId === bot.entity.id) pk.push(p.properties.map(x => x.key + '=' + x.value + JSON.stringify(x.modifiers.map(m => [m.uuid ?? m.id, m.amount]))).join(' ')); });
  for (const c of ['/gamemode survival', '/clear @s', '/effect clear @s']) await chat(bot, c);
  await chat(bot, '/fill 10 -61 -5 14 -61 5 minecraft:soul_sand', 300);
  await chat(bot, '/tp @s 12.5 -60 -8.5 0 0', 1500);
  await chat(bot, '/item replace entity @s armor.feet with minecraft:diamond_boots[enchantments={levels:{"minecraft:soul_speed":3}}]', 800);
  console.log('RESULT 잔디 위 장화', JSON.stringify(pk), await ask(bot, '/attribute @s minecraft:generic.movement_speed get', /Value/)); pk.length = 0;
  bot.setControlState('forward', true); await sleep(2500); bot.setControlState('forward', false); await sleep(500);
  console.log('RESULT 영혼 모래로 걸어 들어감', JSON.stringify(pk), bot.entity.position.z.toFixed(2), await ask(bot, '/attribute @s minecraft:generic.movement_speed get', /Value/)); pk.length = 0;
  await chat(bot, '/tp @s 12.5 -60 -8.5 0 0', 1500);
  console.log('RESULT 잔디로 순간이동', JSON.stringify(pk), await ask(bot, '/attribute @s minecraft:generic.movement_speed get', /Value/));
  bot.quit(); await sleep(300); process.exit(0);
})();
