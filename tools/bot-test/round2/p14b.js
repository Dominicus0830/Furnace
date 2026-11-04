const { setup, chat, sleep, ask } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  for (const c of ['/gamemode survival', '/effect give @s resistance 1000 4 true']) await chat(bot, c);
  console.log('RESULT fillbiome', await ask(bot, '/fillbiome -16 -64 -16 15 0 15 minecraft:snowy_plains', /./, 3000)?.then?.(x=>x) );
  console.log('RESULT 0,-61,0 is dirt?', await ask(bot, '/execute if block 0 -61 0 minecraft:dirt', /./));
  for (let t = 0; t < 3; t++) {
    const x = 4 + t * 2;
    await chat(bot, `/setblock ${x} -61 0 minecraft:farmland[moisture=7]`, 300);
    await chat(bot, `/tp @s ${x}.5 -56 0.5`, 2500);
    console.log('RESULT 낙하', t, await ask(bot, `/execute if block ${x} -61 0 minecraft:farmland`, /./), bot.blockAt(new V(x, -61, 0))?.name, bot.entity.onGround);
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
