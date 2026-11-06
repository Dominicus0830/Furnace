// 이동 서버 검증: 상황별로 봇을 움직이고 서버가 보낸 위치 되돌림(position 패킷)과 로그 경고를 센다.
const U = require('../round2/u');
const { sleep, cmd } = U;
const fs = require('fs');
const SRV = process.env.SRV;
const logLen = () => fs.readFileSync(SRV + '/server.log', 'utf8').length;
const logSince = n => fs.readFileSync(SRV + '/server.log', 'utf8').slice(n).split('\n').filter(l => /moved|floating|kicked/i.test(l));
(async () => {
  const bot = await U.setup('Mover');
  let corr = 0, expect = 0; const pos = [];
  bot._client.on('position', p => { if (expect > 0) { expect--; return; } corr++; pos.push(p); });
  const res = [];
  async function phase(name, fn) {
    const c0 = corr, l0 = logLen(); let p0 = null;
    try { await fn((p) => { p0 = p; }); } catch (e) { console.log('ERR', name, e.message); }
    await sleep(600);
    const lines = logSince(l0);
    const pe = bot.entity.position; const r = `RESULT ${name}: 되돌림 ${corr - c0}, 로그 ${lines.length}, 끝 ${pe.x.toFixed(1)},${pe.y.toFixed(1)},${pe.z.toFixed(1)}${lines.length ? ' :: ' + lines.slice(0, 3).map(s => s.replace(/^.*?\]: /, '')).join(' | ') : ''}`;
    console.log(r); res.push(r);
  }
  async function tp(x, y, z, yaw = 0) { expect++; bot.chat(`/tp @s ${x} ${y} ${z} ${yaw} 0`); await sleep(1200); }
  const ctl = (k, v) => bot.setControlState(k, v);
  const clear = () => bot.clearControlStates();
  bot.chat('/gamemode survival'); await sleep(300);
  bot.chat('/difficulty peaceful'); bot.chat('/gamerule doDaylightCycle false'); await sleep(300);
  // 지형: z 방향 길(평지 y=-61 이 땅, 발은 -60)
  const C = [
    'fill -20 -61 0 20 -61 200 stone', 'fill -20 -60 0 20 -50 200 air',
    // 계단·반블록 걸어 오르기 (x=0, z=20..30)
    'setblock 0 -60 22 stone_slab', 'setblock 0 -60 24 stone', 'fill 0 -60 26 0 -59 26 stone', 'setblock 0 -58 26 oak_stairs[facing=south]',
    // 얼음 (x=5, z 0..60)
    'fill 5 -61 0 5 -61 60 packed_ice', 'fill 6 -61 0 6 -61 60 blue_ice',
    // 슬라임 (x=-5, z=10)
    'fill -6 -61 9 -4 -61 11 slime_block',
    // 물 웅덩이 (x 10..14, z 0..40, 깊이 3)
    'fill 10 -64 0 14 -62 40 water', 'fill 10 -61 0 14 -61 40 water',
    // 높이 1칸 물 터널 (x=17, z 0..20): 바닥 -61 아래 -62 를 물, 위는 막음
    'fill 16 -63 0 18 -60 20 stone', 'fill 17 -62 1 17 -62 19 water',
    // 사다리 (x=-10, z=5, 높이 10)
    'fill -10 -60 4 -10 -51 4 stone', 'fill -10 -60 5 -10 -51 5 ladder[facing=south]',
    // 영혼 모래·꿀·거미줄 (x=-15)
    'fill -15 -61 0 -15 -61 20 soul_sand', 'fill -16 -61 0 -16 -61 20 honey_block', 'setblock -17 -60 5 cobweb',
    // 다락문 아래 기어가기는 봇 자세 미지원 — 생략
  ];
  for (const c of C) { bot.chat('/' + c); await sleep(120); }
  await sleep(1500);

  await phase('걷기', async () => { await tp(0.5, -60, 2.5); ctl('forward', true); await sleep(3000); clear(); });
  await phase('달리기', async () => { await tp(0.5, -60, 40.5); ctl('forward', true); ctl('sprint', true); await sleep(4000); clear(); });
  await phase('달리며 점프', async () => { await tp(0.5, -60, 80.5); ctl('forward', true); ctl('sprint', true); ctl('jump', true); await sleep(5000); clear(); });
  await phase('옆걸음·뒤로', async () => { await tp(0.5, -60, 150.5); ctl('left', true); await sleep(1500); clear(); ctl('back', true); await sleep(1500); clear(); });
  await phase('웅크려 걷기', async () => { await tp(0.5, -60, 170.5); ctl('sneak', true); ctl('forward', true); await sleep(2500); clear(); });
  await phase('반블록·계단 오르기', async () => { await tp(0.5, -60, 20.5); ctl('forward', true); ctl('jump', true); await sleep(3500); clear(); });
  await phase('얼음 위 달리기', async () => { await tp(5.5, -60, 0.5); ctl('forward', true); ctl('sprint', true); ctl('jump', true); await sleep(3000); clear(); await sleep(1500); });
  await phase('파란 얼음 달리기', async () => { await tp(6.5, -60, 0.5); ctl('forward', true); ctl('sprint', true); await sleep(3000); clear(); await sleep(1500); });
  await phase('슬라임 낙하 튕김', async () => { await tp(-4.5, -50, 10.5); await sleep(4000); });
  await phase('높은 곳 낙하(20칸)', async () => { await tp(0.5, -40, 120.5); await sleep(3000); });
  await phase('헤엄(물 위)', async () => { await tp(12.5, -61, 2.5); ctl('forward', true); ctl('jump', true); await sleep(4000); clear(); });
  await phase('물속 달리기 헤엄', async () => { await tp(12.5, -63, 20.5); ctl('forward', true); ctl('sprint', true); await sleep(3000); clear(); });
  await phase('1칸 물 터널', async () => { await tp(17.5, -62, 1.5); ctl('forward', true); ctl('sprint', true); await sleep(4000); clear(); });
  await phase('사다리 오르기', async () => { await tp(-9.5, -60, 5.5, 180); ctl('forward', true); ctl('jump', true); await sleep(4000); clear(); await sleep(2500); });
  await phase('영혼 모래·꿀', async () => { await tp(-14.5, -60, 0.5); ctl('forward', true); await sleep(2500); clear(); await tp(-15.5, -60, 0.5); ctl('forward', true); await sleep(2500); clear(); });
  await phase('거미줄 통과', async () => { await tp(-16.5, -60, 3.5); ctl('forward', true); await sleep(3000); clear(); });
  await phase('/tp 직후 걷기', async () => { for (let i = 0; i < 4; i++) { await tp(0.5, -60, 100.5 + i * 5); ctl('forward', true); await sleep(400); clear(); } });
  await phase('텔레포트 확인 전 이동', async () => { expect++; bot.chat('/tp @s 0.5 -60 60.5'); ctl('forward', true); await sleep(1500); clear(); });
  await phase('넉백(좀비)', async () => {
    await tp(-3.5, -60, 130.5); bot.chat('/difficulty easy'); await sleep(300);
    bot.chat('/summon zombie -3.5 -60 133.5'); ctl('forward', true); await sleep(3000); clear(); bot.chat('/kill @e[type=zombie]'); bot.chat('/difficulty peaceful');
  });
  await phase('계속 점프(제자리 10초)', async () => { await tp(0.5, -60, 190.5); ctl('jump', true); await sleep(10000); clear(); });
  await phase('대조: 15칸 순간이동 패킷', async () => { await tp(0.5, -60, 60.5); const p = bot.entity.position; bot._client.write('position', { x: p.x, y: p.y, z: p.z + 15, onGround: true }); await sleep(1500); });
  await phase('대조: 벽 속으로 0.8칸', async () => { bot.chat('/setblock 0 -60 62 stone'); bot.chat('/setblock 0 -59 62 stone'); await tp(0.5, -60, 61.5); const p = bot.entity.position; bot._client.write('position', { x: p.x, y: p.y, z: p.z + 0.8, onGround: true }); await sleep(1500); });
  console.log('SUMMARY'); res.forEach(r => console.log(r));
  console.log('CORRECTIONS', JSON.stringify(pos.slice(0, 10)));
  bot.quit(); await sleep(500); process.exit(0);
})();
