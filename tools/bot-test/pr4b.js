const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  const bot = makeBot('Tester'), obs = makeBot('Watcher');
  const sounds = { Tester: [], Watcher: [] }; let unloads = 0, loads = 0;
  for (const [b, k] of [[bot, 'Tester'], [obs, 'Watcher']]) b._client.on('entity_sound_effect', p => sounds[k].push(p));
  bot._client.on('unload_chunk', () => unloads++); bot._client.on('map_chunk', () => loads++);
  await spawned(bot); await spawned(obs); await sleep(1500);
  const sn = p => { const id = p.soundId ?? p.sound?.soundId ?? p.soundEvent; const r = bot.registry.sounds[(typeof id === 'number' ? id : 0) - 1]; return (r?.name ?? JSON.stringify(p.soundEvent ?? id)); };
  const names = () => Object.fromEntries(Object.values(bot.entities).map(e => [e.id, e.name === 'player' ? e.username : e.name]));
  const rep = (label, k, since) => { const m = names(); console.log('RESULT 소리', label, k, JSON.stringify(sounds[k].slice(since).map(p => `${sn(p)}@${m[p.entityId] ?? p.entityId} cat=${p.soundCategory}`))); };
  console.log('raw sample keys', Object.keys(bot.registry.sounds).slice(0, 3), bot.registry.sounds[1]);
  await chat(bot, '/tp Watcher 1 -60 -2', 500);
  await chat(bot, '/tp @s 0 -60 0 0 0', 800);
  await chat(bot, '/kill @e[type=!player]', 500);
  await chat(bot, '/summon minecraft:sheep 0 -60 2 {NoAI:1b}', 1000);
  await chat(bot, '/give @s minecraft:shears', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'shears'), 'hand');
  let a = sounds.Tester.length, b = sounds.Watcher.length; const sheep = Object.values(bot.entities).find(e => e.name === 'sheep');
  await bot.lookAt(sheep.position.offset(0, 0.8, 0)); await bot.activateEntity(sheep); await sleep(800); rep('양 털 깎기', 'Tester', a); rep('양 털 깎기', 'Watcher', b);
  await chat(bot, '/give @s minecraft:goat_horn[minecraft:instrument="minecraft:ponder_goat_horn"]', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'goat_horn'), 'hand');
  a = sounds.Tester.length; b = sounds.Watcher.length; bot.activateItem(); await sleep(800); bot.deactivateItem(); await sleep(300); rep('염소 뿔', 'Tester', a); rep('염소 뿔', 'Watcher', b);
  await chat(bot, '/give @s minecraft:trident', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'trident'), 'hand'); await bot.look(0, 0.3, true);
  a = sounds.Tester.length; b = sounds.Watcher.length; bot.activateItem(); await sleep(1200); bot.deactivateItem(); await sleep(800); rep('삼지창', 'Tester', a); rep('삼지창', 'Watcher', b);
  // 시야 거리
  await sleep(2000);
  const cols = () => Object.keys(bot.world.async.columns || {}).length;
  console.log('RESULT 청크 수(전)', cols(), 'loads', loads);
  const u0 = unloads, l0 = loads;
  await ask(bot, '/vd 2', /PROBE/); await sleep(4000);
  console.log('RESULT vd 2 뒤 unload', unloads - u0, '청크 수', cols());
  const l1 = loads; await ask(bot, '/vd 6', /PROBE/); await sleep(4000);
  console.log('RESULT vd 6 뒤 새 로드', loads - l1, '청크 수', cols());
  bot.quit(); obs.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
