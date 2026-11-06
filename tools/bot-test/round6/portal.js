// 포탈 직후 이동 검증: 네더 포탈로 네더에 갔다가 돌아오면서, 도착 직후 걷기·점프에 서버 되돌림(position 패킷)이 있는지 센다
const U = require('../round2/u'); const { sleep, chat, ask } = U;
const fs = require('fs'); const SRV = process.env.SRV;
const logLen = () => fs.readFileSync(SRV + '/server.log', 'utf8').length;
const logSince = n => fs.readFileSync(SRV + '/server.log', 'utf8').slice(n).split('\n').filter(l => /moved|floating|kicked/i.test(l));
(async () => {
  const bot = await U.setup('Porter');
  const ev = []; let dim = 'overworld';
  bot._client.on('position', p => ev.push({ t: Date.now(), k: 'pos', y: p.y }));
  bot._client.on('respawn', p => { dim = String(p.worldState?.name ?? p.worldName ?? p.dimension); ev.push({ t: Date.now(), k: 'respawn ' + dim }); });
  for (const c of ['/gamemode survival', '/difficulty peaceful', '/effect give @s resistance 600 4 true', '/effect give @s fire_resistance 600 0 true',
    '/fill 100 -61 -3 106 -61 3 obsidian', '/fill 100 -60 0 103 -56 0 obsidian', '/fill 101 -59 0 102 -57 0 nether_portal[axis=x]', '/tp @s 101.5 -60 -2.5 0 0']) await chat(bot, c, 200);
  await sleep(1500);
  const l0 = logLen();
  async function travel(label) {
    const t0 = Date.now(); const startDim = dim;
    await chat(bot, '/tp @s 101.5 -59 0.5', 300);   // 포탈 칸 안으로
    for (let i = 0; i < 200 && dim === startDim; i++) await sleep(100);
    if (dim === startDim) { console.log('RESULT', label, '차원 이동 안 됨'); return false; }
    const tArr = Date.now(); console.log('RESULT', label, `포탈 들어가 ${((tArr - t0) / 1000).toFixed(1)}초 만에 ${dim}`);
    await sleep(2500);   // 도착 텔레포트·청크
    const tWalk = Date.now();
    bot.setControlState('back', true); await sleep(1200); bot.setControlState('back', false);
    bot.setControlState('jump', true); bot.setControlState('left', true); await sleep(1500); bot.clearControlStates();
    await sleep(800);
    const corr = ev.filter(e => e.k === 'pos' && e.t >= tWalk).length;
    console.log('RESULT', label, '도착 뒤 걷기·점프 동안 되돌림', corr, '도착 이후 position 패킷', ev.filter(e => e.k === 'pos' && e.t >= tArr).map(e => e.y.toFixed(2)).join(','), '위치', bot.entity.position.floored());
    return true;
  }
  if (await travel('오버월드→네더')) {
    // 네더 쪽 포탈로 되돌아가기: 도착 지점 포탈 블록 찾기
    const portal = bot.findBlock({ matching: b => b.name === 'nether_portal', maxDistance: 16 });
    if (portal) {
      await chat(bot, `/tp @s ${portal.position.x + 0.5} ${portal.position.y} ${portal.position.z + 0.5}`, 300);
      // tp 로 포탈 칸 안에 넣는다(forward 없이 기다림)
      const startDim = dim; const t0 = Date.now(); for (let i = 0; i < 200 && dim === startDim; i++) await sleep(100);
      const tArr = Date.now(); console.log('RESULT 네더→오버월드', dim, `${((tArr - t0) / 1000).toFixed(1)}초`);
      await sleep(2500); const tWalk = Date.now();
      bot.setControlState('back', true); await sleep(1200); bot.setControlState('back', false);
      bot.setControlState('jump', true); bot.setControlState('right', true); await sleep(1500); bot.clearControlStates(); await sleep(800);
      console.log('RESULT 네더→오버월드 도착 뒤 걷기·점프 동안 되돌림', ev.filter(e => e.k === 'pos' && e.t >= tWalk).length, '위치', bot.entity.position.floored());
    } else console.log('RESULT 네더 쪽 포탈 못 찾음');
  }
  console.log('RESULT 서버 로그 이동 경고', JSON.stringify(logSince(l0)));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
