// M6: 플레이어 전투 — 쓸기·피해 표시 입자, 힘이 생물이 아닌 대상(보트)에도, 살충 느림, 철퇴 착지 먼지 기둥을 패킷·엔티티로 확인한다.
const { makeBot, spawned, chat, sleep } = require('../lib');
(async () => {
  const bot = makeBot('Tester'); await spawned(bot); await sleep(1500);
  require('fs').appendFileSync(process.env.SRV + '/cmds', 'op Tester\n'); await sleep(800);
  const pname = id => typeof id === 'string' ? id : (bot.registry.particles?.[id]?.name ?? ('#' + id));
  const parts = [];
  bot._client.on('world_particles', p => parts.push({ name: pname(p.particleId ?? p.particle?.type), count: p.amount ?? p.count, x: p.x, y: p.y, z: p.z, dx: p.offsetX, dy: p.offsetY, dz: p.offsetZ, speed: p.velocityOffset ?? p.maxSpeed, raw: p }));
  for (const c of ['/gamerule doMobSpawning false', '/difficulty easy', '/time set midnight', '/kill @e[type=!player]', '/clear @s', '/gamemode survival', '/gamerule naturalRegeneration false', '/effect clear @s']) await chat(bot, c, 400);
  const R = [];
  const res = (name, ok, extra = '') => { R.push(ok); console.log('RESULT', ok ? 'PASS' : 'FAIL', name, extra); };
  const ents = n => Object.values(bot.entities).filter(e => e.name === n || e.type === n);
  const find = async (n, ms = 3000) => { const end = Date.now() + ms; while (Date.now() < end) { const l = ents(n).sort((a, b) => b.id - a.id); if (l[0]) return l[0]; await sleep(100); } return null; };
  // 평지: y=-61 가 바닥, 선 높이 -60
  await chat(bot, '/tp @s 0.5 -60 0.5 -90 0', 800);

  // A. 쓸기 + 피해 표시 입자 (다이아 검, 좀비 NoAI)
  await chat(bot, '/summon zombie 1.5 -60 0.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,HandItems:[{},{}],ArmorItems:[{},{},{},{}],Health:100f,Attributes:[{Name:"generic.max_health",Base:100d}]}', 800);
  await chat(bot, '/give @s minecraft:diamond_sword', 500);
  const sword = bot.inventory.items().find(i => i.name === 'diamond_sword'); await bot.equip(sword, 'hand');
  const z = await find('zombie'); await sleep(2000);   // 쿨다운 가득
  await bot.look(-Math.PI / 2, 0, true); await sleep(300);
  parts.length = 0;
  bot.attack(z); await sleep(800);
  const sweep = parts.filter(x => x.name === 'sweep_attack'), ind = parts.filter(x => x.name === 'damage_indicator');
  await chat(bot, '/data get entity @e[type=zombie,limit=1] Health', 600); console.log('A health', bot.msgs.slice(-1)[0].m);
  console.log('A particles', JSON.stringify(parts.map(({ raw, ...r }) => r)));
  res('sweep_attack 입자 1개(count 0, 방향 +x)', sweep.length === 1 && sweep[0].count === 0 && Math.abs(sweep[0].dx - 1) < 1e-3, JSON.stringify(sweep[0] && { x: sweep[0].x, y: sweep[0].y, dx: sweep[0].dx }));
  res('damage_indicator 입자(7 피해 → 3개, 속도 0.2)', ind.length === 1 && ind[0].count === 3 && Math.abs(ind[0].speed - 0.2) < 1e-3, JSON.stringify(ind[0] && { count: ind[0].count, y: ind[0].y }));

  // B. 힘 II 맨손으로 보트 한 방(바닐라: (1 + 6) × 10 = 70 > 40)
  await chat(bot, '/kill @e[type=!player]', 500);
  await chat(bot, '/clear @s', 400);
  await chat(bot, '/summon minecraft:boat 1.5 -60 0.5', 800);
  let boat = await find('boat'); await sleep(2000);
  res('보트가 소환됨', !!boat);
  await bot.look(-Math.PI / 2, 0, true);
  bot.attack(boat); await sleep(800);
  res('힘 없이 맨손 한 방: 보트 안 부서짐(피해 10)', ents('boat').length === 1);
  await sleep(2500);
  await chat(bot, '/effect give @s minecraft:strength 100 1 true', 500);
  await sleep(2000);
  boat = ents('boat')[0];
  bot.attack(boat); await sleep(800);
  res('힘 II 맨손 한 방: 보트 부서짐(피해 70)', ents('boat').length === 0);
  await chat(bot, '/effect clear @s', 400);

  // C. 살충 V → 거미에게 느림 IV
  await chat(bot, '/kill @e[type=!player]', 500);
  await chat(bot, '/summon spider 1.5 -60 0.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,HandItems:[{},{}],ArmorItems:[{},{},{},{}],Health:100f,Attributes:[{Name:"generic.max_health",Base:100d}]}', 800);
  await chat(bot, '/give @s minecraft:iron_sword[enchantments={levels:{"minecraft:bane_of_arthropods":5}}]', 600);
  await bot.equip(bot.inventory.items().find(i => i.name === 'iron_sword'), 'hand');
  const sp = await find('spider'); await sleep(2000);
  await bot.look(-Math.PI / 2, 0, true);
  bot.attack(sp); await sleep(800);
  const logPath = process.env.SRV + '/server.log';
  const fs = require('fs'); const before = fs.readFileSync(logPath, 'utf8').length;
  await chat(bot, '/data get entity @e[type=spider,limit=1] active_effects', 800);
  const out = fs.readFileSync(logPath, 'utf8').slice(before);
  console.log('C data', out.split('\n').filter(l => /active_effects|slowness/i.test(l)).join(' | ').slice(0, 400));
  const msg = bot.msgs.slice(-3).map(x => x.m).join(' | ');
  console.log('C chat', msg);
  res('거미에 느림 효과 amplifier 3', /slowness/.test(msg) && /amplifier: 3b/.test(msg) || /slowness/.test(out) && /amplifier: ?3/.test(out), msg.slice(0, 200));

  // D. 철퇴 스매시 후 착지 먼지 기둥
  await chat(bot, '/kill @e[type=!player]', 500);
  await chat(bot, '/clear @s', 400);
  await chat(bot, '/summon zombie 5.5 -60 5.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,HandItems:[{},{}],ArmorItems:[{},{},{},{}],Health:100f,Attributes:[{Name:"generic.max_health",Base:100d}]}', 800);
  await chat(bot, '/give @s minecraft:mace', 500);
  await bot.equip(bot.inventory.items().find(i => i.name === 'mace'), 'hand');
  const zz = await find('zombie'); await sleep(2500);
  await chat(bot, '/tp @s 7.2 -48 5.5 90 0', 800);   // 12칸 위, 좀비 옆(1.7칸)
  parts.length = 0;
  let hit = false; const t0 = Date.now(); const vel = []; bot._client.on('entity_velocity', p => { if (p.entityId === bot.entity.id) vel.push(JSON.stringify(p.velocity)); });
  while (Date.now() - t0 < 6000) {
    const d = bot.entity.position.distanceTo(zz.position.offset(0, 1, 0));
    if (!hit && bot.entity.velocity.y < -0.3 && d < 3.0 && bot.entity.position.y < -58.2)   // 서버가 아는 위치는 몇 틱 늦다 — 너무 일찍 치면 6칸 거리 검사에 걸린다
     { await bot.lookAt(zz.position.offset(0, 1, 0), true); bot.attack(zz); hit = true; console.log('D hit at y', bot.entity.position.y.toFixed(2)); }
    await sleep(20);
    if (hit && Date.now() - t0 > 3000) break;
  }
  await sleep(1500);
  console.log('D vel', vel.join(' '), 'zombie', zz.position.toString());
  await chat(bot, '/data get entity @e[type=zombie,limit=1] Health', 600); console.log('D zhealth', bot.msgs.slice(-1)[0].m);
  const pil = parts.filter(x => x.name === 'dust_pillar');
  console.log('D particles', JSON.stringify(parts.filter(x => x.count !== 0 || true).slice(0, 8).map(({ raw, ...r }) => r)));
  res('철퇴로 맞힘', hit);
  res('착지 먼지 기둥(dust_pillar) 입자', pil.length >= 1, JSON.stringify(pil[0] && { count: pil[0].count, y: pil[0].y, dx: pil[0].dx, speed: pil[0].speed }));

  console.log(R.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.end(); process.exit(0);
})().catch(e => { console.log('ERR', e); process.exit(1); });
