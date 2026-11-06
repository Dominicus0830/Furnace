const { makeBot, spawned, waitMsg, chat, cmd, sleep } = require('./lib');
const V = require('vec3');
const phase = process.argv[2];
(async () => {
  const bot = makeBot('Tester');
  let windows = 0; bot.on('windowOpen', w => { windows++; console.log('WINDOW', w.type, w.title); });
  await spawned(bot); await sleep(2000);
  const msgSince = async (re, t, ms = 3000) => await waitMsg(bot, re, ms, t);
  let seq = 100;
  const leftClick = async (pos) => {
    const t = Date.now();
    bot._client.write('block_dig', { status: 0, location: pos, face: 1, sequence: seq++ });
    bot._client.write('arm_animation', { hand: 0 });
    await sleep(150);
    bot._client.write('block_dig', { status: 1, location: pos, face: 1, sequence: seq++ });
    return await msgSince(/clicks in chunk|Too deep/, t);
  };
  const stick = async () => { await chat(bot, '/clear @s', 300); await chat(bot, '/give @s minecraft:stick', 500); await bot.equip(bot.inventory.items().find(i => i.name === 'stick'), 'hand'); };
  if (phase === '1') {
    cmd('op Tester'); await sleep(800);
    await chat(bot, '/gamerule doMobSpawning false');
    await chat(bot, '/tp @s 0.5 -60 0.5 0 0', 800);
    await stick();
    await chat(bot, '/setblock 1 -60 2 minecraft:stone', 500);
    const target = new V(1, -60, 2);
    await bot.lookAt(target.offset(0.5, 0.5, 0.5));
    for (let i = 0; i < 3; i++) console.log('RESULT 서바이벌 왼쪽 클릭', i + 1, await leftClick(target));
    console.log('RESULT 서바이벌 후 블록', bot.blockAt(target)?.name);
    await chat(bot, '/gamemode creative', 600);
    await bot.equip(bot.inventory.items().find(i => i.name === 'stick'), 'hand');
    console.log('RESULT 크리에이티브 왼쪽 클릭', await leftClick(target)); await sleep(500);
    console.log('RESULT 크리에이티브 후 블록', bot.blockAt(target)?.name);
    await chat(bot, '/gamemode survival', 600);
    // 허공 왼쪽 클릭
    await chat(bot, '/summon minecraft:cow 3 -60 0 {NoAI:1b}', 600);
    await bot.look(0, Math.PI / 2, true); await sleep(300);
    let t = Date.now(); bot.swingArm('right'); console.log('RESULT 하늘 보고 휘두르기', await msgSince(/entities within/, t));
    await bot.lookAt(target.offset(0.5, 0.5, 0.5), true); await sleep(300);
    t = Date.now(); bot.swingArm('right'); console.log('RESULT 블록 보고 휘두르기(안 떠야 함)', await msgSince(/entities within/, t, 1500));
    // 용기
    await chat(bot, '/setblock -1 -60 2 minecraft:chest', 400);
    await chat(bot, '/item replace block -1 -60 2 container.0 with minecraft:diamond 5', 400);
    await chat(bot, '/setblock -3 -60 2 minecraft:furnace', 400);
    await chat(bot, '/item replace block -3 -60 2 container.0 with minecraft:iron_ore 3', 400);
    for (const [p, nm] of [[new V(-1, -60, 2), '상자'], [new V(-3, -60, 2), '화로']]) {
      t = Date.now(); const w0 = windows; await bot.lookAt(p.offset(0.5, 0.5, 0.5)); await bot.activateBlock(bot.blockAt(p));
      console.log('RESULT 막대기로', nm, '우클릭', await msgSince(/slots, slot 0/, t), '창 열림', windows - w0);
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    }
    // 엔티티
    await chat(bot, '/summon minecraft:villager 0 -60 4 {NoAI:1b,VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"}}', 600);
    await chat(bot, '/summon minecraft:chest_boat 3 -60 3', 600);
    for (const nm of ['cow', 'villager', 'chest_boat']) {
      const e = Object.values(bot.entities).find(x => x.name === nm);
      t = Date.now(); const w0 = windows; await bot.lookAt(e.position.offset(0, 0.5, 0)); await bot.activateEntity(e); await sleep(2600);
      const got = bot.msgs.filter(m => m.t >= t).map(m => m.m);
      console.log('RESULT 막대기로', nm, '우클릭', JSON.stringify(got), '창 열림', windows - w0, '탑승', !!bot.vehicle);
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      if (bot.vehicle) bot.dismount();
    }
    // retired: 소 찌르고 바로 죽이기
    await chat(bot, '/summon minecraft:cow -3 -60 0 {NoAI:1b,CustomName:\'"victim"\'}', 800);
    const victim = Object.values(bot.entities).filter(x => x.name === 'cow').sort((a, b) => a.position.x - b.position.x)[0];
    t = Date.now(); await bot.lookAt(victim.position.offset(0, 0.5, 0)); await bot.activateEntity(victim); await sleep(300);
    await chat(bot, '/kill @e[type=cow,name=victim]', 2800);
    console.log('RESULT 찌르고 죽인 소', JSON.stringify(bot.msgs.filter(m => m.t >= t && /cow/.test(m.m)).map(m => m.m)));
    // 엔티티 스폰
    await bot.look(0, 0, true); t = Date.now(); await chat(bot, '/samplespawn', 1500);
    console.log('RESULT /samplespawn', await msgSince(/spawn/, t), '좀비', Object.values(bot.entities).filter(x => x.name === 'zombie').map(z => z.position));
    await chat(bot, '/kill @e[type=zombie]', 300);
    // 막대기 없이
    await chat(bot, '/clear @s', 300); bot.setQuickBarSlot(3);
    await chat(bot, '/gamemode creative', 600);
    const b2 = new V(1, -60, 3); await chat(bot, '/setblock 1 -60 3 minecraft:stone', 400);
    await bot.lookAt(b2.offset(0.5, 0.5, 0.5));
    bot._client.write('block_dig', { status: 0, location: b2, face: 1, sequence: seq++ }); await sleep(800);
    console.log('RESULT 막대기 없이 크리에이티브 부수기 후', bot.blockAt(b2)?.name);
    await chat(bot, '/gamemode survival', 600);
    const vil = Object.values(bot.entities).find(x => x.name === 'villager');
    let w0 = windows; await bot.lookAt(vil.position.offset(0, 1.5, 0)); await bot.activateEntity(vil); await sleep(1500);
    console.log('RESULT 막대기 없이 주민 우클릭 창', windows - w0, bot.currentWindow?.type);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    const boat = Object.values(bot.entities).find(x => x.name === 'chest_boat');
    await bot.lookAt(boat.position.offset(0, 0.3, 0)); bot.mount(boat); await sleep(1500);
    console.log('RESULT 막대기 없이 상자 배 탑승', !!bot.vehicle); if (bot.vehicle) { bot.dismount(); await sleep(800); }
    // 재시작용 표시: 소 하나 남겨 두고 찌르기 2회
    await stick();
    const keep = Object.values(bot.entities).find(x => x.name === 'cow');
    for (let i = 0; i < 2; i++) { t = Date.now(); await bot.lookAt(keep.position.offset(0, 0.5, 0)); await bot.activateEntity(keep); console.log('RESULT 재시작 전 찌르기', await msgSince(/poked/, t)); }
  } else {
    await chat(bot, '/tp @s 0.5 -60 0.5 0 0', 1500);
    await stick();
    const target = new V(1, -60, 2);
    await bot.lookAt(target.offset(0.5, 0.5, 0.5));
    console.log('RESULT 재시작 후 같은 청크 왼쪽 클릭', await leftClick(target));
    const keep = Object.values(bot.entities).find(x => x.name === 'cow');
    const t = Date.now(); await bot.lookAt(keep.position.offset(0, 0.5, 0)); await bot.activateEntity(keep);
    console.log('RESULT 재시작 후 소 찌르기', await msgSince(/poked/, t));
  }
  bot.quit(); await sleep(800); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
