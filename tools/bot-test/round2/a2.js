// 엔티티: 몹 양동이, 생물 밀기, 가죽끈, 늑대 변종·갑옷, 셜커 상자, 펫 사망 메시지, 충전 크리퍼 머리
const { setup, chat, sleep, ask, num, near, count, hold, uuidInts, waitMsg } = require('./u');
const V = require('vec3');
(async () => {
  const bot = await setup();
  const OWN = uuidInts(bot.player.uuid);
  for (const c of ['/gamerule doMobSpawning false', '/time set day', '/gamemode creative', '/clear @s', '/kill @e[type=!player]', '/difficulty peaceful']) await chat(bot, c);
  // 1) 몹 양동이: 물웅덩이에 이름·변종 있는 아홀로틀
  await chat(bot, '/fill -5 -61 -5 5 -61 5 minecraft:water', 500);
  await chat(bot, '/tp @s 0.5 -60 -5.5 0 30', 800);
  await chat(bot, '/summon minecraft:axolotl 0.5 -61 -3.5 {NoAI:1b,Variant:3,CustomName:\'"Bob"\',Age:-12000}', 600);
  await chat(bot, '/summon minecraft:tropical_fish 2.5 -61 -3.5 {NoAI:1b,Variant:65536,CustomName:\'"Nemo"\'}', 600);
  await chat(bot, '/summon minecraft:tadpole -1.5 -61 -3.5 {NoAI:1b,Age:5000,CustomName:\'"Tad"\'}', 600);
  await chat(bot, '/gamemode survival', 300);
  for (const [mob, out] of [['axolotl', 'axolotl_bucket'], ['tropical_fish', 'tropical_fish_bucket'], ['tadpole', 'tadpole_bucket']]) {
    await chat(bot, '/give @s minecraft:water_bucket', 400); await hold(bot, 'water_bucket');
    const e = near(bot, mob)[0]; await bot.lookAt(e.position); await sleep(200); await bot.activateEntity(e); await sleep(700);
    const b = bot.inventory.items().find(i => i.name === out);
    console.log('RESULT', mob, '양동이로 잡음', !!b, '남은 엔티티', near(bot, mob).length);
  }
  await chat(bot, '/fill -5 -61 10 5 -61 15 minecraft:water', 500);
  await chat(bot, '/tp @s 0.5 -60 9.5 0 60', 800);
  for (const [b, x] of [["axolotl_bucket", -2], ["tropical_fish_bucket", 0], ["tadpole_bucket", 2]]) {
    await hold(bot, b); await bot.lookAt(new V(x + 0.5, -61, 10.5)); await sleep(200);
    bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(700);
  }
  console.log('RESULT 놓은 아홀로틀', await ask(bot, '/data get entity @e[type=axolotl,z=10,dz=6,limit=1]', /entity data|Found/));
  console.log('RESULT 놓은 열대어', await ask(bot, '/data get entity @e[type=tropical_fish,z=10,dz=6,limit=1]', /entity data|Found/));
  console.log('RESULT 놓은 올챙이', await ask(bot, '/data get entity @e[type=tadpole,z=10,dz=6,limit=1]', /entity data|Found/));
  await chat(bot, '/gamemode creative', 300); await chat(bot, '/kill @e[type=!player]', 300);
  // 2) 생물 밀기: 1x1 구덩이에 돼지 10마리, maxEntityCramming 4
  await chat(bot, '/fill 20 -61 0 22 -59 2 minecraft:stone', 300); await chat(bot, '/fill 21 -60 1 21 -59 1 minecraft:air', 300);
  await chat(bot, '/gamerule maxEntityCramming 4', 300);
  for (let i = 0; i < 10; i++) await chat(bot, '/summon minecraft:pig 21.5 -60 1.5', 60);
  await chat(bot, '/tp @s 24 -60 1', 300);
  await sleep(6000);
  console.log('RESULT 구덩이 돼지 수(기대: 4 이하)', near(bot, 'pig').length);
  await chat(bot, '/gamerule maxEntityCramming 24', 300); await chat(bot, '/kill @e[type=!player]', 300);
  // 넓은 곳에서 겹친 돼지가 서로 밀리는지
  for (let i = 0; i < 5; i++) await chat(bot, '/summon minecraft:pig 30.5 -60 0.5 {NoAI:1b}', 60);
  await sleep(3000);
  const ps = near(bot, 'pig').map(e => e.position); const spread = Math.max(...ps.map(a => Math.max(...ps.map(b => a.distanceTo(b)))));
  console.log('RESULT 같은 자리 NoAI 돼지 5마리 3초 뒤 최대 간격', spread.toFixed(2));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 3) 가죽끈
  let attach = []; bot._client.on('attach_entity', p => attach.push(p));
  await chat(bot, '/gamemode survival', 300); await chat(bot, '/tp @s 40 -60 0 0 0', 600);
  await chat(bot, '/summon minecraft:cow 40.5 -60 2.5 {NoAI:1b}', 600);
  await chat(bot, '/give @s minecraft:lead 2', 400); await hold(bot, 'lead');
  let cow = near(bot, 'cow')[0]; await bot.lookAt(cow.position.offset(0, 1, 0)); await sleep(200); await bot.activateEntity(cow); await sleep(600);
  console.log('RESULT 소에 끈', JSON.stringify(attach.slice(-1)), '끈 남은 수', count(bot, 'lead'));
  await chat(bot, '/setblock 42 -60 0 minecraft:oak_fence', 400);
  await bot.lookAt(new V(42.5, -59.5, 0.5)); await sleep(200);
  await bot.activateBlock(bot.blockAt(new V(42, -60, 0))); await sleep(800);
  console.log('RESULT 울타리에 묶음: leash_knot', near(bot, 'leash_knot').length, JSON.stringify(attach.slice(-1)));
  await chat(bot, '/data merge entity @e[type=cow,limit=1] {NoAI:0b}', 200);
  await chat(bot, '/tp @e[type=cow] 56 -60 0', 2500);
  console.log('RESULT 소를 14칸 떨어뜨림: 끈 아이템 드롭', near(bot, 'item').length, JSON.stringify(attach.slice(-1)));
  await chat(bot, '/kill @e[type=!player]', 300); attach = [];
  // 소 끈을 잡고 플레이어가 멀어지면
  await chat(bot, '/summon minecraft:cow 40.5 -60 2.5 {NoAI:1b}', 600); await hold(bot, 'lead');
  cow = near(bot, 'cow')[0]; await bot.lookAt(cow.position.offset(0, 1, 0)); await sleep(200); await bot.activateEntity(cow); await sleep(600);
  await chat(bot, '/tp @s 40 -60 -12', 2500);
  console.log('RESULT 플레이어가 14칸 떨어짐: attach', JSON.stringify(attach), '드롭 아이템', near(bot, 'item').length);
  await chat(bot, '/kill @e[type=!player]', 300); await chat(bot, '/tp @s 40 -60 0 0 0', 600);
  // 주민에 끈
  let win = null; bot.once('windowOpen', w => win = w.type);
  await chat(bot, '/summon minecraft:villager 40.5 -60 2.5 {NoAI:1b,VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:wheat",count:20},sell:{id:"minecraft:emerald",count:1}}]}}', 800);
  await chat(bot, '/give @s minecraft:lead', 300); await hold(bot, 'lead');
  const vil = near(bot, 'villager')[0]; await bot.lookAt(vil.position.offset(0, 1.5, 0)); await sleep(200); attach = [];
  await bot.activateEntity(vil); await sleep(1000);
  console.log('RESULT 주민에 끈: 창', win, '끈 연결', JSON.stringify(attach), '끈 수', count(bot, 'lead'));
  bot.closeWindow?.(bot.currentWindow); await chat(bot, '/kill @e[type=!player]', 300);
  // 4) 늑대 변종(바이옴별)
  for (const [biome, x] of [['minecraft:grove', 60], ['minecraft:forest', 70], ['minecraft:plains', 80], ['minecraft:snowy_taiga', 90], ['minecraft:wooded_badlands', 100]]) {
    await chat(bot, `/fillbiome ${x - 4} -64 -4 ${x + 4} -50 4 ${biome}`, 400);
    await chat(bot, `/summon minecraft:wolf ${x} -60 0 {NoAI:1b}`, 400);
    console.log('RESULT 늑대 변종', biome, await ask(bot, `/data get entity @e[type=wolf,x=${x - 2},dx=4,y=-61,dy=3,z=-2,dz=4,limit=1] variant`, /entity data|Found/));
  }
  await chat(bot, '/kill @e[type=!player]', 300);
  // 늑대 갑옷
  await chat(bot, '/tp @s 120 -60 0 0 0', 600);
  await chat(bot, `/summon minecraft:wolf 120.5 -60 2.5 {NoAI:1b,Owner:${OWN},CustomName:'"Rex"'}`, 600);
  await chat(bot, '/give @s minecraft:wolf_armor', 400); await hold(bot, 'wolf_armor');
  let w = near(bot, 'wolf')[0]; await bot.lookAt(w.position.offset(0, 0.5, 0)); await sleep(200); await bot.activateEntity(w); await sleep(600);
  console.log('RESULT 갑옷 장착 body_armor_item', await ask(bot, '/data get entity @e[type=wolf,limit=1] body_armor_item', /entity data|Found/), '갑옷 남음', count(bot, 'wolf_armor'));
  const h0 = await num(bot, '@e[type=wolf,limit=1]', 'Health');
  await chat(bot, '/damage @e[type=wolf,limit=1] 6 minecraft:mob_attack', 600);
  console.log('RESULT 갑옷 늑대 6 피해 뒤 체력', h0, '→', await num(bot, '@e[type=wolf,limit=1]', 'Health'), '갑옷', await ask(bot, '/data get entity @e[type=wolf,limit=1] body_armor_item', /entity data|Found/));
  await chat(bot, '/give @s minecraft:shears', 400); await hold(bot, 'shears');
  await bot.lookAt(w.position.offset(0, 0.5, 0)); await sleep(200); await bot.activateEntity(w); await sleep(800);
  console.log('RESULT 가위 뒤 body_armor_item', await ask(bot, '/data get entity @e[type=wolf,limit=1] body_armor_item', /entity data|Found|no element/i), '드롭', near(bot, 'item').length);
  // 5) 펫 사망 메시지
  const t5 = Date.now(); await chat(bot, '/damage @e[type=wolf,limit=1] 100 minecraft:generic', 800);
  console.log('RESULT 늑대 죽음 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t5).map(m => m.m)));
  await chat(bot, `/summon minecraft:cat 121 -60 3 {NoAI:1b,Owner:${OWN},CustomName:'"Tom"'}`, 500);
  await chat(bot, '/summon minecraft:husk 121 -60 5 {NoAI:1b,Tags:["k"]}', 500);
  const t6 = Date.now(); await chat(bot, '/damage @e[type=cat,limit=1] 100 minecraft:mob_attack by @e[type=husk,limit=1]', 800);
  console.log('RESULT 고양이 죽음 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t6).map(m => m.m)));
  await chat(bot, `/summon minecraft:parrot 121 -60 3 {NoAI:1b,Owner:${OWN},CustomName:'"Polly"'}`, 500);
  const t7 = Date.now(); await chat(bot, '/kill @e[type=parrot]', 800);
  console.log('RESULT 앵무새 /kill 메시지', JSON.stringify(bot.msgs.filter(m => m.t >= t7).map(m => m.m)));
  await chat(bot, '/kill @e[type=!player]', 300);
  // 6) 셜커 상자
  await chat(bot, '/gamemode creative', 300); await chat(bot, '/tp @s 140 -60 0 0 30', 600);
  await chat(bot, '/setblock 140 -60 2 minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}]}', 500);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(1000);
  console.log('RESULT 크리에이티브로 부순 내용물 셜커 드롭', await ask(bot, '/data get entity @e[type=item,limit=1] Item', /entity data|Found/));
  await chat(bot, '/kill @e[type=item]', 300);
  await chat(bot, '/setblock 140 -60 2 minecraft:blue_shulker_box', 300);
  await bot.dig(bot.blockAt(new V(140, -60, 2)), true).catch(e => console.log('dig err', e.message)); await sleep(1000);
  console.log('RESULT 크리에이티브로 부순 빈 셜커 드롭 수(바닐라 0)', near(bot, 'item').length);
  await chat(bot, '/clear @s', 200);
  await chat(bot, '/give @s minecraft:green_shulker_box[custom_name=\'"Stash"\',container=[{slot:0,item:{id:"minecraft:emerald",count:3}}]]', 500);
  await hold(bot, 'green_shulker_box');
  await bot.placeBlock(bot.blockAt(new V(142, -61, 2)), new V(0, 1, 0)).catch(e => console.log('place', e.message)); await sleep(800);
  console.log('RESULT 이름·내용물 셜커 설치 블록 엔티티', await ask(bot, '/data get block 142 -60 2', /block data|not a block entity|Found/));
  // 7) 충전 크리퍼 머리
  await chat(bot, '/gamemode creative', 300); await chat(bot, '/difficulty easy', 300); await chat(bot, '/tp @s 200 -60 -20', 600);
  for (const [mob, x, head] of [['zombie', 200, 'zombie_head'], ['skeleton', 220, 'skeleton_head'], ['wither_skeleton', 240, 'wither_skeleton_skull'], ['piglin', 260, 'piglin_head'], ['creeper', 280, 'creeper_head']]) {
    await chat(bot, `/summon minecraft:${mob} ${x}.5 -60 2.5 {NoAI:1b,Health:1f,PersistenceRequired:1b}`, 300);
    await chat(bot, `/summon minecraft:${mob} ${x + 1}.5 -60 2.5 {NoAI:1b,Health:1f,PersistenceRequired:1b}`, 300);
    await chat(bot, `/summon minecraft:creeper ${x}.5 -60 0.5 {powered:1b,ignited:1b,Fuse:1s,NoAI:1b,Tags:["boom"]}`, 3000);
    const heads = (await ask(bot, `/execute if entity @e[type=item,x=${x - 8},dx=16,y=-64,dy=10,z=-8,dz=16,nbt={Item:{id:"minecraft:${head}"}}]`, /passed|failed/i));
    const n = await ask(bot, `/execute store result score @s x run kill @e[type=item,x=${x - 8},dx=16,y=-64,dy=10,z=-8,dz=16,nbt={Item:{id:"minecraft:${head}"}}]`, /Killed|No entity|Found no|stored|result/i);
    console.log('RESULT 충전 크리퍼 →', mob, head, heads, n);
  }
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
