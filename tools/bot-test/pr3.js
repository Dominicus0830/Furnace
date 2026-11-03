const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const V = require('vec3');
const near = (bot, name, pred = () => true) => Object.values(bot.entities).filter(e => e.name === name && pred(e)).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
async function ask(bot, c, re = /./, ms = 3000) { const t = Date.now(); bot.chat(c); return await waitMsg(bot, re, ms, t); }
(async () => {
  const bot = makeBot('Tester');
  await spawned(bot); await sleep(1500);
  cmd('op Tester'); await sleep(800);
  await chat(bot, '/gamerule doMobSpawning false');
  await chat(bot, '/time set day');
  await chat(bot, '/fill 10 -61 10 24 -61 24 minecraft:lava', 800);
  await chat(bot, '/tp @s 9 -60 17 -90 0', 1500);
  // 1) 안장
  await chat(bot, '/summon minecraft:strider 12 -61 17 {NoAI:1b}', 1000);
  let s = near(bot, 'strider')[0]; console.log('strider id', s?.id, s?.position);
  await chat(bot, '/give @s minecraft:saddle 2', 800);
  await bot.equip(bot.inventory.items().find(i => i.name === 'saddle'), 'hand');
  await bot.lookAt(s.position.offset(0, 1, 0)); await sleep(300);
  await bot.activateEntity(s); await sleep(800);
  console.log('RESULT 안장 후 saddle 수', bot.inventory.items().filter(i => i.name === 'saddle').reduce((a, b) => a + b.count, 0));
  console.log('RESULT 안장 data', await ask(bot, '/data get entity @e[type=strider,limit=1,sort=nearest] saddle', /strider|Strider|data|has|entity/i));
  // 새끼
  await chat(bot, '/summon minecraft:strider 12 -61 20 {NoAI:1b,Age:-24000}', 1000);
  const baby = near(bot, 'strider', e => e.position.z > 19)[0];
  await bot.lookAt(baby.position.offset(0, 0.5, 0)); await sleep(300);
  await bot.activateEntity(baby); await sleep(800);
  console.log('RESULT 새끼 안장 후 saddle 수', bot.inventory.items().filter(i => i.name === 'saddle').reduce((a, b) => a + b.count, 0));
  // 뒤틀린 균류 먹이(안장 없는 다른 어른)
  await chat(bot, '/summon minecraft:strider 12 -61 14 {NoAI:1b}', 1000);
  const adult2 = near(bot, 'strider', e => e.position.z < 15)[0];
  await chat(bot, '/give @s minecraft:warped_fungus 4', 600);
  await bot.equip(bot.inventory.items().find(i => i.name === 'warped_fungus'), 'hand');
  await bot.lookAt(adult2.position.offset(0, 1, 0)); await sleep(300);
  await bot.activateEntity(adult2); await sleep(800);
  console.log('RESULT 균류 먹인 뒤 개수', bot.inventory.items().filter(i => i.name === 'warped_fungus').reduce((a, b) => a + b.count, 0), 'vehicle', !!bot.vehicle);
  console.log('RESULT InLove', await ask(bot, '/data get entity @e[type=strider,limit=1,sort=nearest,x=12,y=-61,z=14,distance=..1] InLove', /strider|Strider|InLove|has/i));
  // 2) 타기 (안장 얹은 첫 스트라이더, 빈손)
  await chat(bot, '/clear @s minecraft:warped_fungus', 300);
  bot.setQuickBarSlot(5); await sleep(300);
  await chat(bot, '/data merge entity @e[type=strider,limit=1,sort=nearest,x=12,y=-61,z=17,distance=..1] {NoAI:0b}', 500);
  s = near(bot, 'strider', e => Math.abs(e.position.z - 17) < 1)[0];
  await bot.lookAt(s.position.offset(0, 1, 0)); await sleep(300);
  bot.mount(s); await sleep(1500);
  console.log('RESULT 탑승', !!bot.vehicle, bot.vehicle?.name);
  // 3) 조종: 막대를 들고 동쪽(+x) 바라보기
  await chat(bot, '/give @s minecraft:warped_fungus_on_a_stick 1', 600);
  await bot.equip(bot.inventory.items().find(i => i.name === 'warped_fungus_on_a_stick'), 'hand'); await sleep(300);
  await bot.look(-Math.PI / 2, 0, true);  // yaw -90° → +x 쪽
  const p0 = bot.vehicle.position.clone();
  for (let i = 0; i < 30; i++) { await bot.look(-Math.PI / 2, 0, true); await sleep(100); }
  const p1 = bot.vehicle.position.clone();
  console.log('RESULT 조종 3초 이동', p0, '→', p1, 'dx', (p1.x - p0.x).toFixed(2), 'dz', (p1.z - p0.z).toFixed(2));
  // 가속(우클릭)
  const dur = () => { const it = bot.heldItem; return it?.components?.find(c => c.type === 'damage')?.data ?? it?.durabilityUsed ?? 0; };
  console.log('RESULT 가속 전 내구 손상', dur());
  bot.activateItem(); await sleep(200); bot.deactivateItem(); await sleep(300);
  console.log('RESULT 가속 1회 후 내구 손상', dur());
  bot.activateItem(); await sleep(200); bot.deactivateItem(); await sleep(300);
  console.log('RESULT 가속 중 재클릭 후 내구 손상', dur());
  const p2 = bot.vehicle.position.clone();
  for (let i = 0; i < 20; i++) { await bot.look(Math.PI / 2, 0, true); await sleep(100); }
  const p3 = bot.vehicle.position.clone();
  console.log('RESULT 가속 중 2초 이동(서쪽)', (p3.x - p2.x).toFixed(2));
  // 4) 내리기 — 스트라이더를 웅덩이 가운데로 옮기고 내림
  await chat(bot, '/tp @e[type=strider,limit=1,sort=nearest] 17 -61 17', 1500);
  await sleep(1000);
  bot.dismount(); await sleep(1500);
  const bp = bot.entity.position;
  const under = bot.blockAt(bp.offset(0, -0.5, 0))?.name, at = bot.blockAt(bp)?.name;
  console.log('RESULT 가운데 내림 위치', bp, '발밑', under, '현재칸', at);
  // 가장자리에서 내리기
  await chat(bot, '/tp @s 10 -60 17', 800);
  await chat(bot, '/tp @e[type=strider,limit=1,sort=nearest,x=17,y=-61,z=17,distance=..3] 10.5 -61 17.5', 1200);
  s = near(bot, 'strider', e => e.position.distanceTo(new V(10.5, -61, 17.5)) < 2)[0];
  bot.setQuickBarSlot(6); await sleep(200);
  await bot.lookAt(s.position.offset(0, 1, 0)); bot.mount(s); await sleep(1200);
  console.log('RESULT 재탑승', !!bot.vehicle);
  bot.dismount(); await sleep(1500);
  const ep = bot.entity.position;
  console.log('RESULT 가장자리 내림 위치', ep, '발밑', bot.blockAt(ep.offset(0, -0.5, 0))?.name);
  // 5) 떨기: 땅 위 스트라이더 (메타데이터 보기)
  await chat(bot, '/summon minecraft:strider 4 -60 4 {NoAI:1b}', 1500);
  const cold = near(bot, 'strider', e => e.position.distanceTo(new V(4, -60, 4)) < 1.5)[0];
  console.log('RESULT 땅 위 스트라이더 메타', JSON.stringify(cold?.metadata?.slice(16)));
  const warm = near(bot, 'strider', e => Math.abs(e.position.z - 14) < 1)[0];
  console.log('RESULT 용암 위 스트라이더 메타', JSON.stringify(warm?.metadata?.slice(16)));
  // 6) 죽으면 안장
  await chat(bot, '/kill @e[type=strider,limit=1,sort=nearest,x=10.5,y=-61,z=17.5,distance=..3]', 1500);
  const drops = Object.values(bot.entities).filter(e => e.name === 'item').map(e => { try { return e.getDroppedItem()?.name } catch { return '?' } });
  console.log('RESULT 처치 후 바닥 아이템', JSON.stringify(drops));
  bot.quit(); await sleep(800); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
