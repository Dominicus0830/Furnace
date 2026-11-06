// 플러그인 API 3단계 둘째 묶음(블록·인벤토리 이벤트) 봇 시험 — round5/probe/BlockEventProbe 를 넣은 서버에서.
// 결과는 "RESULT 이름 PASS|FAIL 근거" 줄. SRV=<서버 폴더> node round5/ev.js
// 각 항목은 "이벤트가 오는가 → 취소(또는 값 바꾸기)가 먹는가 → 구독만 하고 건드리지 않으면 바닐라대로인가" 를 본다.
const fs = require('fs');
const V = require('vec3');
const { setup, chat, sleep, cmd, count } = require('../round2/u');
const SRV = process.env.SRV;
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
let logPos = 0;
function mark() { logPos = fs.statSync(SRV + '/server.log').size; }
function log() { return fs.readFileSync(SRV + '/server.log').subarray(logPos).toString('utf8'); }   // 바이트 위치(로그에 한글이 있다)
function evs(re) { return log().split('\n').filter(l => l.includes('[EV] ') && re.test(l)).map(l => l.slice(l.indexOf('[EV] ') + 5)); }
async function evcancel(bot, ...names) { await chat(bot, '/evcancel ' + (names.length ? names.join(' ') : 'none'), 400); }
async function evmode(bot, ...names) { await chat(bot, '/evmode ' + (names.length ? names.join(' ') : 'none'), 400); }
const name = (bot, x, y, z) => bot.blockAt(new V(x, y, z))?.name;
const props = (bot, x, y, z) => bot.blockAt(new V(x, y, z))?.getProperties() ?? {};
/** 콘솔 execute if … 결과(server.log 의 Test passed/failed). 이벤트 로그 위치(mark)는 건드리지 않는다. */
async function test(cond) {
  const at = fs.statSync(SRV + '/server.log').size;
  cmd('execute if ' + cond); await sleep(700);
  return /Test passed/.test(fs.readFileSync(SRV + '/server.log').subarray(at).toString('utf8'));
}
// 주의: execute if block <칸> <블록>{NBT} 의 NBT 조건은 Furnace 에서 늘 실패해서(별도 확인 필요) 용기 내용은 execute if data 로 본다.

