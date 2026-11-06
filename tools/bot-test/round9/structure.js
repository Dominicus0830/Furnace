// 구조물 블록·직소 블록 8차: 편집 패킷(update_structure_block / update_jigsaw_block / generate_structure)을 봇이 직접 보내
// 저장 → 파일 → 불러오기 → 회전 → 크기 감지 → 신호 → 권한 → 재시작 뒤 파일 불러오기, 직소 블록 설정·(노이즈 월드에서)생성을 확인한다.
// 사용: SRV=$D node structure.js flat 1  (재시작) SRV=$D node structure.js flat 2   /  SRV=$D node structure.js noise
const U = require('../round2/u.js');
const fs = require('fs');
const { cmd, sleep } = U;
const phase = process.argv[2] || 'flat', part = process.argv[3] || '1';
const SRV = process.env.SRV;
const res = {};
const ok = (k, v) => { res[k] = v; console.log(v === true ? 'PASS' : 'FAIL/INFO', k, v === true ? '' : JSON.stringify(v)); };

function structure(bot, p, o) {
  bot._client.write('update_structure_block', {
    location: { x: p[0], y: p[1], z: p[2] }, action: o.action ?? 0, mode: o.mode, name: o.name ?? '', offset_x: o.off?.[0] ?? 0, offset_y: o.off?.[1] ?? 1, offset_z: o.off?.[2] ?? 0,
    size_x: o.size?.[0] ?? 0, size_y: o.size?.[1] ?? 0, size_z: o.size?.[2] ?? 0, mirror: o.mirror ?? 0, rotation: o.rotation ?? 0, metadata: o.meta ?? '',
    integrity: o.integrity ?? 1, seed: o.seed ?? 0, flags: o.flags ?? 5 });
}
const MODE = { save: 0, load: 1, corner: 2, data: 3 }, ACT = { update: 0, save: 1, load: 2, scan: 3 };
async function exec(bot, c) { const m = await U.ask(bot, `/execute if ${c}`, /Test passed|Test failed/i, 2500); return /passed/i.test(m || ''); }
async function test(bot, c) { return exec(bot, `block ${c}`); }
// /data get 출력은 잘려 읽기 어렵다 — execute if data/items 의 통과 여부로 확인한다.
async function has(bot, p, snbt) { return await exec(bot, `data block ${p.join(' ')} ${snbt}`); }
async function hasItem(bot, p, item) { return await exec(bot, `items block ${p.join(' ')} container.0 ${item}`); }
function findFile(rel) { for (const d of [SRV + '/world/generated', SRV + '/generated']) { if (fs.existsSync(d + '/' + rel)) return d + '/' + rel; } return null; }

