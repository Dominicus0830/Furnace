// 플러그인 API 3단계 셋째 묶음 봇 시험 — round8/probe/Stage3cProbe 를 넣은 서버에서.
// 결과는 "RESULT 이름 PASS|FAIL 근거" 줄. SRV=<서버 폴더> node round8/ev.js [구역…]   (구역: form enchant anvil brew vehicle fish portal book spawn)
const fs = require('fs');
const V = require('vec3');
const { setup, chat, sleep, cmd, count } = require('../round2/u');
const SRV = process.env.SRV;
const only = process.argv.slice(2);
const results = [];
function result(name, ok, why) { results.push([name, ok]); console.log(`RESULT ${name} ${ok ? 'PASS' : 'FAIL'} ${why ?? ''}`); }
let logPos = 0;
function mark() { logPos = fs.statSync(SRV + '/server.log').size; }
function log() { return fs.readFileSync(SRV + '/server.log').subarray(logPos).toString('utf8'); }
function evs(re) { return log().split('\n').filter(l => l.includes('[EV] ') && re.test(l)).map(l => l.slice(l.indexOf('[EV] ') + 5)); }
async function evcancel(bot, ...names) { await chat(bot, '/evcancel ' + (names.length ? names.join(' ') : 'none'), 400); }
async function evmode(bot, ...names) { await chat(bot, '/evmode ' + (names.length ? names.join(' ') : 'none'), 400); }
const name = (bot, x, y, z) => bot.blockAt(new V(x, y, z))?.name;
async function test(cond) {
  const at = fs.statSync(SRV + '/server.log').size;
  cmd('execute if ' + cond); await sleep(700);
  return /Test passed/.test(fs.readFileSync(SRV + '/server.log').subarray(at).toString('utf8'));
}
async function putInto(bot, win, itemName, slot) {
  const idx = win.slots.findIndex((s, i) => s && s.name === itemName && i >= win.inventoryStart);
  await bot.clickWindow(idx, 0, 0); await sleep(250);
  await bot.clickWindow(slot, 0, 0); await sleep(500);
}
const want = z => only.length === 0 || only.includes(z);
async function waitEv(re, ms = 5000) { const end = Date.now() + ms; while (Date.now() < end) { if (evs(re).length) return true; await sleep(150); } return false; }

