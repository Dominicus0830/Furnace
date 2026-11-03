const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const rcon = require('./rcon');
const V = require('vec3');
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  // 1) RCON IP당 상한
  const cs = [];
  for (let i = 0; i < 5; i++) cs.push(await rcon.connect('testpw'));
  console.log('RESULT RCON 5개 동시: 인증', cs.map(c => c.authed), '닫힘', cs.map(c => c.closed));
  console.log('RESULT RCON 1번 명령', JSON.stringify(await rcon.command(cs[0], 'list')));
  cs[0].s.destroy(); await sleep(800);
  const c6 = await rcon.connect('testpw');
  console.log('RESULT RCON 하나 닫은 뒤 새 연결: 인증', c6.authed, '닫힘', c6.closed);
  for (const c of [...cs, c6]) c.s.destroy();
  // 2) 봇: 채널 등록, 클라이언트 이름
  const bot = makeBot('Tester');
  const serverRegs = [], sounds = [], vd = [], sd = []; let unloads = 0;
  bot._client.on('custom_payload', p => { if (/register/.test(p.channel)) serverRegs.push(p.channel + ':' + p.data.toString().split('\0').join(',')); });
  bot._client.on('entity_sound_effect', p => sounds.push(p));
  bot._client.on('update_view_distance', p => vd.push(p.viewDistance));
  bot._client.on('simulation_distance', p => sd.push(p.distance));
  bot._client.on('unload_chunk', () => unloads++);
  await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  console.log('RESULT 서버가 보낸 register', JSON.stringify(serverRegs));
  bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from('fabric:test\0mymod:chan') });
  await sleep(500);
  console.log('RESULT probe', await ask(bot, '/probe', /PROBE/));
  bot._client.write('custom_payload', { channel: 'minecraft:unregister', data: Buffer.from('mymod:chan') });
  await sleep(500);
  console.log('RESULT probe 해제 후', await ask(bot, '/probe', /PROBE/));
  // 3) 소리
  await chat(bot, '/gamerule doMobSpawning false');
  await chat(bot, '/tp @s 0 -60 0 0 0', 800);
  const ids = () => Object.fromEntries(Object.values(bot.entities).map(e => [e.id, e.name]));
  const soundName = id => { const s = bot.registry.soundsArray?.[id - 1] ?? bot.registry.sounds?.[id - 1]; return s?.name ?? ('#' + id); };
  const report = (label, since) => { const m = ids(); const list = sounds.slice(since).map(p => `${p.soundEvent?.soundName ?? soundName(p.soundId ?? p.sound?.soundId)}@${m[p.entityId] ?? p.entityId} cat=${p.soundCategory}`); console.log('RESULT 소리', label, JSON.stringify(list)); };
  await chat(bot, '/summon minecraft:sheep 0 -60 2 {NoAI:1b}', 1000);
  await chat(bot, '/give @s minecraft:shears', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'shears'), 'hand');
  let n = sounds.length; let sheep = Object.values(bot.entities).find(e => e.name === 'sheep');
  await bot.lookAt(sheep.position.offset(0, 0.8, 0)); await bot.activateEntity(sheep); await sleep(800); report('양 털 깎기', n);
  await chat(bot, '/summon minecraft:pig 2 -60 0 {NoAI:1b}', 1000);
  await chat(bot, '/give @s minecraft:saddle', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'saddle'), 'hand');
  n = sounds.length; const pig = Object.values(bot.entities).find(e => e.name === 'pig');
  await bot.lookAt(pig.position.offset(0, 0.6, 0)); await bot.activateEntity(pig); await sleep(800); report('돼지 안장', n);
  await chat(bot, '/give @s minecraft:goat_horn[minecraft:instrument="minecraft:ponder_goat_horn"]', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'goat_horn'), 'hand');
  n = sounds.length; bot.activateItem(); await sleep(800); bot.deactivateItem(); await sleep(300); report('염소 뿔', n);
  await chat(bot, '/give @s minecraft:trident', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'trident'), 'hand');
  await bot.look(0, 0.3, true);
  n = sounds.length; bot.activateItem(); await sleep(1200); bot.deactivateItem(); await sleep(800); report('삼지창 던지기', n);
  // 4) 시야/시뮬레이션 거리
  const u0 = unloads;
  console.log('RESULT vd', await ask(bot, '/vd 2', /PROBE/)); await sleep(2000);
  console.log('RESULT vd 4 후 패킷', JSON.stringify(vd), 'unload 수', unloads - u0);
  console.log('RESULT vd', await ask(bot, '/vd 6', /PROBE/)); await sleep(2000);
  console.log('RESULT sd', await ask(bot, '/sd 3', /PROBE/)); await sleep(1000);
  console.log('RESULT 거리 패킷 vd', JSON.stringify(vd), 'sd', JSON.stringify(sd));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