(async () => {
  const bot = await U.setup('Tester');
  const beMsgs = [];
  bot._client.on('tile_entity_data', p => beMsgs.push(p));
  cmd('gamemode creative Tester'); cmd('gamerule doMobSpawning false'); await sleep(800);

  if (phase === 'flat' && part === '1') {
    bot.chat('/tp 10 -58 10'); await sleep(1500);
    // 템플릿 재료: 돌 3x3, 계단(동쪽), 판자, 상자(다이아 3)
    cmd('fill 3 -54 3 5 -54 5 stone'); cmd('setblock 3 -53 3 oak_planks');
    cmd('setblock 4 -53 3 oak_stairs[facing=east]');
    cmd('setblock 5 -53 5 chest{Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}');
    cmd('setblock 5 -53 4 structure_void');
    cmd('setblock 2 -55 2 structure_block[mode=save]'); await sleep(1500);
    // 1) 편집(UPDATE_DATA)
    const before = beMsgs.length;
    structure(bot, [2, -55, 2], { mode: MODE.save, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3], meta: 'hello', seed: 5 });
    await sleep(1000);
    ok('edit.fields', await has(bot, [2, -55, 2], '{name:"bot:house",sizeX:3,sizeY:2,sizeZ:3,posX:1,posY:1,posZ:1,metadata:"hello",mode:"SAVE",seed:5L}'));
    ok('edit.blockEntityDataSentToClient', beMsgs.length > before ? true : 'tile_entity_data 없음');
    ok('edit.blockStateModeSave', await test(bot, '2 -55 2 structure_block[mode=save]'));
    // 2) SAVE_AREA
    const t0 = Date.now(); bot.msgs.length = 0;
    structure(bot, [2, -55, 2], { action: ACT.save, mode: MODE.save, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3], seed: 5 });
    const m = await U.waitMsg(bot, /save|Structure/i, 4000, t0);
    ok('save.message', /saved/i.test(m || '') ? true : m);
    await sleep(500);
    const f = findFile('bot/structures/house.nbt');
    ok('save.file', f ? true : '파일 없음');
    // 3) LOAD_AREA 같은 크기
    cmd('setblock 2 -55 10 structure_block[mode=load]'); await sleep(800);
    structure(bot, [2, -55, 10], { mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] });
    await sleep(500);
    bot.msgs.length = 0; const t1 = Date.now();
    structure(bot, [2, -55, 10], { action: ACT.load, mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] });
    const m2 = await U.waitMsg(bot, /load|Structure/i, 4000, t1);
    ok('load.message', /loaded/i.test(m2 || '') ? true : m2);
    await sleep(800);
    ok('load.stone', await test(bot, '3 -54 11 stone'));
    ok('load.planks', await test(bot, '3 -53 11 oak_planks'));
    ok('load.stairs', await test(bot, '4 -53 11 oak_stairs[facing=east]'));
    ok('load.structureVoidIsAir', await test(bot, '5 -53 12 air'));
    ok('load.chestItems', await hasItem(bot, [5, -53, 13], 'minecraft:diamond'));
    // 4) 크기가 다르면 놓지 않고 템플릿 크기로 맞춘다(load_prepare)
    cmd('setblock 2 -55 26 structure_block[mode=load]'); await sleep(800);
    bot.msgs.length = 0; const t2 = Date.now();
    structure(bot, [2, -55, 26], { action: ACT.load, mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [1, 1, 1] });
    const m3 = await U.waitMsg(bot, /structure/i, 4000, t2);
    ok('load.sizeMismatchMessage', m3 ? true : m3);
    await sleep(500);
    ok('load.sizeMismatchAdoptsTemplateSize', await has(bot, [2, -55, 26], '{sizeX:3,sizeY:2,sizeZ:3}'));
    ok('load.sizeMismatchPlacedNothing', await test(bot, '3 -54 27 air'));
    // 5) 회전 CLOCKWISE_90: 계단 (로컬 1,1,0) → (0,1,1) 으로, 방향 east → south
    cmd('setblock 2 -55 18 structure_block[mode=load]'); await sleep(800);
    structure(bot, [2, -55, 18], { mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3], rotation: 1 }); await sleep(400);
    bot.msgs.length = 0; const t3 = Date.now();
    structure(bot, [2, -55, 18], { action: ACT.load, mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3], rotation: 1 });
    await U.waitMsg(bot, /loaded|structure/i, 4000, t3); await sleep(800);
    ok('rotate.stairsFacingSouth', await test(bot, '3 -53 20 oak_stairs[facing=south]'));
    // 6) 신호: 불러오기 블록 옆에 레드스톤 블록
    cmd('setblock 20 -55 10 structure_block[mode=load]'); await sleep(800);
    structure(bot, [20, -55, 10], { mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(400);
    cmd('setblock 20 -55 9 redstone_block'); await sleep(1500);
    ok('redstone.loads', await test(bot, '21 -54 11 stone') && await test(bot, '21 -53 11 oak_planks'));
    // 7) 신호 저장은 메모리 템플릿만(파일 안 씀) — 이름만 다른 저장 블록
    cmd('setblock 30 -55 2 structure_block[mode=save]'); await sleep(800);
    structure(bot, [30, -55, 2], { mode: MODE.save, name: 'bot:memonly', off: [-26, 1, 1], size: [1, 1, 1] }); await sleep(400);
    cmd('setblock 30 -55 1 redstone_block'); await sleep(1500);
    ok('redstone.saveWritesNoFile', findFile('bot/structures/memonly.nbt') ? '신호 저장이 파일을 썼다' : true);
    // 8) 크기 감지: 모서리 두 개 + 이름 다른 모서리 하나
    cmd('setblock 45 -55 45 structure_block[mode=save]'); await sleep(800);
    for (const [c, nm] of [[[40, -58, 40], 'bot:big'], [[49, -50, 49], 'bot:big'], [[52, -45, 52], 'bot:other']]) {
      cmd(`setblock ${c.join(' ')} structure_block[mode=corner]`); await sleep(600);
      structure(bot, c, { mode: MODE.corner, name: nm });
      await sleep(300);
    }
    structure(bot, [45, -55, 45], { mode: MODE.save, name: 'bot:big', off: [0, 1, 0], size: [0, 0, 0] }); await sleep(400);
    bot.msgs.length = 0; const t4 = Date.now();
    structure(bot, [45, -55, 45], { action: ACT.scan, mode: MODE.save, name: 'bot:big', off: [0, 1, 0], size: [0, 0, 0] });
    const m4 = await U.waitMsg(bot, /size|structure/i, 4000, t4);
    ok('scan.message', /detected|size/i.test(m4 || '') ? true : m4);
    await sleep(500);
    // 상자 x 40..49, y -58..-50, z 40..49 → 오프셋 (40-45+1, -58+55+1, 40-45+1) = (-4,-2,-4), 크기 (8,7,8)
    ok('scan.result', await has(bot, [45, -55, 45], '{posX:-4,posY:-2,posZ:-4,sizeX:8,sizeY:7,sizeZ:8}'));
    // 9) 권한: 서바이벌·권한 없음이면 아무 일도 없다
    cmd('gamemode survival Tester'); await sleep(600);
    structure(bot, [2, -55, 2], { mode: MODE.save, name: 'bot:hacked', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(600);
    ok('perm.survivalIgnored', await has(bot, [2, -55, 2], '{name:"bot:house"}') ? true : 'survival 이 편집했다');
    cmd('gamemode creative Tester'); cmd('deop Tester'); await sleep(800);
    structure(bot, [2, -55, 2], { mode: MODE.save, name: 'bot:hacked', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(600);
    cmd('op Tester'); await sleep(800);
    ok('perm.nonOpIgnored', await has(bot, [2, -55, 2], '{name:"bot:house"}') ? true : '비권한자가 편집했다');
    // 10) 잘못된 이름
    bot.msgs.length = 0; const t5 = Date.now();
    structure(bot, [2, -55, 2], { action: ACT.save, mode: MODE.save, name: 'Bad Name', off: [1, 1, 1], size: [3, 2, 3] });
    const m5 = await U.waitMsg(bot, /invalid|name/i, 3000, t5);
    ok('invalidName.message', m5 ? true : m5);
    // 구조물 블록 모드 전환이 블록 상태에 반영
    structure(bot, [2, -55, 2], { mode: MODE.data, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(600);
    ok('mode.dataState', await test(bot, '2 -55 2 structure_block[mode=data]'));
    structure(bot, [2, -55, 2], { mode: MODE.save, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(600);
    // 직소 블록
    cmd('setblock 60 -55 2 jigsaw[orientation=north_up]'); await sleep(800);
    ok('jigsaw.defaults', await has(bot, [60, -55, 2], '{name:"minecraft:empty",joint:"rollable",final_state:"minecraft:air"}'));
    bot._client.write('update_jigsaw_block', { location: { x: 60, y: -55, z: 2 }, name: 'bot:a', target: 'bot:b', pool: 'minecraft:village/plains/houses',
      finalState: 'minecraft:oak_planks', jointType: 'rollable', selection_priority: 2, placement_priority: 3 });
    await sleep(800);
    ok('jigsaw.edit', await has(bot, [60, -55, 2], '{name:"bot:a",target:"bot:b",pool:"minecraft:village/plains/houses",final_state:"minecraft:oak_planks",joint:"rollable",placement_priority:3,selection_priority:2}'));
    // 잘못된 joint 이름은 aligned
    bot._client.write('update_jigsaw_block', { location: { x: 60, y: -55, z: 2 }, name: 'bot:a', target: 'bot:b', pool: 'minecraft:empty', finalState: 'minecraft:air', jointType: 'zzz', selection_priority: 0, placement_priority: 0 });
    await sleep(600);
    ok('jigsaw.badJointAligned', await has(bot, [60, -55, 2], '{joint:"aligned"}'));
    // 권한 없는 편집은 무시
    cmd('gamemode survival Tester'); await sleep(500);
    bot._client.write('update_jigsaw_block', { location: { x: 60, y: -55, z: 2 }, name: 'hack:a', target: 'bot:b', pool: 'minecraft:empty', finalState: 'minecraft:air', jointType: 'aligned', selection_priority: 0, placement_priority: 0 });
    await sleep(600);
    ok('jigsaw.perm', await has(bot, [60, -55, 2], '{name:"bot:a"}') ? true : '서바이벌이 편집했다');
    cmd('gamemode creative Tester');
    // 상호작용: 구조물 블록/직소 블록을 우클릭해도 서버가 막지 않는다(화면은 클라이언트가 연다) — 오류 없이 지나가는지만
    await sleep(500);
    fs.writeFileSync(SRV + '/round9-part1.json', JSON.stringify(res));
  } else if (phase === 'flat' && part === '2') {
    // 재시작 뒤: 블록 엔티티가 저장돼 있고, 메모리가 비었어도 파일에서 불러온다
    ok('restart.blockEntityPersisted', await has(bot, [2, -55, 2], '{name:"bot:house",sizeX:3,sizeY:2,posY:1,mode:"SAVE"}'));
    ok('restart.jigsawPersisted', await has(bot, [60, -55, 2], '{name:"bot:a",final_state:"minecraft:air"}'));
    bot.chat('/tp 10 -58 10'); await sleep(1500);
    cmd('setblock 2 -55 34 structure_block[mode=load]'); await sleep(800);
    structure(bot, [2, -55, 34], { mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] }); await sleep(400);
    bot.msgs.length = 0; const t = Date.now();
    structure(bot, [2, -55, 34], { action: ACT.load, mode: MODE.load, name: 'bot:house', off: [1, 1, 1], size: [3, 2, 3] });
    const m = await U.waitMsg(bot, /load|Structure/i, 4000, t);
    ok('restart.loadFromFile', /loaded/i.test(m || '') ? true : m);
    await sleep(800);
    ok('restart.blocks', await test(bot, '3 -54 35 stone') && await test(bot, '4 -53 35 oak_stairs[facing=east]'));
    // 템플릿 이름이 없으면 load_not_found
    bot.msgs.length = 0; const t2 = Date.now();
    structure(bot, [2, -55, 34], { action: ACT.load, mode: MODE.load, name: 'bot:missing', off: [1, 1, 1], size: [3, 2, 3] });
    const m2 = await U.waitMsg(bot, /not found|find|Structure/i, 4000, t2);
    ok('load.notFound', m2 ? true : m2);
  } else if (phase === 'noise') {
    // 노이즈 월드: 직소 블록 "생성" — 마을 중심을 펼친다
    bot.chat('/tp 8 150 8'); await sleep(2500);
    cmd('forceload add -64 -64 64 64'); await sleep(1500);
    cmd('setblock 8 140 8 jigsaw[orientation=up_north]'); await sleep(1000);
    bot._client.write('update_jigsaw_block', { location: { x: 8, y: 140, z: 8 }, name: 'minecraft:bottom', target: 'minecraft:bottom', pool: 'minecraft:village/plains/town_centers',
      finalState: 'minecraft:air', jointType: 'rollable', selection_priority: 0, placement_priority: 0 });
    await sleep(800);
    bot.chat('/tp 8 150 8'); await sleep(1000);
    bot._client.write('generate_structure', { location: { x: 8, y: 140, z: 8 }, levels: 1, keepJigsaws: true });
    await sleep(5000);
    // 32x32x32 안에서(최대 32768칸) 직소 블록이 남았는지 — keepJigsaws 면 남는다
    const placed = await U.ask(bot, '/fill 0 130 0 31 161 31 air replace minecraft:jigsaw', /Could not|Successfully|filled|No blocks|Too many/i, 4000);
    ok('jigsaw.generate.keepJigsawsLeavesJigsawBlocks', /Successfully filled|filled/i.test(placed || '') && !/Could not|No blocks/i.test(placed || '') ? true : placed);
    // 직소 블록이 18개 남았다면(바탕 블록 1개보다 많다) 조각이 이어 붙었다는 뜻이다
    const n = Number((/filled (\d+)/.exec(placed || '') || [])[1] || 0);
    ok('jigsaw.generate.connectedManyPieces', n > 1 ? true : placed);
  }
  console.log('RESULT', JSON.stringify(res, null, 1));
  bot.quit(); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
