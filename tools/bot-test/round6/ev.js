// 몹 장착 소리·진동, 다른 플레이어 첨벙 소리, 워든 진동 반응, SamplePlugin 비op 깊은 곳 보호
const U = require('../round2/u'); const { chat, sleep, ask, cmd } = U; const S = require('./snd');
(async () => {
  const bot = await U.setup('Hearer'); const snd = []; S.listen(bot, snd);
  for (const c of ['/gamemode creative', '/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set midnight', '/difficulty easy', '/kill @e[type=!player]', '/tp @s 0.5 -60 0.5']) await chat(bot, c);
  await sleep(500);
  const active = async (x, y, z) => /passed/i.test(await ask(bot, `/execute if block ${x} ${y} ${z} minecraft:sculk_sensor[sculk_sensor_phase=active]`, /Test (passed|failed)/) ?? '');
  async function equip(label, summon, slot, item, sx) {
    await chat(bot, `/tp @s ${sx + 0.5} -60 -3.5`);   // 소리는 16칸 안에서만 들린다
    await chat(bot, `/setblock ${sx} -60 0 minecraft:sculk_sensor`); await chat(bot, summon, 1500);   // 소환 진동이 가라앉을 때까지
    const t = Date.now(); const r = await ask(bot, `/item replace entity @e[tag=eq,limit=1] ${slot} with ${item}`, /./, 1500);
    let on = false; for (let i = 0; i < 8 && !on; i++) { on = await active(sx, -60, 0); if (!on) await sleep(150); }
    console.log('  명령 응답', r);
    console.log('RESULT 장착', label, '소리', JSON.stringify(snd.filter(s => s.t >= t).map(s => s.name)), '감지기 작동', on);
    await chat(bot, '/kill @e[tag=eq]'); await chat(bot, `/setblock ${sx} -60 0 air`);
  }
  await equip('좀비 철 투구', '/summon zombie 4.5 -60 3.5 {NoAI:1b,Silent:0b,Tags:["eq"]}', 'armor.head', 'iron_helmet', 4);
  await equip('갑옷 거치대 다이아 흉갑', '/summon armor_stand 12.5 -60 3.5 {Tags:["eq"]}', 'armor.chest', 'diamond_chestplate', 12);
  await equip('말 철 말 갑옷', '/summon horse 20.5 -60 3.5 {NoAI:1b,Tame:1b,Tags:["eq"]}', 'armor.body', 'iron_horse_armor', 20);
  await equip('늑대 늑대 갑옷', '/summon wolf 28.5 -60 3.5 {NoAI:1b,Tags:["eq"]}', 'armor.body', 'wolf_armor', 28);
  // 햇빛에 투구가 닳을 때는 소리·진동 없음
  await chat(bot, '/time set noon'); await chat(bot, '/setblock 36 -60 0 minecraft:sculk_sensor');
  await chat(bot, '/summon zombie 36.5 -60 3.5 {NoAI:1b,Tags:["sun"],ArmorItems:[{},{},{},{id:"leather_helmet",count:1}]}', 3000);
  let t = Date.now(); const ons = []; for (let i = 0; i < 16; i++) { await sleep(500); ons.push(await active(36, -60, 0)); }
  console.log('RESULT 햇빛 투구 마모 8초: 투구 손상', await ask(bot, '/data get entity @e[tag=sun,limit=1] ArmorItems[3].components."minecraft:damage"', /data:|Found no|not found|No /),
    '장착 소리', snd.filter(s => s.t >= t && /equip/.test(s.name)).length, '감지기 작동 횟수', ons.filter(Boolean).length);
  await chat(bot, '/kill @e[type=!player]'); await chat(bot, '/time set midnight');
  // 다른 플레이어 첨벙 소리
  await chat(bot, '/fill 40 -63 10 44 -61 14 water'); await chat(bot, '/tp @s 46.5 -60 12.5');
  const sp = U.makeBot('Splasher', { plugins: { resource_pack: false } }); await U.spawned(sp); await sleep(1500);
  cmd('gamemode survival Splasher'); cmd('effect give Splasher resistance 100 4 true'); cmd('tp Splasher 42.5 -50 12.5'); await sleep(200);
  t = Date.now(); await sleep(2500);
  console.log('RESULT 첨벙: Hearer 가 들은 소리', JSON.stringify(snd.filter(s => s.t >= t).map(s => s.name + '@' + s.entity)), 'Splasher id', sp.entity.id);
  // SamplePlugin 깊은 곳 보호: op 아닌 Splasher 가 y -61(보호 깊이 -60 아래) 잔디를 부순다
  cmd('tp Splasher 50.5 -60 12.5'); await sleep(800); const ab = []; sp._client.on('system_chat', p => { if (p.isActionBar) ab.push(JSON.stringify(p.content)); }); sp._client.on('action_bar', p => ab.push(JSON.stringify(p.text)));
  const V = require('vec3'); const blk = sp.blockAt(new V(50, -61, 13));
  try { await sp.lookAt(blk.position.offset(0.5, 1, 0.5)); await sp.dig(blk, true); } catch (e) { console.log('dig err', e.message); }
  await sleep(800);
  console.log('RESULT 비op 깊은 곳 부수기: 블록', await ask(bot, '/execute if block 50 -61 13 minecraft:grass_block', /Test (passed|failed)/), '액션바', ab.join(' | '));
  const blk2 = bot.blockAt(new V(2, -61, 2)); await chat(bot, '/gamemode survival'); await chat(bot, '/tp @s 2.5 -60 3.5'); await sleep(500);
  try { await bot.lookAt(blk2.position.offset(0.5, 1, 0.5)); await bot.dig(bot.blockAt(new V(2, -61, 2)), true); } catch (e) { console.log('dig err', e.message); }
  await sleep(800);
  console.log('RESULT op 깊은 곳 부수기: 블록 남음?', await ask(bot, '/execute if block 2 -61 2 minecraft:grass_block', /Test (passed|failed)/));
  sp.quit();
  // 워든: 플레이어가 던진 눈덩이가 땅에 닿는 진동에 반응(분노 대상)
  await chat(bot, '/gamemode survival'); await chat(bot, '/effect give @s resistance 600 4 true'); await chat(bot, '/tp @s 80.5 -60 0.5');
  await chat(bot, '/summon warden 80.5 -60 12.5 {Brain:{memories:{"minecraft:dig_cooldown":{value:{},ttl:1200L}}}}', 2500);
  const anger = async () => await ask(bot, '/data get entity @e[type=warden,limit=1] anger.suspects', /data:|following|Found no|No /);
  console.log('RESULT 워든 처음 분노', await anger());
  await chat(bot, '/give @s snowball 4'); await U.hold(bot, 'snowball');
  await bot.lookAt(new (require('vec3'))(80.5, -60, 8.5)); bot.activateItem(); bot.deactivateItem(); await sleep(2500);
  console.log('RESULT 눈덩이 착지 뒤 워든 분노', await anger(), '내 UUID', bot.player.uuid);
  console.log('RESULT 워든 진동 기억', await ask(bot, '/data get entity @e[type=warden,limit=1] Brain.memories."minecraft:vibration_cooldown"', /data:|following|Found no|No /));
  await chat(bot, '/kill @e[type=warden]');
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