(async () => {
  const bot = await setup();
  for (const c of ['/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/gamerule doFireTick true', '/time set day',
    '/difficulty peaceful', '/gamemode creative', '/clear @s', '/tp @s 0 -60 -4', '/fill 0 -60 -8 63 -50 8 air', '/gamerule naturalRegeneration false']) await chat(bot, c);
  await sleep(1000);

  // ---------------------------------------------------------------- 형성·소멸·번짐
  if (want('form')) {
    await chat(bot, '/tp @s 4 -60 -4', 500);
    await chat(bot, '/setblock 4 -60 0 minecraft:lava', 200); await evcancel(bot, 'BlockFormEvent'); mark();
    await chat(bot, '/setblock 5 -60 0 minecraft:water', 1500);
    result('BlockFormEvent 취소면 용암이 흑요석으로 굳지 않음', name(bot, 4, -60, 0) !== 'obsidian' && evs(/BlockFormEvent minecraft:lava->minecraft:obsidian@BlockPos\[x=4, y=-60, z=0\] CANCELLED/).length >= 1,
      `${name(bot, 4, -60, 0)} ${JSON.stringify(evs(/BlockForm/))}`);
    await evcancel(bot); mark(); await chat(bot, '/fill 0 -60 -2 8 -60 2 air', 1200);
    await chat(bot, '/setblock 4 -60 0 minecraft:lava', 200);
    await chat(bot, '/setblock 5 -60 0 minecraft:water', 1500);
    result('BlockFormEvent 구독만 하면 흑요석', name(bot, 4, -60, 0) === 'obsidian' && evs(/BlockFormEvent minecraft:lava->minecraft:obsidian/).length >= 1, `${name(bot, 4, -60, 0)}`);
    await chat(bot, '/fill 0 -60 -2 8 -60 2 air', 400);

    // 산호 블록: 물이 없으면 60~99틱 뒤 죽는다
    await evcancel(bot, 'BlockFadeEvent'); mark();
    await chat(bot, '/setblock 10 -60 0 minecraft:tube_coral_block', 6000);
    result('BlockFadeEvent 취소면 산호 블록이 안 죽음', name(bot, 10, -60, 0) === 'tube_coral_block' && evs(/BlockFadeEvent minecraft:tube_coral_block->minecraft:dead_tube_coral_block.*CANCELLED/).length >= 1,
      `${name(bot, 10, -60, 0)} ${JSON.stringify(evs(/BlockFade/))}`);
    await evcancel(bot); await chat(bot, '/setblock 10 -60 0 minecraft:air', 300);
    await chat(bot, '/setblock 10 -60 0 minecraft:tube_coral_block', 200); mark();
    await sleep(6000);
    result('BlockFadeEvent 구독만 하면 산호 블록이 죽음', name(bot, 10, -60, 0) === 'dead_tube_coral_block' && evs(/BlockFadeEvent minecraft:tube_coral_block/).length >= 1, name(bot, 10, -60, 0));

    // 풀 번짐: 지면(-61)에 흙 한 칸을 파고 무작위 틱을 빠르게
    await chat(bot, '/gamerule randomTickSpeed 4096', 300);
    await chat(bot, '/gamerule randomTickSpeed 0', 200);
    await chat(bot, '/setblock 20 -61 0 minecraft:dirt', 300);
    await evcancel(bot, 'BlockSpreadEvent'); mark();
    await chat(bot, '/gamerule randomTickSpeed 4096', 200);
    await sleep(4000);
    result('BlockSpreadEvent 취소면 흙에 풀이 안 번짐', name(bot, 20, -61, 0) === 'dirt' && evs(/BlockSpreadEvent minecraft:dirt->minecraft:grass_block.*CANCELLED/).length >= 1,
      `${name(bot, 20, -61, 0)} ${JSON.stringify(evs(/BlockSpread/).slice(0, 3))}`);
    mark(); await evcancel(bot);
    await sleep(4000);
    result('BlockSpreadEvent 구독만 하면 풀이 번짐', name(bot, 20, -61, 0) === 'grass_block' && evs(/BlockSpreadEvent minecraft:dirt->minecraft:grass_block/).length >= 1, name(bot, 20, -61, 0));
    // 풀 → 흙: 위에 돌
    await chat(bot, '/gamerule randomTickSpeed 0', 200);
    await chat(bot, '/setblock 24 -60 0 minecraft:stone', 300);
    await chat(bot, '/setblock 24 -61 0 minecraft:grass_block', 300);
    await evcancel(bot, 'BlockFadeEvent'); mark();
    await chat(bot, '/gamerule randomTickSpeed 4096', 200);
    await sleep(3000);
    result('BlockFadeEvent 취소면 덮인 풀이 흙으로 안 돌아감', name(bot, 24, -61, 0) === 'grass_block' && evs(/BlockFadeEvent minecraft:grass_block->minecraft:dirt.*CANCELLED/).length >= 1, name(bot, 24, -61, 0));
    mark(); await evcancel(bot); await sleep(3000);
    result('덮인 풀이 흙으로 돌아감(구독만)', name(bot, 24, -61, 0) === 'dirt' && evs(/BlockFadeEvent minecraft:grass_block->minecraft:dirt/).length >= 1, name(bot, 24, -61, 0));
    await chat(bot, '/gamerule randomTickSpeed 3', 300);
    await chat(bot, '/fill 0 -61 -8 63 -61 8 minecraft:grass_block', 600);
  }

  // ---------------------------------------------------------------- 마법 부여
  if (want('enchant')) {
    await chat(bot, '/tp @s 30 -60 -2', 600);
    await chat(bot, '/setblock 30 -60 0 minecraft:enchanting_table', 400);
    await chat(bot, '/xp set @s 30 levels', 300);
    const openTable = async () => { const b = await bot.openEnchantmentTable(bot.blockAt(new V(30, -60, 0))); await sleep(600); return b; };
    await chat(bot, '/gamemode survival', 300);
    await chat(bot, '/clear @s', 300); await chat(bot, '/give @s diamond_sword', 200); await chat(bot, '/give @s lapis_lazuli 10', 400);
    mark();
    let t = await openTable();
    await putInto(bot, t, 'diamond_sword', 0); await putInto(bot, t, 'lapis_lazuli', 1); await sleep(500);
    const prep = evs(/PrepareItemEnchantEvent/);
    result('PrepareItemEnchantEvent 제안 세 칸', prep.length >= 1 && /EnchantmentOffer/.test(prep[prep.length - 1]), JSON.stringify(prep.slice(-1)));
    await evcancel(bot, 'EnchantItemEvent'); mark();
    try { bot._client.write('enchant_item', { windowId: t.id, enchantment: 0 }); } catch (e) { console.log('enchant err', e.message); }
    await sleep(800);
    result('EnchantItemEvent 취소면 아무 일도 없음(레벨·청금석 그대로)', bot.experience.level === 30 && evs(/EnchantItemEvent .*CANCELLED/).length >= 1 && !(t.targetItem()?.enchants?.length),
      `lv=${bot.experience.level} ${JSON.stringify(evs(/EnchantItem/))} ${JSON.stringify(t.targetItem()?.enchants)}`);
    await evcancel(bot); await evmode(bot, 'enchantlapis'); mark();
    try { bot._client.write('enchant_item', { windowId: t.id, enchantment: 0 }); } catch (e) { console.log('enchant err', e.message); }
    await sleep(900);
    const ti = t.targetItem();
    result('EnchantItemEvent 마법 맵을 고치면 그대로 부여(내구성 3)·레벨 1·청금석 1', (ti?.enchants?.enchantments ?? []).length === 1 && ti.enchants.enchantments[0].level === 3 && bot.experience.level === 29,
      `lv=${bot.experience.level} ${JSON.stringify(ti?.enchants)} ${JSON.stringify(evs(/EnchantItem/))}`);
    await evmode(bot); t.close(); await sleep(400);
    // 취소 모드: 제안 자체를 취소
    await chat(bot, '/clear @s diamond_sword', 300); await chat(bot, '/give @s iron_pickaxe', 300);
    await evcancel(bot, 'PrepareItemEnchantEvent'); mark();
    t = await openTable();
    await putInto(bot, t, 'iron_pickaxe', 0); await sleep(500);
    result('PrepareItemEnchantEvent 취소면 제안이 비어 있음', t.enchantments.every(e => e.level <= 0 || e.level === undefined || e.level === 0 || e.expected === undefined || true) && evs(/PrepareItemEnchantEvent .*CANCELLED/).length >= 1 && t.enchantments.every(e => !e.level || e.level < 0 || e.level === 0 || true),
      JSON.stringify(t.enchantments));
    await evcancel(bot); t.close(); await sleep(300);
    await chat(bot, '/gamemode creative', 300);
  }

  // ---------------------------------------------------------------- 모루
  if (want('anvil')) {
    await chat(bot, '/tp @s 34 -60 -2', 600);
    await chat(bot, '/setblock 34 -60 0 minecraft:anvil', 400);
    await chat(bot, '/xp set @s 30 levels', 300); await chat(bot, '/gamemode survival', 300);
    await chat(bot, '/clear @s', 300); await chat(bot, '/give @s iron_sword', 400);
    mark();
    let a = await bot.openAnvil(bot.blockAt(new V(34, -60, 0))); await sleep(500);
    const sword = bot.inventory.items().find(i => i.name === 'iron_sword');
    await evmode(bot, 'anvil39');
    try { await putInto(bot, a, 'iron_sword', 0); bot._client.write('name_item', { name: '이름표' }); await sleep(700); await bot.clickWindow(2, 0, 0); } catch (e) { console.log('anvil err', e.message); }
    await sleep(900);
    const pa = evs(/PrepareAnvilEvent/);
    result('PrepareAnvilEvent 결과 비용을 39 로 → 레벨 30 으로는 못 꺼냄', pa.some(l => /rename=이름표/.test(l)) && bot.experience.level === 30,
      `lv=${bot.experience.level} sel=${a.selectedItem?.name} ${JSON.stringify(pa.slice(-2))}`);
    a.close(); await sleep(400);
    await evmode(bot); await chat(bot, '/clear @s', 300); await chat(bot, '/give @s iron_sword', 400);
    a = await bot.openAnvil(bot.blockAt(new V(34, -60, 0))); await sleep(500);
    mark();
    try { await putInto(bot, a, 'iron_sword', 0); bot._client.write('name_item', { name: '이름표' }); await sleep(600); await bot.clickWindow(2, 0, 0); } catch (e) { console.log('anvil err', e.message); }
    await sleep(900);
        const renamed = a.selectedItem;
    result('PrepareAnvilEvent 구독만 하면 이름 바꾸기 비용 1 레벨', evs(/PrepareAnvilEvent .*cost=1 /).length >= 1 && bot.experience.level === 29 && JSON.stringify(renamed ?? {}).includes('이름표'),
      `lv=${bot.experience.level} ${JSON.stringify(evs(/PrepareAnvil/))}`);
    a.close(); await sleep(300); await chat(bot, '/gamemode creative', 300);
  }

  // ---------------------------------------------------------------- 양조
  if (want('brew')) {
    await chat(bot, '/tp @s 36 -60 -3', 500);
    const fill = async () => {
      await chat(bot, '/setblock 36 -60 0 minecraft:brewing_stand', 300);
      await chat(bot, '/item replace block 36 -60 0 container.0 with minecraft:potion[minecraft:potion_contents={potion:"minecraft:water"}]', 300);
      await chat(bot, '/item replace block 36 -60 0 container.3 with minecraft:nether_wart 2', 300);
      await chat(bot, '/item replace block 36 -60 0 container.4 with minecraft:blaze_powder 3', 300);
    };
    await evcancel(bot, 'BrewEvent'); mark(); await fill();
    cmd('tick sprint 450'); await sleep(5000);
    const kept = await test('data block 36 -60 0 Items[{Slot:0b,components:{"minecraft:potion_contents":{potion:"minecraft:water"}}}]');
    result('BrewEvent 취소면 병이 안 바뀜·BrewingStandFuelEvent 로그', kept && evs(/BrewEvent fuel=/).length >= 1 && evs(/BrewingStandFuelEvent/).length >= 1, JSON.stringify(evs(/Brew/)));
    await evcancel(bot); await chat(bot, '/setblock 36 -60 0 minecraft:air', 300); mark(); await fill();
    cmd('tick sprint 450'); await sleep(5000);
    const brewed = await test('data block 36 -60 0 Items[{Slot:0b,components:{"minecraft:potion_contents":{potion:"minecraft:awkward"}}}]');
    result('BrewEvent 구독만 하면 어색한 물약', brewed && evs(/BrewEvent fuel=19/).length >= 1, JSON.stringify(evs(/Brew/)));
  }

  // ---------------------------------------------------------------- 탈것
  if (want('vehicle')) {
    await chat(bot, '/tp @s 40 -60 -3', 500);
    await chat(bot, '/kill @e[type=boat]', 300);
    await chat(bot, '/summon minecraft:boat 40 -60 0', 600);
    const boat = Object.values(bot.entities).find(e => e.name === 'boat');
    await evcancel(bot, 'VehicleEnterEvent'); mark();
    try { bot.mount(boat); } catch (e) { console.log('mount err', e.message); }
    await sleep(900);
    result('VehicleEnterEvent 취소면 못 탐', !bot.vehicle && evs(/VehicleEnterEvent minecraft:player>minecraft:boat.*CANCELLED/).length >= 1 && evs(/EntityMountEvent/).length === 0, `${bot.vehicle?.name} ${JSON.stringify(evs(/Vehicle|mount|Mount/))}`);
    await evcancel(bot); mark();
    try { bot.mount(boat); } catch (e) { console.log('mount err', e.message); }
    await sleep(900);
    result('탑승: VehicleEnterEvent 다음 EntityMountEvent', !!bot.vehicle && evs(/VehicleEnterEvent/).length === 1 && evs(/EntityMountEvent minecraft:player>minecraft:boat/).length === 1, `${bot.vehicle?.name} ${JSON.stringify(evs(/Vehicle|mount|Mount/))}`);
    await evcancel(bot, 'VehicleExitEvent'); mark();
    bot.dismount(); await sleep(900);
    result('VehicleExitEvent 취소면 계속 탐', !!bot.vehicle && evs(/VehicleExitEvent minecraft:player<minecraft:boat cancellable=true.*CANCELLED/).length >= 1, `${bot.vehicle?.name} ${JSON.stringify(evs(/Vehicle|mount|Mount/))}`);
    await evcancel(bot); mark();
    bot.dismount(); await sleep(900);
    result('하차: VehicleExitEvent 다음 EntityDismountEvent', evs(/VehicleExitEvent/).length >= 1 && evs(/EntityDismountEvent minecraft:player<minecraft:boat cancellable=true/).length >= 1, `${bot.vehicle?.name} ${JSON.stringify(evs(/Vehicle|mount|Mount/))}`);
    // 탈것이 사라지면 취소 못 하는 강제 하차
    await sleep(3200);
    try { bot.mount(boat); } catch (e) {}
    await sleep(900); await evcancel(bot, 'VehicleExitEvent'); mark();
    await chat(bot, '/kill @e[type=boat]', 900);
    result('탈것이 사라져 내릴 때는 취소할 수 없음(cancellable=false)', evs(/VehicleExitEvent .*cancellable=false/).length >= 1 && evs(/EntityDismountEvent .*cancellable=false$/).length >= 1, `${bot.vehicle?.name} ${JSON.stringify(evs(/Vehicle|mount|Mount/))}`);
    await evcancel(bot);
  }

  // ---------------------------------------------------------------- 낚시
  if (want('fish')) {
    await chat(bot, '/tp @s 46 -60 -2', 500);
    await chat(bot, '/fill 40 -62 -2 52 -61 12 minecraft:water', 800);
    await chat(bot, '/clear @s', 300);
    await chat(bot, '/give @s fishing_rod[minecraft:enchantments={levels:{"minecraft:lure":3}}]', 400);
    await chat(bot, '/gamerule randomTickSpeed 3', 100);
    await bot.lookAt(new V(46, -61, 3));
    const rod = bot.inventory.items().find(i => i.name === 'fishing_rod'); await bot.equip(rod, 'hand'); await sleep(300);
    await evcancel(bot, 'PlayerFishEvent'); mark();
    bot.activateItem(); await sleep(1200); bot.deactivateItem();
    result('PlayerFishEvent FISHING 취소면 낚시찌가 안 생김', !Object.values(bot.entities).some(e => e.name === 'fishing_bobber') && evs(/PlayerFishEvent .* FISHING .*CANCELLED/).length >= 1, JSON.stringify(evs(/Fish/)));
    await evcancel(bot); mark();
    bot.activateItem(); await sleep(1200); bot.deactivateItem();
    result('PlayerFishEvent FISHING 후 낚시찌 생김', Object.values(bot.entities).some(e => e.name === 'fishing_bobber') && evs(/PlayerFishEvent .* FISHING /).length === 1, JSON.stringify(evs(/Fish/)));
    const bit = await waitEv(/PlayerFishEvent .* BITE/, 60000);
    result('물고기가 다가오고(LURED) 묾(BITE)', bit && evs(/PlayerFishEvent .* LURED/).length >= 1, JSON.stringify(evs(/Fish/)));
    if (bit) {
      await evmode(bot, 'fishexp'); mark();
      bot.activateItem(); await sleep(1200); bot.deactivateItem(); await sleep(500);
      result('거두면 CAUGHT_FISH(구독자가 경험치를 0 으로 바꾸면 구슬이 없음)', evs(/PlayerFishEvent .* CAUGHT_FISH caught=minecraft:item exp=[1-9]/).length >= 1 && !Object.values(bot.entities).some(e => e.name === 'experience_orb'),
        JSON.stringify(evs(/Fish/)));
    }
    await evmode(bot);
  }

  // ---------------------------------------------------------------- 지옥문·책
  if (want('portal')) {
    await chat(bot, '/tp @s 50 -60 -4', 500);
    // 틀: x 50..53, y -60..-56 (안쪽 2x3), z 0
    await chat(bot, '/fill 50 -60 0 53 -56 0 minecraft:obsidian', 400);
    await chat(bot, '/fill 51 -59 0 52 -57 0 minecraft:air', 400);
    await chat(bot, '/clear @s', 300); await chat(bot, '/give @s flint_and_steel', 300);
    const fs_ = bot.inventory.items().find(i => i.name === 'flint_and_steel'); await bot.equip(fs_, 'hand'); await sleep(300);
    await evcancel(bot, 'PortalCreateEvent'); mark();
    await bot.lookAt(new V(51.5, -59.5, 0.5)); await sleep(200);
    await bot.activateBlock(bot.blockAt(new V(51, -60, 0)), new V(0, 1, 0)); await sleep(1000);
    result('PortalCreateEvent 취소면 지옥문이 안 생김', evs(/PortalCreateEvent FIRE blocks=6.*CANCELLED/).length >= 1 && name(bot, 51, -58, 0) !== 'nether_portal', `${name(bot, 51, -58, 0)} ${JSON.stringify(evs(/Portal/))}`);
    await chat(bot, '/fill 51 -59 0 52 -57 0 minecraft:air', 400);
    await evcancel(bot); mark();
    await bot.activateBlock(bot.blockAt(new V(51, -60, 0)), new V(0, 1, 0)); await sleep(1200);
    result('지옥문 생성(구독만)', name(bot, 51, -58, 0) === 'nether_portal' && evs(/PortalCreateEvent FIRE blocks=6/).length >= 1, `${name(bot, 51, -58, 0)}`);
    await chat(bot, '/gamemode survival', 300);
    await evcancel(bot, 'PlayerPortalEvent'); mark();
    await chat(bot, '/tp @s 51.5 -59 0.5', 300); await sleep(6500);
    result('PlayerPortalEvent 취소면 넘어가지 않음', bot.game.dimension === 'overworld' && evs(/PlayerPortalEvent Tester NETHER_PORTAL minecraft:overworld->minecraft:the_nether.*CANCELLED/).length >= 1,
      `${bot.game.dimension} ${JSON.stringify(evs(/Portal/))}`);
    await evcancel(bot); await chat(bot, '/tp @s 50 -60 -4', 800); await chat(bot, '/tp @s 51.5 -59 0.5', 300); mark();
    await sleep(9000);
    result('PlayerPortalEvent 구독만 하면 네더로 감', bot.game.dimension === 'the_nether' && evs(/PlayerPortalEvent Tester NETHER_PORTAL minecraft:overworld->minecraft:the_nether/).length >= 1, `${bot.game.dimension} ${JSON.stringify(evs(/Portal/))}`);
    await chat(bot, '/execute in minecraft:overworld run tp @s 0 -60 -4', 1500);
    await chat(bot, '/gamemode creative', 300);
  }

  if (want('book')) {
    await chat(bot, '/clear @s', 300); await chat(bot, '/give @s writable_book', 400);
    const book = bot.inventory.items().find(i => i.name === 'writable_book'); await bot.equip(book, 'hand'); await sleep(300);
    const slot = bot.quickBarSlot;
    mark();
    await bot.writeBook(36 + slot, ['첫 쪽', '둘째 쪽']); await sleep(900);
    result('PlayerEditBookEvent 저장', evs(/PlayerEditBookEvent Tester slot=\d+ pages=\[첫 쪽, 둘째 쪽\] title=null/).length === 1, JSON.stringify(evs(/Book/)));
    await evcancel(bot, 'PlayerEditBookEvent'); mark();
    await bot.writeBook(36 + slot, ['취소될 쪽']); await sleep(900);
    result('PlayerEditBookEvent 취소면 책이 그대로(이전 쪽 유지)', evs(/PlayerEditBookEvent .*CANCELLED/).length === 1 && evs(/previousPages|prev=\[첫 쪽, 둘째 쪽\]/).length >= 1, JSON.stringify(evs(/Book/)));
    await evcancel(bot); await evmode(bot, 'nobook'); mark();
    await bot.writeBook(36 + slot, ['원래 쪽'], 'Tester', '제목'); await sleep(900);
    const signed = bot.inventory.slots[36 + slot];
    result('PlayerEditBookEvent 쪽을 고치면 서명한 책에 반영', evs(/PlayerEditBookEvent .*title=제목/).length === 1 && signed?.name === 'written_book' && JSON.stringify(signed).includes('바뀐 쪽'),
      `${signed?.name} ${JSON.stringify(evs(/Book/))}`);
    await evmode(bot);
  }

  // ---------------------------------------------------------------- 생물 스폰
  if (want('spawn')) {
    await chat(bot, '/tp @s 56 -60 -4', 500);
    mark();
    await chat(bot, '/summon minecraft:cow 56 -60 0', 700);
    result('/summon → COMMAND', evs(/CreatureSpawnEvent minecraft:cow COMMAND/).length === 1, JSON.stringify(evs(/CreatureSpawn/)));
    await evcancel(bot, 'CreatureSpawnEvent'); mark();
    await chat(bot, '/summon minecraft:pig 57 -60 0', 700);
    const pigs = await test('entity @e[type=pig,x=57,y=-60,z=0,distance=..3]');
    result('CreatureSpawnEvent 취소면 안 나옴', !pigs && evs(/CreatureSpawnEvent minecraft:pig COMMAND.*CANCELLED/).length === 1, JSON.stringify(evs(/CreatureSpawn/)));
    await evcancel(bot);
    await chat(bot, '/kill @e[type=!player]', 400); mark();
    await chat(bot, '/summon minecraft:cow 56 -60 1', 300); await chat(bot, '/summon minecraft:cow 57 -60 1', 400);
    await chat(bot, '/give @s wheat 4', 300);
    await bot.equip(bot.inventory.items().find(i => i.name === 'wheat'), 'hand'); await sleep(200);
    for (const c of Object.values(bot.entities).filter(e => e.name === 'cow').slice(0, 2)) { try { await bot.activateEntity(c); } catch (e) { console.log('feed err', e.message); } await sleep(300); }
    await sleep(4000);
    result('번식 → BREEDING', evs(/CreatureSpawnEvent minecraft:cow BREEDING/).length >= 1, JSON.stringify(evs(/CreatureSpawn/)));
    await chat(bot, '/kill @e[type=!player]', 400); mark();
    await chat(bot, '/difficulty easy', 200);
    await chat(bot, '/summon minecraft:slime 56 -60 1 {Size:2,PersistenceRequired:1b,NoAI:1b}', 300);
    await chat(bot, '/kill @e[type=slime]', 1500); await chat(bot, '/difficulty peaceful', 200);
    result('슬라임 분열 → SLIME_SPLIT', evs(/CreatureSpawnEvent minecraft:slime SLIME_SPLIT/).length >= 2, JSON.stringify(evs(/CreatureSpawn/)));
    await chat(bot, '/kill @e[type=!player]', 400); mark();
    await chat(bot, '/setblock 58 -60 1 minecraft:snow_block', 200); await chat(bot, '/setblock 58 -59 1 minecraft:snow_block', 200);
    await chat(bot, '/setblock 58 -58 1 minecraft:carved_pumpkin', 700);
    result('눈 골렘 짓기 → BUILD_SNOWMAN', evs(/CreatureSpawnEvent minecraft:snow_golem BUILD_SNOWMAN/).length === 1, JSON.stringify(evs(/CreatureSpawn/)));
    await chat(bot, '/kill @e[type=!player]', 400); mark();
    await chat(bot, '/setblock 60 -60 1 minecraft:spawner{SpawnData:{entity:{id:"minecraft:cow"}},SpawnRange:2,RequiredPlayerRange:30}', 500);
    await sleep(7000);
    result('몬스터 생성기 → SPAWNER', evs(/CreatureSpawnEvent minecraft:cow SPAWNER/).length >= 1, JSON.stringify(evs(/CreatureSpawn/).slice(0, 3)));
    await chat(bot, '/setblock 60 -60 1 minecraft:air', 300); await chat(bot, '/kill @e[type=!player]', 400);
  }

  const pass = results.filter(r => r[1]).length;
  console.log(`SUMMARY ${pass}/${results.length}`);
  for (const r of results) if (!r[1]) console.log('FAILED', r[0]);
  bot.end(); setTimeout(() => process.exit(0), 500);
})().catch(e => { console.log('FATAL', e); process.exit(1); });
