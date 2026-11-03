const { setup, chat, sleep, ask } = require('./u');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode survival', '/clear @s', '/effect clear @s']) await chat(bot, c);
  await chat(bot, '/tp @s 12.5 -60 -4.5 0 0', 1500);
  await chat(bot, '/item replace entity @s armor.feet with minecraft:diamond_boots[enchantments={levels:{"minecraft:soul_speed":3}}]', 800);
  bot.setControlState('forward', true); await sleep(1500); bot.setControlState('forward', false); await sleep(500);
  console.log('RESULT speed get', await ask(bot, '/attribute @s minecraft:generic.movement_speed get', /attribute|Value|value/i));
  console.log('RESULT eff get', await ask(bot, '/attribute @s minecraft:generic.movement_efficiency get', /attribute|Value|value/i));
  console.log('RESULT modifier', await ask(bot, '/attribute @s minecraft:generic.movement_speed modifier value get minecraft:enchantment.soul_speed/feet', /./));
  console.log('RESULT tag', await ask(bot, '/execute if block 12 -61 0 #minecraft:soul_speed_blocks', /passed|failed|Unknown/));
  console.log('RESULT ench', await ask(bot, '/data get entity @s Inventory[{Slot:100b}]', /entity data/));
  bot.quit(); await sleep(300); process.exit(0);
})();
