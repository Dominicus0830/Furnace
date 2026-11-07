// M2: 플레이어 사망이 entity_die 를 내서 스컬크 촉매가 경험치(레벨×7)를 가져가 스컬크가 퍼지고, 촉매가 없으면 경험치 구슬이 떨어지는지를 패킷·블록으로 확인한다.
// 사용: SRV=<서버 폴더> node m2/m2.js   (평지·오프라인 서버, 바닐라 데이터 필요 — README 참고)
const { makeBot, spawned, chat, cmd, sleep } = require('../lib');
(async () => {
  const bot = makeBot('Victim'); await spawned(bot); await sleep(1500);
  const R = [];
  const res = (name, ok, extra = '') => { R.push(ok); console.log('RESULT', ok ? 'PASS' : 'FAIL', name, extra); };
  const parts = [], orbs = [];
  bot._client.on('world_particles', p => parts.push({ t: Date.now(), count: p.amount ?? p.count, id: p.particleId ?? p.particle?.type, x: p.x, y: p.y, z: p.z, dx: p.offsetX, dz: p.offsetZ, speed: p.velocityOffset ?? p.maxSpeed }));
  bot._client.on('packet', (d, meta) => { if (/experience_orb/.test(meta.name)) orbs.push({ name: meta.name, d }); });
  for (const c of ['op Victim', 'gamerule doMobSpawning false', 'difficulty peaceful', 'time set day', 'kill @e[type=!player]', 'gamemode survival Victim']) { cmd(c); await sleep(300); }
  await sleep(500);
  const sculkAround = (cx, cz) => {
    let n = 0;
    for (let x = cx - 12; x <= cx + 12; x++) for (let z = cz - 12; z <= cz + 12; z++) for (let y = -62; y <= -58; y++) {
      const b = bot.blockAt(new (require('vec3').Vec3)(x, y, z));
      if (b && b.name === 'sculk') n++;
    }
    return n;
  };

  // A. 촉매 옆에서 레벨 10 으로 죽는다
  cmd('execute in minecraft:overworld run setblock 3 -60 0 minecraft:sculk_catalyst');
  cmd('xp set Victim 10 levels'); await sleep(500);
  await chat(bot, '/tp @s 0.5 -60 0.5', 600);
  parts.length = 0; orbs.length = 0;
  const before = sculkAround(3, 0);
  cmd('kill Victim');
  await sleep(2500);
  const soul = parts.filter(x => x.count === 2 && Math.abs(x.x - 3.5) < 0.01 && Math.abs(x.y - (-60 + 1.15)) < 0.01);
  console.log('A particles', parts.length, JSON.stringify(parts.slice(0, 4)));
  res('촉매 bloom 영혼 입자(count 2, 촉매 위 1.15)', soul.length >= 1, `n=${soul.length}`);
  res('죽은 자리에 경험치 구슬이 안 떨어진다(촉매가 가져감)', orbs.length === 0, `orbs=${orbs.length}`);
  await sleep(12000);
  const after = sculkAround(3, 0);
  res('스컬크가 퍼졌다(레벨 10 → 전하 70)', after > before, `${before}->${after}`);

  // B. 촉매에서 멀리(30칸) 레벨 5 로 죽는다 → 구슬 합 35
  cmd('xp set Victim 5 levels'); await sleep(500);
  await chat(bot, '/tp @s 40.5 -60 40.5', 1200); await sleep(1500);
  orbs.length = 0;
  cmd('kill Victim');
  await sleep(2500);
  const sum = orbs.reduce((a, o) => a + (o.d.count ?? o.d.value ?? 0), 0);
  console.log('B orbs', orbs.length, JSON.stringify(orbs.slice(0, 5).map(o => [o.name, o.d])));
  res('촉매가 없으면 레벨 5 → 경험치 구슬 합 35', sum === 35, `sum=${sum}`);

  // C. keepInventory 면 경험치도 안 떨어진다
  cmd('gamerule keepInventory true'); cmd('xp set Victim 5 levels'); await sleep(500);
  await chat(bot, '/tp @s 40.5 -60 40.5', 1200); await sleep(1000);
  orbs.length = 0;
  cmd('kill Victim');
  await sleep(2500);
  res('keepInventory 면 구슬 없음', orbs.length === 0, `orbs=${orbs.length}`);
  console.log(R.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.end(); process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
