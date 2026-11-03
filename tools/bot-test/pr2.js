const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const phase = process.argv[2];
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(1500);
  const get = async (attr) => { const t = Date.now(); bot.chat(`/attribute @s ${attr} get`); return await waitMsg(bot, new RegExp('attribute|속성|Value|value', 'i'), 3000, t); };
  if (phase === '1') {
    cmd('op Tester'); await sleep(800);
    await chat(bot, '/attribute @s minecraft:generic.max_health base set 40');
    await chat(bot, '/effect give @s minecraft:instant_health 1 10', 1500);
    await chat(bot, '/attribute @s minecraft:generic.scale modifier add test:big 1 add_value');
    await chat(bot, '/effect give @s minecraft:speed 600 1');
    await chat(bot, '/setblock 3 -60 3 minecraft:diamond_block');
    await chat(bot, '/setblock 200 -60 200 minecraft:gold_block');
    await chat(bot, '/tp @s 200 -59 202', 3000);
    await chat(bot, '/tp @s 0 -59 0', 2000);
  }
  console.log('RESULT health', bot.health);
  console.log('RESULT max_health', await get('minecraft:generic.max_health'));
  console.log('RESULT scale', await get('minecraft:generic.scale'));
  console.log('RESULT speed', await get('minecraft:generic.movement_speed'));
  console.log('RESULT speed base', await (async () => { const t = Date.now(); bot.chat('/attribute @s minecraft:generic.movement_speed base get'); return await waitMsg(bot, /./, 3000, t); })());
  const a = bot.entity.attributes || {}; for (const k of Object.keys(a)) console.log('RESULT attr', k, a[k].value, JSON.stringify(a[k].modifiers));
  console.log('RESULT block 3,-60,3', bot.blockAt(new (require('vec3'))(3, -60, 3))?.name);
  bot.quit(); await sleep(800); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
