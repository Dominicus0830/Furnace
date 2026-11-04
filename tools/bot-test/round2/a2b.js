const { setup, chat, sleep, ask, num, near, count, hold, uuidInts } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode survival', '/clear @s', '/kill @e[type=!player]', '/difficulty peaceful']) await chat(bot, c);
  // 양동이 다시: 잡고 → 놓기
  await chat(bot, '/fill -5 -61 -5 5 -61 5 minecraft:water', 500);
  await chat(bot, '/tp @s 0.5 -60 -5.5 0 30', 800);
  await chat(bot, '/summon minecraft:axolotl 0.5 -61 -3.5 {NoAI:1b,Variant:3,CustomName:\'"Bob"\',Age:-12000}', 600);
  await chat(bot, '/give @s minecraft:water_bucket', 400); await hold(bot, 'water_bucket');
  let e = near(bot, 'axolotl')[0]; await bot.lookAt(e.position); await sleep(200); await bot.activateEntity(e); await sleep(700);
  console.log('RESULT 잡은 양동이 컴포넌트', JSON.stringify(bot.inventory.items().find(i => i.name === 'axolotl_bucket')?.components?.map(c => c.type)));
  await chat(bot, '/kill @e[type=axolotl]', 300);
  await bot.lookAt(new V(2.5, -61, -3.5)); await sleep(300);
  console.log('보는 블록', bot.blockAtCursor(5)?.name, bot.blockAtCursor(5)?.position);
  bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(1000);
  console.log('RESULT 놓은 뒤 손', bot.heldItem?.name, '아홀로틀', await ask(bot, '/data get entity @e[type=axolotl,limit=1,sort=nearest]', /entity data|Found|No entity/));
  // 돼지 밀기(AI 있으나 속도 0)
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/tp @s 30 -60 -5', 600);
  for (let i = 0; i < 5; i++) await chat(bot, '/summon minecraft:pig 30.5 -60 0.5 {attributes:[{id:"minecraft:generic.movement_speed",base:0d}]}', 60);
  await sleep(3000);
  const ps = near(bot, 'pig').map(e => e.position); const spread = Math.max(...ps.map(a => Math.max(...ps.map(b => a.distanceTo(b)))));
  console.log('RESULT 같은 자리 속도0 돼지 5마리 3초 뒤 최대 간격', spread.toFixed(2));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 고양이 죽음 메시지
  await chat(bot, `/summon minecraft:cat 121 -60 3 {NoAI:1b,Owner:${OWN},CustomName:'"Tom"'}`, 500);
  await chat(bot, '/summon minecraft:husk 121 -60 5 {NoAI:1b}', 500); await chat(bot, '/tp @s 121 -60 0', 600);
  const t6 = Date.now(); await chat(bot, '/damage @e[type=cat,limit=1] 100 minecraft:mob_attack by @e[type=husk,limit=1]', 800);
  console.log('RESULT 고양이 죽음 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t6).map(m => m.m)));
  await chat(bot, '/kill @e[type=!player]', 300); await sleep(500); await chat(bot, '/kill @e[type=item]', 300);
  // 셜커
  await chat(bot, '/gamemode creative', 300); await chat(bot, '/tp @s 140 -60 0 0 30', 600);
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', 500);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(1000);
  console.log('RESULT 크리에이티브로 부순 내용물 셜커 드롭', await ask(bot, '/data get entity @e[type=item,limit=1,sort=nearest] Item', /entity data|Found|No entity/));
  // 스켈레톤 머리(밤)
  await chat(bot, '/time set midnight', 300); await chat(bot, '/difficulty easy', 300); await chat(bot, '/tp @s 220 -60 -20', 800);
  await chat(bot, '/summon minecraft:skeleton 220.5 -60 2.5 {NoAI:1b,Health:1f,PersistenceRequired:1b}', 300);
  await chat(bot, '/summon minecraft:skeleton 221.5 -60 2.5 {NoAI:1b,Health:1f,PersistenceRequired:1b}', 300);
  await chat(bot, '/summon minecraft:creeper 220.5 -60 0.5 {powered:1b,ignited:1b,Fuse:1s,NoAI:1b}', 3000);
  console.log('RESULT 충전 크리퍼 → 스켈레톤 2마리: 머리 수', await ask(bot, '/execute if entity @e[type=item,nbt={Item:{id:"minecraft:skeleton_skull"}}]', /passed|failed/i), '스켈레톤 남음', near(bot, 'skeleton').length);
  await chat(bot, '/time set day', 300);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