(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/gamerule doFireTick true', '/time set day',
    '/difficulty peaceful', '/gamemode creative', '/clear @s', '/tp @s 0 -60 -4', '/fill 0 -60 -8 63 -50 8 air']) await chat(bot, c);
  await sleep(1000);

  // ---------------------------------------------------------------- 물리
  await chat(bot, '/setblock 4 -60 0 minecraft:stone', 200); await chat(bot, '/setblock 4 -59 0 minecraft:torch', 500);
  await evcancel(bot, 'BlockPhysicsEvent'); mark();
  await chat(bot, '/setblock 4 -60 0 minecraft:air', 800);
  result('BlockPhysicsEvent 취소면 받침 없는 횃불이 남음', name(bot, 4, -59, 0) === 'torch' && evs(/BlockPhysicsEvent minecraft:torch.*-59.*CANCELLED/).length >= 1,
    `${name(bot, 4, -59, 0)} ${JSON.stringify(evs(/BlockPhysicsEvent minecraft:torch/))}`);
  await evcancel(bot);
  await chat(bot, '/setblock 6 -60 0 minecraft:stone', 200); await chat(bot, '/setblock 6 -59 0 minecraft:torch', 500);
  mark(); await chat(bot, '/setblock 6 -60 0 minecraft:air', 800);
  result('BlockPhysicsEvent 구독만 하면 횃불이 떨어짐', name(bot, 6, -59, 0) === 'air' && evs(/BlockPhysicsEvent minecraft:torch/).length >= 1, name(bot, 6, -59, 0));
  await chat(bot, '/setblock 4 -59 0 minecraft:air', 300);

  // ---------------------------------------------------------------- 레드스톤
  await chat(bot, '/setblock 8 -60 0 minecraft:lever[face=floor]', 300);
  await chat(bot, '/tp @s 8 -60 -2', 800);
  await evmode(bot, 'redstone0'); mark();
  await bot.activateBlock(bot.blockAt(new V(8, -60, 0))); await sleep(800);
  result('BlockRedstoneEvent 새 세기 0 이면 레버가 안 켜짐', props(bot, 8, -60, 0).powered === false && evs(/BlockRedstoneEvent minecraft:lever.* 0->15/).length === 1,
    `${JSON.stringify(props(bot, 8, -60, 0))} ${JSON.stringify(evs(/Redstone/))}`);
  await evmode(bot); mark();
  await bot.activateBlock(bot.blockAt(new V(8, -60, 0))); await sleep(800);
  result('레버 켜짐(구독만)', props(bot, 8, -60, 0).powered === true && evs(/BlockRedstoneEvent minecraft:lever.* 0->15/).length === 1, JSON.stringify(props(bot, 8, -60, 0)));
  await chat(bot, '/setblock 10 -60 0 minecraft:redstone_wire', 300);
  mark(); await chat(bot, '/setblock 11 -60 0 minecraft:redstone_block', 800);
  result('BlockRedstoneEvent 가루 0->15', evs(/BlockRedstoneEvent minecraft:redstone_wire.*\(10, -60, 0\)|BlockRedstoneEvent minecraft:redstone_wire@.*0->15/).length >= 1, JSON.stringify(evs(/redstone_wire/)));
  await chat(bot, '/setblock 12 -60 0 minecraft:redstone_lamp', 300);
  await evmode(bot, 'redstone0'); mark();
  await chat(bot, '/setblock 13 -60 0 minecraft:redstone_block', 800);
  result('램프: 새 세기 0 이면 안 켜짐', props(bot, 12, -60, 0).lit === false && evs(/redstone_lamp.*0->15/).length >= 1, `${JSON.stringify(props(bot, 12, -60, 0))} ${JSON.stringify(evs(/lamp/))}`);
  await evmode(bot);

  await chat(bot, '/tp @s 0 -60 -4', 600);
  // ---------------------------------------------------------------- 피스톤
  await chat(bot, '/setblock 16 -60 0 minecraft:piston[facing=east]', 200); await chat(bot, '/setblock 17 -60 0 minecraft:stone', 300);
  await evcancel(bot, 'BlockPistonExtendEvent'); mark();
  await chat(bot, '/setblock 15 -60 0 minecraft:redstone_block', 1200);
  result('BlockPistonExtendEvent 취소면 안 늘어남', props(bot, 16, -60, 0).extended === false && name(bot, 17, -60, 0) === 'stone'
    && evs(/BlockPistonExtendEvent .* east sticky=false blocks=1 CANCELLED/).length === 1, `${JSON.stringify(props(bot, 16, -60, 0))} ${JSON.stringify(evs(/Piston/))}`);
  await evcancel(bot); await chat(bot, '/setblock 15 -60 0 minecraft:air', 400); mark();
  await chat(bot, '/setblock 15 -60 0 minecraft:redstone_block', 1500);
  result('피스톤 늘어남(구독만)', props(bot, 16, -60, 0).extended === true && name(bot, 18, -60, 0) === 'stone' && evs(/BlockPistonExtendEvent/).length === 1, JSON.stringify(evs(/Piston/)));
  mark(); await chat(bot, '/setblock 15 -60 0 minecraft:air', 1500);
  result('BlockPistonRetractEvent 일반 피스톤(빈 목록)', props(bot, 16, -60, 0).extended === false && evs(/BlockPistonRetractEvent .* sticky=false blocks=0$/).length === 1, JSON.stringify(evs(/Piston/)));
  await chat(bot, '/setblock 20 -60 0 minecraft:sticky_piston[facing=east]', 200); await chat(bot, '/setblock 21 -60 0 minecraft:stone', 200);
  await chat(bot, '/setblock 19 -60 0 minecraft:redstone_block', 1500);
  await evcancel(bot, 'BlockPistonRetractEvent'); mark();
  await chat(bot, '/setblock 19 -60 0 minecraft:air', 1500);
  result('끈끈이 줄어듦 취소면 블록이 안 끌려옴', props(bot, 20, -60, 0).extended === false && name(bot, 22, -60, 0) === 'stone' && name(bot, 21, -60, 0) === 'air'
    && evs(/BlockPistonRetractEvent .* sticky=true blocks=1 CANCELLED/).length === 1, `${name(bot, 21, -60, 0)}/${name(bot, 22, -60, 0)} ${JSON.stringify(evs(/Piston/))}`);
  await evcancel(bot);

  // ---------------------------------------------------------------- 물 흐름
  await evcancel(bot, 'BlockFromToEvent'); mark();
  await chat(bot, '/setblock 26 -58 0 minecraft:water', 2500);
  result('BlockFromToEvent 취소면 안 흐름', name(bot, 26, -59, 0) === 'air' && name(bot, 27, -58, 0) === 'air' && evs(/BlockFromToEvent minecraft:water.* down CANCELLED/).length >= 1,
    `${name(bot, 26, -59, 0)} ${JSON.stringify(evs(/FromTo/).slice(0, 3))}`);
  await evcancel(bot); await chat(bot, '/setblock 26 -58 0 minecraft:air', 300); mark();
  await chat(bot, '/setblock 30 -58 0 minecraft:water', 2500);
  result('물 흐름(구독만)', name(bot, 30, -59, 0) === 'water' && evs(/BlockFromToEvent minecraft:water.* down/).length >= 1, name(bot, 30, -59, 0));
  await chat(bot, '/fill 25 -60 -4 36 -58 4 air', 500);

  // ---------------------------------------------------------------- 성장(뼛가루)
  await chat(bot, '/gamemode survival', 300);
  await chat(bot, '/setblock 34 -61 0 minecraft:farmland', 200); await chat(bot, '/setblock 34 -60 0 minecraft:wheat', 200);
  await chat(bot, '/tp @s 34 -60 -2', 600);
  await chat(bot, '/give @s minecraft:bone_meal 8', 600);
  await bot.equip(bot.inventory.items().find(i => i.name === 'bone_meal'), 'hand'); await sleep(200);
  await evcancel(bot, 'BlockGrowEvent'); mark();
  await bot.activateBlock(bot.blockAt(new V(34, -60, 0))); await sleep(800);
  result('BlockGrowEvent 취소면 밀이 안 자람', Number(props(bot, 34, -60, 0).age) === 0 && evs(/BlockGrowEvent minecraft:wheat\[age=0\]->minecraft:wheat\[age=\d\].*CANCELLED/).length === 1,
    `${JSON.stringify(props(bot, 34, -60, 0))} ${JSON.stringify(evs(/Grow/))}`);
  await evcancel(bot); mark();
  await bot.activateBlock(bot.blockAt(new V(34, -60, 0))); await sleep(800);
  result('밀이 자람(구독만)', Number(props(bot, 34, -60, 0).age) > 0 && evs(/BlockGrowEvent/).length === 1, JSON.stringify(props(bot, 34, -60, 0)));

  // ---------------------------------------------------------------- 불: 부싯돌·태움·잎
  await chat(bot, '/setblock 38 -60 0 minecraft:stone', 300);
  await chat(bot, '/give @s minecraft:flint_and_steel', 600);
  await bot.equip(bot.inventory.items().find(i => i.name === 'flint_and_steel'), 'hand'); await sleep(200);
  await chat(bot, '/tp @s 38 -60 -2', 600);
  await evcancel(bot, 'BlockIgniteEvent'); mark();
  await bot.activateBlock(bot.blockAt(new V(38, -60, 0)), new V(0, 1, 0)); await sleep(800);
  const dmg1 = bot.heldItem?.durabilityUsed ?? bot.heldItem?.nbt;
  result('BlockIgniteEvent(부싯돌) 취소면 불 없음', name(bot, 38, -59, 0) === 'air' && evs(/BlockIgniteEvent FLINT_AND_STEEL@.*player=Tester CANCELLED/).length === 1,
    `${name(bot, 38, -59, 0)} ${JSON.stringify(evs(/Ignite/))} dmg=${JSON.stringify(dmg1)}`);
  await evcancel(bot); mark();
  await bot.activateBlock(bot.blockAt(new V(38, -60, 0)), new V(0, 1, 0)); await sleep(800);
  result('부싯돌 불(구독만)', name(bot, 38, -59, 0) === 'fire' && evs(/BlockIgniteEvent FLINT_AND_STEEL/).length === 1, name(bot, 38, -59, 0));
  await chat(bot, '/setblock 38 -59 0 minecraft:air', 300);
  await chat(bot, '/gamemode creative', 300);

  // 태움: 판자 기둥 위 불(불 틱 30~40 틱마다, 옆 판자를 태울 확률이 꽤 있다) — 틱을 빠르게 돌린다
  await chat(bot, '/fill 42 -60 -1 44 -58 1 minecraft:oak_planks', 300);
  await chat(bot, '/setblock 43 -59 0 minecraft:air', 200);
  await evcancel(bot, 'BlockBurnEvent', 'BlockIgniteEvent'); mark();
  await chat(bot, '/setblock 43 -59 0 minecraft:fire', 300);
  await chat(bot, '/tick sprint 4000', 6000);
  const burnEv = evs(/BlockBurnEvent minecraft:oak_planks/);
  const planks = await test('block 43 -60 0 minecraft:oak_planks') && await test('block 43 -58 0 minecraft:oak_planks') && await test('block 42 -59 0 minecraft:oak_planks');
  result('BlockBurnEvent 취소면 판자가 안 탐', burnEv.length >= 1 && burnEv.every(l => l.endsWith('CANCELLED')) && planks, `${burnEv.length} ${planks}`);
  const spread = evs(/BlockIgniteEvent SPREAD/);
  result('BlockIgniteEvent(번짐) 오고 취소됨', spread.length >= 1 && spread.every(l => l.endsWith('CANCELLED')), `${spread.length} ${JSON.stringify(spread.slice(0, 2))}`);
  await evcancel(bot); await chat(bot, '/fill 41 -61 -2 45 -57 2 air', 300); await chat(bot, '/fill 41 -61 -2 45 -61 2 minecraft:grass_block', 300);

  // 잎: 떨어진 잎(distance=7) + 빠른 무작위 틱
  await chat(bot, '/setblock 48 -60 0 minecraft:oak_leaves[distance=7]', 300);
  await evcancel(bot, 'LeavesDecayEvent'); mark();
  await chat(bot, '/gamerule randomTickSpeed 4096', 2500);
  result('LeavesDecayEvent 취소면 잎이 남음', name(bot, 48, -60, 0) === 'oak_leaves' && evs(/LeavesDecayEvent minecraft:oak_leaves.*CANCELLED/).length >= 1, JSON.stringify(evs(/Leaves/).slice(0, 2)));
  mark(); await evcancel(bot); await sleep(2500);
  result('잎이 사라짐(구독만)', name(bot, 48, -60, 0) === 'air' && evs(/LeavesDecayEvent/).length >= 1, name(bot, 48, -60, 0));
  await chat(bot, '/gamerule randomTickSpeed 3', 300);

  // ---------------------------------------------------------------- 화로
  await chat(bot, '/tp @s 52 -60 -3', 800);
  await chat(bot, '/setblock 52 -60 0 minecraft:furnace', 300);
  await evcancel(bot, 'FurnaceBurnEvent'); mark();
  await chat(bot, '/item replace block 52 -60 0 container.0 with minecraft:raw_iron 2', 200);
  await chat(bot, '/item replace block 52 -60 0 container.1 with minecraft:coal 1', 1500);
  result('FurnaceBurnEvent 취소면 불이 안 켜지고 석탄 그대로', props(bot, 52, -60, 0).lit === false && await test('data block 52 -60 0 Items[{Slot:1b,id:"minecraft:coal",count:1}]')
    && evs(/FurnaceBurnEvent minecraft:furnace fuel=minecraft:coal time=1600 CANCELLED/).length >= 1, JSON.stringify(evs(/FurnaceBurn/).slice(0, 2)));
  await evmode(bot, 'smeltdiamond'); mark(); await evcancel(bot);
  await chat(bot, '/tick sprint 260', 3000);
  result('FurnaceBurnEvent → 불 켜짐', evs(/FurnaceBurnEvent minecraft:furnace fuel=minecraft:coal time=1600$/).length === 1, JSON.stringify(evs(/FurnaceBurn/)));
  result('FurnaceSmeltEvent 결과를 다이아몬드로', evs(/FurnaceSmeltEvent minecraft:furnace minecraft:raw_iron->minecraft:iron_ingot/).length === 1
    && await test('data block 52 -60 0 Items[{Slot:2b,id:"minecraft:diamond"}]'), JSON.stringify(evs(/Smelt/)));
  await chat(bot, '/item replace block 52 -60 0 container.2 with minecraft:air', 300);   // 다이아몬드가 있으면 철 주괴와 합칠 수 없어 굽지 않는다(바닐라)
  await evmode(bot); await evcancel(bot, 'FurnaceSmeltEvent'); mark();
  await chat(bot, '/tick sprint 220', 3000);
  result('FurnaceSmeltEvent 취소면 재료가 남음', evs(/FurnaceSmeltEvent.*CANCELLED/).length >= 1 && await test('data block 52 -60 0 Items[{Slot:0b,id:"minecraft:raw_iron",count:1}]'), JSON.stringify(evs(/Smelt/)));
  await evcancel(bot);

  // ---------------------------------------------------------------- 깔때기
  await chat(bot, '/setblock 56 -60 0 minecraft:chest', 200); await chat(bot, '/setblock 56 -59 0 minecraft:hopper', 300);
  await evcancel(bot, 'InventoryMoveItemEvent'); mark();
  await chat(bot, '/item replace block 56 -59 0 container.0 with minecraft:stick 3', 1200);
  result('InventoryMoveItemEvent 취소면 상자가 빔', !(await test('data block 56 -60 0 Items[0]')) && evs(/InventoryMoveItemEvent minecraft:hopper->minecraft:chest minecraft:stick src=true CANCELLED/).length >= 1,
    JSON.stringify(evs(/Move/).slice(0, 2)));
  await evmode(bot, 'movediamond'); mark(); await evcancel(bot); await sleep(400);
  await evmode(bot); await sleep(1500);
  result('InventoryMoveItemEvent 아이템 바꾸기', await test('data block 56 -60 0 Items[{id:"minecraft:diamond"}]'), JSON.stringify(evs(/Move/).slice(0, 3)));
  await chat(bot, '/setblock 58 -59 0 minecraft:chest', 200); await chat(bot, '/setblock 58 -60 0 minecraft:hopper', 200);
  mark(); await chat(bot, '/item replace block 58 -59 0 container.0 with minecraft:apple 1', 1200);
  result('깔때기 빨아들이기(구독만)', await test('data block 58 -60 0 Items[{id:"minecraft:apple"}]') && evs(/InventoryMoveItemEvent minecraft:chest->minecraft:hopper minecraft:apple src=false$/).length === 1, JSON.stringify(evs(/Move/)));

  // ---------------------------------------------------------------- 제작(인벤토리 2×2)
  await chat(bot, '/gamemode survival', 300);
  await chat(bot, '/clear @s', 300);
  await chat(bot, '/give @s minecraft:oak_log 2', 800);
  const planksRecipe = bot.recipesFor(bot.registry.itemsByName.oak_planks.id, null, 1, null)[0];
  await evcancel(bot, 'CraftItemEvent'); mark();
  try { await bot.craft(planksRecipe, 1, null); } catch (e) { console.log('craft(취소)', e.message); }
  await sleep(800);
  result('PrepareItemCraftEvent 결과 알림', evs(/PrepareItemCraftEvent Tester minecraft:oak_planks minecraft:oak_planks/).length >= 1, JSON.stringify(evs(/Prepare/).slice(0, 3)));
  result('CraftItemEvent 취소면 판자가 안 생김', count(bot, 'oak_planks') === 0 && evs(/CraftItemEvent Tester minecraft:oak_planks minecraft:oak_planksx4 .* CANCELLED/).length >= 1,
    `planks=${count(bot, 'oak_planks')} log=${count(bot, 'oak_log')} ${JSON.stringify(evs(/CraftItem/))}`);
  await evcancel(bot); bot.closeWindow(bot.inventory); await sleep(300); mark();
  try { await bot.craft(planksRecipe, 1, null); } catch (e) { console.log('craft', e.message); }
  await sleep(800);
  result('제작됨(구독만)', count(bot, 'oak_planks') === 4 && evs(/CraftItemEvent .*oak_planksx4/).length >= 1, `planks=${count(bot, 'oak_planks')} ${JSON.stringify(evs(/CraftItem/))}`);
  await evmode(bot, 'craftdiamond'); mark();
  try { await bot.craft(planksRecipe, 1, null); } catch (e) { console.log('craft(다이아)', e.message); }
  await sleep(800);
  result('PrepareItemCraftEvent 결과를 다이아몬드로', count(bot, 'diamond') >= 1 && evs(/CraftItemEvent .*minecraft:diamondx1/).length >= 1, `diamond=${count(bot, 'diamond')} ${JSON.stringify(evs(/CraftItem/))}`);
  await evmode(bot);

  const fail = results.filter(r => !r[1]).length;
  console.log(`SUMMARY ${results.length - fail}/${results.length} PASS`);
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error('SCRIPT ERROR', e); process.exit(1); });
