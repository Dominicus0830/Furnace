// U1: 습격에서 이긴 뒤 주민이 쏘는 축하 폭죽에 주민이 죽거나 크게 다치지 않는지. 평지에 침대·종으로 마을을 만들고 유리 상자에 주민을 넣는다.
const U = require('../round2/u'); const { sleep, chat, ask, waitMsg } = U;
(async () => {
  const bot = await U.setup('Raider');
  const fw = []; bot.on('entitySpawn', e => { if (e.name === 'firework_rocket') fw.push(Date.now()); });
  bot._client.on('boss_bar', p => { if (p.title) console.log('BOSSBAR', JSON.stringify(p.title).slice(0, 120), p.health); });
  const C = ['/gamemode survival', '/effect give @s resistance 1000 4 true', '/difficulty easy', '/gamerule doMobSpawning false', '/gamerule doDaylightCycle false', '/time set noon', '/kill @e[type=!player]', '/tp @s 200.5 -55 6.5',
    '/fill 190 -61 -10 210 -61 10 stone', '/fill 194 -60 -4 206 -57 4 glass hollow', '/fill 195 -60 -3 205 -57 3 air'];
  // 침대는 /setblock 으로 반쪽만 놓으면 바닐라처럼 모양 갱신으로 사라진다 — 마을 POI 는 일터 블록으로 만든다(태그 village 에 듦)
  ['composter', 'lectern', 'barrel', 'smoker', 'cartography_table'].forEach((b, i) => C.push(`/setblock ${196 + i * 2} -60 -3 ${b}`));
  C.push('/setblock 200 -60 2 bell[attachment=floor]');
  for (let i = 0; i < 5; i++) C.push(`/summon villager ${196.5 + i * 2} -60 0.5 {PersistenceRequired:1b}`);
  C.push('/fill 195 -57 -3 205 -57 3 air');   // 지붕은 뚫는다: 축하 폭죽은 머리 위가 트인 주민만 쏜다(MoveToSkySeeingSpot.hasNoBlocksAbove)
  C.push('/tp @s 200.5 -55 6.5');
  for (let i = 0; i < 8; i++) await chat(bot, C.shift(), 150);   // 명령 8개(tp 까지)
  await sleep(3000);   // 먼저 가서 청크를 올린다 — 시야 밖(로드 안 된) 칸의 /setblock 은 실패한다
  for (const c of C) await chat(bot, c, 150);
  await sleep(4000);   // 주민이 침대·종을 기억(POI)할 시간
  const count = async sel => { const m = await ask(bot, `/execute if entity ${sel}`, /Test (passed|failed)/); const x = m && m.match(/count: (\d+)/); return x ? +x[1] : 0; };
  console.log('RESULT 주민 수', await count('@e[type=villager]'));
  await chat(bot, '/effect give @s bad_omen 6000 0');
  let started = false;
  for (let i = 0; i < 120 && !started; i++) { await sleep(500); started = (await count('@e[type=#minecraft:raiders]')) > 0; }
  console.log('RESULT 습격 시작', started, '효과', await ask(bot, '/data get entity @s active_effects', /entity data/, 2000));
  await chat(bot, '/tick rate 60');
  let waves = 0; const t0 = Date.now();
  while (Date.now() - t0 < 240000) {
    const n = await count('@e[type=#minecraft:raiders]');
    if (n > 0) { waves++; console.log('RESULT 웨이브', waves, '습격자', n); await chat(bot, '/execute as @e[type=#minecraft:raiders] run damage @s 1000 minecraft:player_attack by Raider', 2500); /* 플레이어가 잡아야 마을의 영웅 */ await chat(bot, '/kill @e[type=item]', 300); }
    if (/Hero of the Village|hero_of_the_village/.test(await ask(bot, '/data get entity @s active_effects', /entity data|Found/, 1500) ?? '')) { console.log('RESULT 승리(마을의 영웅) 웨이브', waves); break; }
    await sleep(1000);
  }
  await chat(bot, '/tick rate 20');
  const tWin = Date.now(); fw.length = 0;
  const hp = async () => { const r = []; for (let i = 0; i < 5; i++) { const m = await ask(bot, `/data get entity @e[type=villager,limit=1,sort=nearest,x=${196.5 + i * 2},y=-60,z=0.5] Health`, /data:|Found no|No /, 1500); const x = m && m.match(/data: ([\d.]+)/); r.push(x ? +(+x[1]).toFixed(1) : '?'); } return r; };
  console.log('RESULT 승리 직후 주민 체력', JSON.stringify(await hp()));
  await sleep(35000);   // 축하 600틱(30초)
  console.log('RESULT 축하 30초 동안 폭죽', fw.length, '주민 수', await count('@e[type=villager]'), '체력', JSON.stringify(await hp()));
  bot.quit(); await sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
